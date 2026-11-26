package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    interface OwnerAuthView {
        Long getId();
        String getEmail();
        String getPhone();
        boolean isActive();
        int getTokenVersion();
    }

    @Query("""
            select o.id as id,
                   o.email as email,
                   o.phone as phone,
                   o.active as active,
                   o.tokenVersion as tokenVersion
            from Owner o
            where o.email = :identity or o.phone = :identity
            """)
    Optional<OwnerAuthView> findAuthViewByIdentity(@Param("identity") String identity);

    Optional<Owner> findByEmail(String email);

    Optional<Owner> findByGoogleSub(String googleSub);

    Optional<Owner> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);
}
