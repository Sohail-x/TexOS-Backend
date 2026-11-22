package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.RestaurantBillingSettingsRequest;
import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.InvalidRequestException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final OwnerRepository ownerRepository;

    @Transactional
    public Restaurant createRestaurant(Long ownerId, Restaurant restaurant) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        restaurant.setOwner(owner);
        restaurant.setGoogleMapsUrl(normalizeGoogleMapsUrl(restaurant.getGoogleMapsUrl()));
        return restaurantRepository.save(restaurant);
    }

    public List<Restaurant> getRestaurantsByOwnerId(Long ownerId) {
        if (!ownerRepository.existsById(ownerId)) {
            throw new ResourceNotFoundException("Owner not found");
        }
        return restaurantRepository.findAllByOwnerIdAndActiveTrue(ownerId);
    }

    public Restaurant getRestaurantById(Long restaurantId, Long ownerId) {
        return restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
    }

    @Transactional
    public Restaurant updateRestaurant(
            Long restaurantId,
            Long ownerId,
            com.texos.texos_backend.dto.RestaurantRegistrationRequest request
    ) {
        Restaurant restaurant = getRestaurantById(restaurantId, ownerId);
        restaurant.setRestaurantName(request.getRestaurantName().trim());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());
        restaurant.setEmail(request.getEmail());
        restaurant.setGoogleMapsUrl(normalizeGoogleMapsUrl(request.getGoogleMapsUrl()));
        if (request.getOpeningHours() != null) {
            restaurant.setOpeningHours(request.getOpeningHours().trim());
        }
        return restaurantRepository.save(restaurant);
    }

    private String normalizeGoogleMapsUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
            boolean googleHost = host != null && (
                    normalizedHost.equals("goo.gl")
                            || normalizedHost.endsWith(".goo.gl")
                            || normalizedHost.equals("g.page")
                            || normalizedHost.equals("google.com")
                            || normalizedHost.endsWith(".google.com")
                            || normalizedHost.equals("google.co.in")
                            || normalizedHost.endsWith(".google.co.in"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !googleHost) {
                throw new IllegalArgumentException();
            }
            return url;
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Enter a valid HTTPS Google Maps link");
        }
    }

    @Transactional
    public void removeRestaurant(Long restaurantId, Long ownerId) {
        int updated = restaurantRepository.deactivateByIdAndOwnerId(restaurantId, ownerId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Restaurant not found");
        }
    }

    // Fetch the saved billing configuration for a restaurant.
    @Transactional(readOnly = true)
    public Restaurant getBillingSettings(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
    }

    @Transactional
    public Restaurant updateBillingSettings(
            Long restaurantId,
            RestaurantBillingSettingsRequest request
    ) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        if (request.getTaxRate() == null
                || request.getTaxRate().signum() < 0
                || request.getTaxRate().compareTo(new BigDecimal("100")) > 0) {
            throw new InvalidRequestException("Tax rate must be between 0 and 100");
        }

        if (request.getServiceChargeRate() == null
                || request.getServiceChargeRate().signum() < 0
                || request.getServiceChargeRate().compareTo(new BigDecimal("100")) > 0) {
            throw new InvalidRequestException("Service charge rate must be between 0 and 100");
        }

        restaurant.setMenuPriceTaxMode(request.getMenuPriceTaxMode());
        restaurant.setTaxRate(request.getTaxRate());
        restaurant.setServiceChargeEnabled(request.getServiceChargeEnabled());
        restaurant.setServiceChargeRate(request.getServiceChargeRate());

        return restaurantRepository.save(restaurant);
    }
}
