package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    boolean existsByProviderEventId(String providerEventId);
}
