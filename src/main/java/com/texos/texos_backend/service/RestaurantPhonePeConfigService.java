package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.RestaurantPhonePeConfigRequest;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.entity.RestaurantPhonePeConfig;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.RestaurantPhonePeConfigRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RestaurantPhonePeConfigService {
    private final RestaurantPhonePeConfigRepository configRepository;
    private final RestaurantRepository restaurantRepository;
    private final PhonePeCredentialEncryptionService encryptionService;

    @Value("${app.phonepe.restaurant-merchants-enabled:false}")
    private boolean restaurantMerchantsEnabled;

    @Value("${app.phonepe.partner-integration:false}")
    private boolean partnerIntegration;

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings(Long restaurantId, Long ownerId) {
        requireOwnedRestaurant(restaurantId, ownerId);
        RestaurantPhonePeConfig config = configRepository.findByRestaurant_Id(restaurantId).orElse(null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("configured", config != null);
        response.put("merchantId", config == null ? "" : config.getMerchantId());
        response.put("clientId", config == null ? "" : config.getClientId());
        response.put("clientVersion", config == null ? 1 : config.getClientVersion());
        response.put("environment", config == null ? "SANDBOX" : config.getEnvironment());
        response.put("webhookKeyId", config == null ? "" : config.getWebhookKeyId());
        response.put("onlinePaymentsEnabled", config != null && restaurantMerchantsEnabled);
        response.put("partnerIntegrationEnabled", partnerIntegration);
        return response;
    }

    @Transactional
    public Map<String, Object> saveSettings(
            Long restaurantId, Long ownerId, RestaurantPhonePeConfigRequest request) {
        Restaurant restaurant = requireOwnedRestaurant(restaurantId, ownerId);
        encryptionService.requireKey();
        RestaurantPhonePeConfig config = configRepository.findByRestaurant_Id(restaurantId)
                .orElseGet(RestaurantPhonePeConfig::new);
        boolean isNew = config.getId() == null;
        if (isNew && (isBlank(request.getClientSecret())
                || isBlank(request.getWebhookKeyId()) || isBlank(request.getWebhookSecret()))) {
            throw new IllegalArgumentException(
                    "Client secret and PhonePe webhook key ID/secret are required for initial setup");
        }
        config.setRestaurant(restaurant);
        config.setMerchantId(request.getMerchantId().trim());
        config.setClientId(request.getClientId().trim());
        config.setClientVersion(request.getClientVersion());
        config.setEnvironment(request.getEnvironment().trim().toUpperCase(java.util.Locale.ROOT));
        if (!isBlank(request.getClientSecret())) {
            config.setClientSecretCiphertext(encryptionService.encrypt(request.getClientSecret()));
        }
        if (!isBlank(request.getWebhookKeyId())) config.setWebhookKeyId(request.getWebhookKeyId().trim());
        if (!isBlank(request.getWebhookSecret())) {
            config.setWebhookSecretCiphertext(encryptionService.encrypt(request.getWebhookSecret()));
        }
        configRepository.save(config);
        return getSettings(restaurantId, ownerId);
    }

    @Transactional(readOnly = true)
    public RestaurantPhonePeConfig requireConfigured(Long restaurantId) {
        if (!restaurantMerchantsEnabled) {
            throw new IllegalStateException(
                    "Restaurant PhonePe payments are disabled until PhonePe approves the merchant integration model");
        }
        return configRepository.findByRestaurant_Id(restaurantId)
                .orElseThrow(() -> new IllegalStateException("PhonePe is not configured for this restaurant"));
    }

    public PhonePeMerchantCredentials getCredentials(RestaurantPhonePeConfig config) {
        return new PhonePeMerchantCredentials(
                config.getMerchantId(),
                config.getClientId(),
                encryptionService.decrypt(config.getClientSecretCiphertext()),
                config.getClientVersion(),
                config.getEnvironment());
    }

    public String getWebhookSecret(RestaurantPhonePeConfig config) {
        return encryptionService.decrypt(config.getWebhookSecretCiphertext());
    }

    @Transactional(readOnly = true)
    public boolean isOnlinePaymentsEnabled(Long restaurantId) {
        return restaurantMerchantsEnabled && configRepository.findByRestaurant_Id(restaurantId).isPresent();
    }

    @Transactional(readOnly = true)
    public RestaurantPhonePeConfig findConfiguration(Long restaurantId) {
        return configRepository.findByRestaurant_Id(restaurantId)
                .orElseThrow(() -> new IllegalStateException("PhonePe is not configured for this restaurant"));
    }

    public boolean isPartnerIntegrationEnabled() {
        return partnerIntegration;
    }

    private Restaurant requireOwnedRestaurant(Long restaurantId, Long ownerId) {
        return restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
