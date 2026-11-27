CREATE TABLE compliance_export_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_username VARCHAR(100) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    search_criteria_summary VARCHAR(500) NULL,
    matched_count INT NOT NULL DEFAULT 0,
    client_ip VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_compliance_audit_created_at (created_at),
    KEY idx_compliance_audit_admin_username (admin_username)
) ENGINE=InnoDB;
