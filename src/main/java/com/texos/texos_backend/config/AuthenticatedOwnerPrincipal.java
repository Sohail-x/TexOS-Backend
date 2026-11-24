package com.texos.texos_backend.config;

import java.util.Objects;

public record AuthenticatedOwnerPrincipal(Long ownerId) {

    public static final String REQUEST_ATTRIBUTE =
            AuthenticatedOwnerPrincipal.class.getName();

    public AuthenticatedOwnerPrincipal {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
    }
}
