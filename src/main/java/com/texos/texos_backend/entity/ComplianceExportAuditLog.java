package com.texos.texos_backend.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "compliance_export_audit_logs")
public class ComplianceExportAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_username", nullable = false, length = 100, updatable = false)
    private String adminUsername;

    @Column(name = "action_type", nullable = false, length = 40, updatable = false)
    private String actionType;

    @Column(name = "search_criteria_summary", length = 500, updatable = false)
    private String searchCriteriaSummary;

    @Column(name = "matched_count", nullable = false, updatable = false)
    private int matchedCount;

    @Column(name = "client_ip", length = 64, updatable = false)
    private String clientIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ComplianceExportAuditLog() {
    }

    public ComplianceExportAuditLog(
            String adminUsername,
            String actionType,
            String searchCriteriaSummary,
            int matchedCount,
            String clientIp,
            Instant createdAt) {
        this.adminUsername = adminUsername;
        this.actionType = actionType;
        this.searchCriteriaSummary = searchCriteriaSummary;
        this.matchedCount = matchedCount;
        this.clientIp = clientIp;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getActionType() {
        return actionType;
    }

    public String getSearchCriteriaSummary() {
        return searchCriteriaSummary;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
