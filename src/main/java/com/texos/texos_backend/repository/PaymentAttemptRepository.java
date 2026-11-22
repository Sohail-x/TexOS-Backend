package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByMerchantOrderId(String merchantOrderId);

    Optional<PaymentAttempt> findFirstByPaymentTransaction_IdOrderByCreatedAtDesc(Long transactionId);

    List<PaymentAttempt> findTop100ByPaymentStatusInOrderByUpdatedAtAsc(List<String> statuses);

    List<PaymentAttempt> findTop100ByPaymentStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            List<String> statuses, LocalDateTime before);
}
