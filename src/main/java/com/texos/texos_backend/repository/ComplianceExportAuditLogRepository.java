package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.ComplianceExportAuditLog;
import org.springframework.data.repository.Repository;

public interface ComplianceExportAuditLogRepository
        extends Repository<ComplianceExportAuditLog, Long> {

    <S extends ComplianceExportAuditLog> S save(S auditLog);
}
