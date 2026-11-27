package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.ComplianceExportAuditLog;
import com.texos.texos_backend.repository.ComplianceExportAuditLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceExportAuditService {

    private final ComplianceExportAuditLogRepository repository;

    public ComplianceExportAuditService(ComplianceExportAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            String adminUsername,
            String actionType,
            String searchCriteriaSummary,
            int matchedCount,
            String clientIp) {
        if (adminUsername == null || adminUsername.isBlank() || adminUsername.length() > 100) {
            throw new IllegalArgumentException("Authenticated admin identity is invalid.");
        }
        if (actionType == null || actionType.isBlank() || actionType.length() > 40) {
            throw new IllegalArgumentException("Compliance audit action is invalid.");
        }
        if (searchCriteriaSummary != null && searchCriteriaSummary.length() > 500) {
            throw new IllegalArgumentException("Compliance audit filter summary is too long.");
        }
        if (matchedCount < 0) {
            throw new IllegalArgumentException("Compliance audit matched count cannot be negative.");
        }
        repository.save(new ComplianceExportAuditLog(
                adminUsername,
                actionType,
                searchCriteriaSummary,
                matchedCount,
                clientIp,
                Instant.now()));
    }
}
