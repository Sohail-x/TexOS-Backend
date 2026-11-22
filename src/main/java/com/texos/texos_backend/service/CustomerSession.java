
package com.texos.texos_backend.service;

import java.io.Serializable;
import java.time.Instant;

public class CustomerSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long customerId;
    private final Long restaurantId;
    private Instant lastActivityAt;

    public CustomerSession(Long customerId, Long restaurantId) {
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.lastActivityAt = Instant.now();
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void updateActivity() {
        this.lastActivityAt = Instant.now();
    }
}
