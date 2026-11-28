package com.texos.texos_backend.service;

import com.texos.texos_backend.dto.KitchenAccessCodeResponse;
import com.texos.texos_backend.dto.KitchenAccessStatusResponse;
import com.texos.texos_backend.dto.KitchenStaffSessionResponse;
import com.texos.texos_backend.entity.KitchenAccessGrant;
import com.texos.texos_backend.entity.KitchenAccessStatus;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.KitchenAccessException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.KitchenAccessGrantRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KitchenAccessService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final long ATTEMPT_WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final long STAFF_SESSION_HOURS = 12;

    private final KitchenAccessGrantRepository grantRepository;
    private final RestaurantRepository restaurantRepository;
    private final OwnerRepository ownerRepository;
    private final JwtService jwtService;
    private final ConcurrentHashMap<String, AttemptWindow> attemptsByAddress = new ConcurrentHashMap<>();

    public KitchenAccessService(
            KitchenAccessGrantRepository grantRepository,
            RestaurantRepository restaurantRepository,
            OwnerRepository ownerRepository,
            JwtService jwtService) {
        this.grantRepository = grantRepository;
        this.restaurantRepository = restaurantRepository;
        this.ownerRepository = ownerRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public KitchenAccessCodeResponse generateCode(Long restaurantId, String ownerIdentity) {
        Long ownerId = ownerRepository.findByEmail(ownerIdentity)
                .or(() -> ownerRepository.findByPhone(ownerIdentity))
                .filter(owner -> owner.isActive())
                .map(owner -> owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        Restaurant restaurant = restaurantRepository.findOwnedForUpdate(restaurantId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        LocalDateTime now = LocalDateTime.now();


        // KitchenAccessGrant latest = grantRepository
        //         .findFirstByRestaurant_IdOrderByCreatedAtDesc(restaurantId)
        //         .orElse(null);
        // if (latest != null && latest.getStatus() == KitchenAccessStatus.ACTIVE) {
        //     if (latest.getExpiresAt().isAfter(now)) {
        //         throw new KitchenAccessException(
        //                 HttpStatus.CONFLICT,
        //                 "An access code is already active. Revoke it or wait for it to expire.");
        //     }
        //     latest.setStatus(KitchenAccessStatus.EXPIRED);
        //     grantRepository.save(latest);
        // }
        // if (latest != null && latest.getStatus() == KitchenAccessStatus.USED) {
        //     if (latest.getSessionExpiresAt() != null
        //             && latest.getSessionExpiresAt().isAfter(now)
        //             && latest.getRevokedAt() == null) {
        //         throw new KitchenAccessException(
        //                 HttpStatus.CONFLICT,
        //                 "Revoke the current kitchen staff session before generating another code.");
        //     }
        //     if (latest.getRevokedAt() == null) {
        //         latest.setRevokedAt(now);
        //         grantRepository.save(latest);
        //     }
        // }





        String code;
        String tokenHash;
        do {
            code = String.format(java.util.Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
            tokenHash = jwtService.hashKitchenAccessCode(code);
        } while (grantRepository.existsByTokenHash(tokenHash));

        KitchenAccessGrant grant = new KitchenAccessGrant();
        grant.setRestaurant(restaurant);
        grant.setTokenHash(tokenHash);
        grant.setStatus(KitchenAccessStatus.ACTIVE);
        grant.setExpiresAt(now.plusMinutes(5));
        grantRepository.save(grant);
        return new KitchenAccessCodeResponse(code, grant.getExpiresAt());
    }


    @Transactional
    public KitchenAccessStatusResponse getStatus(Long restaurantId, String ownerIdentity) {
        Restaurant restaurant = getOwnedRestaurant(restaurantId, ownerIdentity, false);
        LocalDateTime now = LocalDateTime.now();
        long activeStaffCount = grantRepository.countActiveSessions(
                restaurant.getId(), KitchenAccessStatus.USED, now);

        KitchenAccessGrant grant = grantRepository
                .findFirstByRestaurant_IdOrderByCreatedAtDesc(restaurantId)
                .orElse(null);
        if (grant == null) {
            return new KitchenAccessStatusResponse(null, null, null, false, activeStaffCount);
        }
        if (grant.getStatus() == KitchenAccessStatus.ACTIVE && !grant.getExpiresAt().isAfter(now)) {
            grant.setStatus(KitchenAccessStatus.EXPIRED);
            grantRepository.save(grant);
        }
        boolean sessionActive = activeStaffCount > 0;
        return new KitchenAccessStatusResponse(
                grant.getStatus(), grant.getExpiresAt(), grant.getUsedAt(), sessionActive, activeStaffCount);
    }

    // @Transactional
    // public KitchenAccessStatusResponse revoke(Long restaurantId, String ownerIdentity) {
    //     getOwnedRestaurant(restaurantId, ownerIdentity, true);
    //     KitchenAccessGrant grant = grantRepository
    //             .findFirstByRestaurant_IdOrderByCreatedAtDesc(restaurantId)
    //             .orElseThrow(() -> new ResourceNotFoundException("No kitchen access grant found"));
    //     if (grant.getStatus() == KitchenAccessStatus.ACTIVE) {
    //         grant.setStatus(KitchenAccessStatus.REVOKED);
    //         grant.setRevokedAt(LocalDateTime.now());
    //     }
    //     if (grant.getStatus() == KitchenAccessStatus.USED && grant.getRevokedAt() == null) {
    //         grant.setRevokedAt(LocalDateTime.now());
    //     }
    //     grantRepository.save(grant);
    //     return getStatus(restaurantId, ownerIdentity);
    // }
    

    @Transactional
    public KitchenAccessStatusResponse revoke(Long restaurantId, String ownerIdentity) {
     getOwnedRestaurant(restaurantId, ownerIdentity, true);
     LocalDateTime now = LocalDateTime.now();
     int revokedCodes = grantRepository.revokeActiveCodes(
             restaurantId, KitchenAccessStatus.ACTIVE, KitchenAccessStatus.REVOKED, now);
     int revokedSessions = grantRepository.revokeActiveSessions(
             restaurantId, KitchenAccessStatus.USED, now);
     if (revokedCodes == 0 && revokedSessions == 0) {
         throw new ResourceNotFoundException("No active kitchen access found");
     }
     return getStatus(restaurantId, ownerIdentity);
 }







    @Transactional(noRollbackFor = KitchenAccessException.class)
    public KitchenStaffSessionResponse redeemCode(String code, String remoteAddress) {
        enforceAttemptLimit(remoteAddress);
        String tokenHash = jwtService.hashKitchenAccessCode(code);
        KitchenAccessGrant grant = grantRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new KitchenAccessException(
                        HttpStatus.NOT_FOUND, "Invalid access code."));
        LocalDateTime now = LocalDateTime.now();
        if (grant.getStatus() == KitchenAccessStatus.USED) {
            throw new KitchenAccessException(HttpStatus.CONFLICT, "This access code has already been used.");
        }
        if (grant.getStatus() == KitchenAccessStatus.REVOKED) {
            throw new KitchenAccessException(HttpStatus.GONE, "This access code is no longer available.");
        }
        if (grant.getStatus() == KitchenAccessStatus.EXPIRED
                || !grant.getExpiresAt().isAfter(now)) {
            grant.setStatus(KitchenAccessStatus.EXPIRED);
            grantRepository.save(grant);
            throw new KitchenAccessException(HttpStatus.GONE, "This access code has expired.");
        }
        if (grant.getStatus() != KitchenAccessStatus.ACTIVE) {
            throw new KitchenAccessException(HttpStatus.NOT_FOUND, "Invalid access code.");
        }

        UUID sessionId = UUID.randomUUID();
        LocalDateTime sessionExpiresAt = now.plusHours(STAFF_SESSION_HOURS);
        grant.setStatus(KitchenAccessStatus.USED);
        grant.setUsedAt(now);
        grant.setSessionId(sessionId);
        grant.setSessionExpiresAt(sessionExpiresAt);
        grantRepository.save(grant);
        clearAttempts(remoteAddress);
        String token = jwtService.generateKitchenStaffToken(
                sessionId, grant.getRestaurant().getId(), java.sql.Timestamp.valueOf(sessionExpiresAt));
        return new KitchenStaffSessionResponse(
                token,
                grant.getRestaurant().getId(),
                grant.getRestaurant().getRestaurantName(),
                sessionExpiresAt);
    }

    @Transactional(readOnly = true)
    public KitchenAccessGrant findActiveSession(UUID sessionId, Long restaurantId) {
        return grantRepository.findBySessionId(sessionId)
                .filter(grant -> grant.getRestaurant().getId().equals(restaurantId))
                .filter(grant -> grant.getStatus() == KitchenAccessStatus.USED)
                .filter(grant -> grant.getRevokedAt() == null)
                .filter(grant -> grant.getSessionExpiresAt() != null
                        && grant.getSessionExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(null);
    }

    @Transactional
    public void logout(UUID sessionId, Long restaurantId) {
        KitchenAccessGrant grant = findActiveSession(sessionId, restaurantId);
        if (grant != null) {
            grant.setRevokedAt(LocalDateTime.now());
            grantRepository.save(grant);
        }
    }

    private Restaurant getOwnedRestaurant(Long restaurantId, String ownerIdentity, boolean lock) {
        Long ownerId = ownerRepository.findByEmail(ownerIdentity)
                .or(() -> ownerRepository.findByPhone(ownerIdentity))
                .filter(owner -> owner.isActive())
                .map(owner -> owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        return (lock
                ? restaurantRepository.findOwnedForUpdate(restaurantId, ownerId)
                : restaurantRepository.findByIdAndOwnerId(restaurantId, ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
    }

    private void enforceAttemptLimit(String remoteAddress) {
        String addressHash = jwtService.hashKitchenAccessCode(
                remoteAddress == null ? "unknown" : remoteAddress);
        long now = System.currentTimeMillis();
        AttemptWindow window = attemptsByAddress.compute(addressHash, (key, current) -> {
            if (current == null || now - current.startedAt >= ATTEMPT_WINDOW_MILLIS) {
                return new AttemptWindow(now, 0);
            }
            return current;
        });
        synchronized (window) {
            if (window.attempts >= MAX_ATTEMPTS) {
                throw new KitchenAccessException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many attempts. Please wait 15 minutes before trying again.");
            }
            window.attempts++;
        }
        if (attemptsByAddress.size() > 10_000) {
            attemptsByAddress.entrySet().removeIf(
                    entry -> now - entry.getValue().startedAt >= ATTEMPT_WINDOW_MILLIS);
        }
    }

    private void clearAttempts(String remoteAddress) {
        String addressHash = jwtService.hashKitchenAccessCode(
                remoteAddress == null ? "unknown" : remoteAddress);
        attemptsByAddress.remove(addressHash);
    }

    private static final class AttemptWindow {
        private final long startedAt;
        private int attempts;

        private AttemptWindow(long startedAt, int attempts) {
            this.startedAt = startedAt;
            this.attempts = attempts;
        }
    }
}
