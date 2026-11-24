package com.texos.texos_backend.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.texos.texos_backend.repository.TexosAnnouncementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AnnouncementBoundaryPublisherTest {

    private final TexosAnnouncementRepository announcements =
            mock(TexosAnnouncementRepository.class);
    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);
    private final AnnouncementBoundaryPublisher publisher =
            new AnnouncementBoundaryPublisher(announcements, eventPublisher);

    @Test
    void publishesAnnouncementInvalidationWhenAScheduleBoundaryPasses() {
        when(announcements.countAnnouncementBoundariesBetween(any(), any())).thenReturn(1L);

        publisher.publishBoundaryChanges();

        verify(eventPublisher).publishEvent(RestaurantLiveUpdateEvent.announcementsChanged());
    }

    @Test
    void doesNotPublishWhenNoAnnouncementBoundaryPasses() {
        when(announcements.countAnnouncementBoundariesBetween(any(), any())).thenReturn(0L);

        publisher.publishBoundaryChanges();

        verify(eventPublisher, never()).publishEvent(
                RestaurantLiveUpdateEvent.announcementsChanged());
    }
}
