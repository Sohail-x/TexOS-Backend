package com.texos.texos_backend.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillTest {
    @Test
    void prePersistUsesAUniqueBillNumberThatFitsTheColumn() {
        Bill bill = new Bill();

        bill.onCreate();

        assertEquals(36, bill.getBillNumber().length());
        assertDoesNotThrow(() -> UUID.fromString(bill.getBillNumber()));
    }
}
