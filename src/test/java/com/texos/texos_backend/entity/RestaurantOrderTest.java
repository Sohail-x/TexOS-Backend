package com.texos.texos_backend.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestaurantOrderTest {
    @Test
    void prePersistUsesAUniqueOrderNumberThatFitsTheColumn() {
        RestaurantOrder order = new RestaurantOrder();

        order.onCreate();

        assertEquals(36, order.getOrderNumber().length());
        assertDoesNotThrow(() -> UUID.fromString(order.getOrderNumber()));
    }
}
