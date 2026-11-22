package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.CustomerAccountIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountIdentityRepository extends JpaRepository<CustomerAccountIdentity, Long> {
    boolean existsByIdentityTypeAndIdentityValue(String identityType, String identityValue);
}
