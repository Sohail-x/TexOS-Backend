package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.entity.MenuCategory;
import com.texos.texos_backend.entity.MenuItem;
import com.texos.texos_backend.entity.Restaurant;
import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.MenuCategoryRepository;
import com.texos.texos_backend.repository.MenuItemRepository;
import com.texos.texos_backend.repository.OwnerRepository;
import com.texos.texos_backend.repository.RestaurantRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestaurantRepository restaurantRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;

    @Transactional
    public Owner registerOwner(Owner owner) {

        owner.setPhone(owner.getPhone().trim());
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            owner.setEmail(owner.getEmail().trim().toLowerCase());

            if (ownerRepository.existsByEmail(owner.getEmail())) {
                throw new DuplicateResourceException("Email is already registered");
            }
            if (owner.getPhone() != null && ownerRepository.existsByPhone(owner.getPhone().trim())) {
                throw new DuplicateResourceException("Mobile number is already registered");
            }
        }

        owner.setPasswordHash(
            passwordEncoder.encode(owner.getPasswordHash())
        );

        return ownerRepository.save(owner);
    }

    public Owner getOwnerById(Long ownerId) {

        return ownerRepository.findById(ownerId)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Owner not found")
                );
    }

    public Owner getOwnerByEmail(String email) {

        return ownerRepository.findByEmail(
                email.trim().toLowerCase()
        ).orElseThrow(() ->
            new ResourceNotFoundException("Owner not found")
        );
    }

    @Transactional
    public void deleteOwnerAccount(Long ownerId) {
        Owner owner = ownerRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
        if (!owner.isActive()) {
            return;
        }

        Instant deletedAt = Instant.now();
        for (Restaurant restaurant : restaurantRepository.findAllByOwnerId(ownerId)) {
            restaurant.setActive(false);
            restaurant.setDeletedAt(deletedAt);
            restaurant.setOrderApprovalEnabled(false);
            restaurant.setAddress(null);
            restaurant.setPhone(null);
            restaurant.setEmail(null);
            restaurant.setGoogleMapsUrl(null);
            restaurant.setOpeningHours(null);
            restaurant.setPaymentQrImage(null);
            restaurant.setUpiId(null);

            for (MenuCategory category : menuCategoryRepository.findAllByRestaurantId(restaurant.getId())) {
                category.setCategoryName("Deleted category " + category.getId());
                category.setDescription(null);
                category.setActive(false);
            }
            for (MenuItem item : menuItemRepository.findAllByRestaurantId(restaurant.getId())) {
                item.setItemName("Deleted menu item " + item.getId());
                item.setDescription(null);
                item.setImageUrl(null);
                item.setPrice(BigDecimal.ZERO);
                item.setAvailable(false);
                item.setDisplayOrder(0);
            }
        }

        owner.setActive(false);
        owner.setPasswordHash(null);
        owner.setDeletedAt(deletedAt);
        owner.setAnonymizedAt(null);
        owner.setTokenVersion(owner.getTokenVersion() + 1);
    }
}
