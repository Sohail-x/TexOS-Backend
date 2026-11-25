package com.texos.texos_backend.config;

import com.texos.texos_backend.entity.Owner;
import java.util.Objects;

public record AuthenticatedOwnerPrincipal(Long ownerId, OwnerSnapshot ownerSnapshot) {

    public static final String REQUEST_ATTRIBUTE =
            AuthenticatedOwnerPrincipal.class.getName();

    public AuthenticatedOwnerPrincipal(Long ownerId) {
        this(ownerId, null);
    }

    public AuthenticatedOwnerPrincipal {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
    }

    public static AuthenticatedOwnerPrincipal from(Owner owner) {
        return new AuthenticatedOwnerPrincipal(
                owner.getId(),
                new OwnerSnapshot(
                        owner.getFullName(),
                        owner.getEmail(),
                        owner.getPhone(),
                        owner.getAvatarIcon(),
                        owner.getAvatarImage(),
                        owner.isActive()));
    }

    public record OwnerSnapshot(
            String fullName,
            String email,
            String phone,
            String avatarIcon,
            String avatarImage,
            boolean active) {
    }
}
