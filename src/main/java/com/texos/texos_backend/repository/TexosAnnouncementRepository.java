package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.TexosAnnouncement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TexosAnnouncementRepository extends JpaRepository<TexosAnnouncement, Long> {

    List<TexosAnnouncement> findAllByOrderByCreatedAtDesc();

    @Query("""
            select announcement
            from TexosAnnouncement announcement
            where announcement.active = true
              and (announcement.startsAt is null or announcement.startsAt <= :now)
              and (announcement.endsAt is null or announcement.endsAt > :now)
            order by announcement.createdAt desc
            """)
    List<TexosAnnouncement> findCurrentAnnouncements(@Param("now") LocalDateTime now);
}
