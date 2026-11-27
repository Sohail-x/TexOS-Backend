package com.texos.texos_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.texos.texos_backend.entity.ComplianceExportAuditLog;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ComplianceExportAuditPersistenceIntegrationTest {

    @Autowired
    private ComplianceExportAuditService auditService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAnAuditRowWithItsUtcInstant() {
        String username = "audit-test-" + UUID.randomUUID();
        Instant before = Instant.now();

        auditService.record(username, "SEARCH", "customerEmail", 3, "203.0.113.8");
        entityManager.flush();

        ComplianceExportAuditLog saved = entityManager.createQuery(
                        "select log from ComplianceExportAuditLog log "
                                + "where log.adminUsername = :username",
                        ComplianceExportAuditLog.class)
                .setParameter("username", username)
                .getSingleResult();

        assertEquals(username, saved.getAdminUsername());
        assertEquals("SEARCH", saved.getActionType());
        assertEquals("customerEmail", saved.getSearchCriteriaSummary());
        assertEquals(3, saved.getMatchedCount());
        assertEquals("203.0.113.8", saved.getClientIp());
        assertFalse(saved.getCreatedAt().isBefore(before.minusSeconds(1)));
    }
}
