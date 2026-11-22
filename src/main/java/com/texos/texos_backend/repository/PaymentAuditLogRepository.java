package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {
    List<PaymentAuditLog> findAllByPaymentTransaction_IdOrderByCreatedAtAsc(Long transactionId);
}
