package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
    Optional<PaymentReceipt> findByPaymentTransaction_Id(Long paymentTransactionId);
}
