package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.PhonePeWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhonePeWebhookEventRepository extends JpaRepository<PhonePeWebhookEvent, Long> {
    Optional<PhonePeWebhookEvent> findByEventHash(String eventHash);

    List<PhonePeWebhookEvent> findTop100ByProcessingStatusOrderByReceivedAtAsc(String processingStatus);

    List<PhonePeWebhookEvent> findTop100ByProcessingStatusInOrderByReceivedAtAsc(List<String> statuses);
}
