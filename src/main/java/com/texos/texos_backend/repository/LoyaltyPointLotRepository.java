package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.LoyaltyPointLot;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyPointLotRepository extends JpaRepository<LoyaltyPointLot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoyaltyPointLot l where l.account.id=:accountId and l.availablePoints>0 and (l.expiresAt is null or l.expiresAt>:now) order by l.createdAt asc")
    List<LoyaltyPointLot> lockUsableLots(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoyaltyPointLot l where l.account.id=:accountId and l.availablePoints>0 and l.expiresAt is not null and l.expiresAt<=:now")
    List<LoyaltyPointLot> lockExpiredLots(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoyaltyPointLot l where l.order.id=:orderId")
    List<LoyaltyPointLot> lockLotsByOrder(@Param("orderId") Long orderId);
}
