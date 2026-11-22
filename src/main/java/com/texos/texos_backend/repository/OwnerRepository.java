package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByEmail(String email);

    Optional<Owner> findByGoogleSub(String googleSub);

    Optional<Owner> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);
}

