package com.texos.texos_backend.service;

import com.texos.texos_backend.repository.TexosAnnouncementRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnnouncementBoundaryPublisher {

    private final TexosAnnouncementRepository announcements;
    private final ApplicationEventPublisher eventPublisher;
    private LocalDateTime lastCheckedAt = LocalDateTime.now();

    @Scheduled(fixedDelay = 30_000)
    public void publishBoundaryChanges() {
        LocalDateTime now = LocalDateTime.now();
        if (announcements.countAnnouncementBoundariesBetween(lastCheckedAt, now) > 0) {
            eventPublisher.publishEvent(RestaurantLiveUpdateEvent.announcementsChanged());
        }
        lastCheckedAt = now;
    }
}
