package com.texos.texos_backend.controller;

import com.texos.texos_backend.dto.AnnouncementRequest;
import com.texos.texos_backend.entity.TexosAnnouncement;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.TexosAnnouncementRepository;
import com.texos.texos_backend.service.RestaurantLiveUpdateEvent;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AnnouncementController {

    private final TexosAnnouncementRepository announcements;
    private final ApplicationEventPublisher eventPublisher;

    public AnnouncementController(
            TexosAnnouncementRepository announcements,
            ApplicationEventPublisher eventPublisher) {
        this.announcements = announcements;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/api/announcements/active")
    public List<TexosAnnouncement> activeAnnouncements() {
        return announcements.findCurrentAnnouncements(LocalDateTime.now());
    }

    @GetMapping("/api/admin/announcements")
    public List<TexosAnnouncement> allAnnouncements() {
        return announcements.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/api/admin/announcements")
    public ResponseEntity<TexosAnnouncement> createAnnouncement(
            @Valid @RequestBody AnnouncementRequest request) {
        validateDates(request);
        TexosAnnouncement announcement = new TexosAnnouncement();
        apply(announcement, request);
        TexosAnnouncement saved = announcements.save(announcement);
        publishAnnouncementChange();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/api/admin/announcements/{id}")
    public TexosAnnouncement updateAnnouncement(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementRequest request) {
        validateDates(request);
        TexosAnnouncement announcement = findAnnouncement(id);
        apply(announcement, request);
        TexosAnnouncement saved = announcements.save(announcement);
        publishAnnouncementChange();
        return saved;
    }

    @DeleteMapping("/api/admin/announcements/{id}")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable Long id) {
        announcements.delete(findAnnouncement(id));
        publishAnnouncementChange();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/announcements/{id}/status")
    public TexosAnnouncement setAnnouncementStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        TexosAnnouncement announcement = findAnnouncement(id);
        announcement.setActive(active);
        TexosAnnouncement saved = announcements.save(announcement);
        publishAnnouncementChange();
        return saved;
    }

    private TexosAnnouncement findAnnouncement(Long id) {
        return announcements.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));
    }

    private void validateDates(AnnouncementRequest request) {
        if (request.getStartsAt() != null && request.getEndsAt() != null
                && !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new com.texos.texos_backend.exception.InvalidRequestException(
                    "Announcement end time must be after its start time");
        }
    }

    private void apply(TexosAnnouncement announcement, AnnouncementRequest request) {
        announcement.setTitle(request.getTitle().trim());
        announcement.setMessage(request.getMessage().trim());
        announcement.setStartsAt(request.getStartsAt());
        announcement.setEndsAt(request.getEndsAt());
    }

    private void publishAnnouncementChange() {
        eventPublisher.publishEvent(RestaurantLiveUpdateEvent.announcementsChanged());
    }
}
