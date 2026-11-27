package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.CustomerAccountIdentity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CustomerAccountIdentityRepository extends JpaRepository<CustomerAccountIdentity, Long> {
    boolean existsByIdentityTypeAndIdentityValue(String identityType, String identityValue);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerAccountIdentity> findByIdentityTypeAndIdentityValue(
            String identityType, String identityValue);

    List<CustomerAccountIdentity> findAllByCustomer_Id(Long customerId);

    long deleteAllByCustomer_Id(Long customerId);
}
