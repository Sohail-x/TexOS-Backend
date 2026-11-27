package com.texos.texos_backend.repository;

import com.texos.texos_backend.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
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
            where (o.email = :identity or o.phone = :identity)
              and o.active = true
              and o.deletedAt is null
            """)
    Optional<OwnerAuthView> findAuthViewByIdentity(@Param("identity") String identity);

    Optional<Owner> findByEmail(String email);

    Optional<Owner> findByEmailAndDeletedAtIsNull(String email);

    Optional<Owner> findByGoogleSub(String googleSub);

    Optional<Owner> findByGoogleSubAndDeletedAtIsNull(String googleSub);

    Optional<Owner> findByPhone(String phone);

    Optional<Owner> findByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByEmail(String email);

    @Query("""
            select owner
            from Owner owner
            where (:name is null or lower(owner.fullName) like lower(concat('%', :name, '%')))
              and (:email is null or lower(coalesce(owner.email, '')) like lower(concat('%', :email, '%')))
              and (:phone is null or coalesce(owner.phone, '') like concat('%', :phone, '%'))
            order by owner.id
            """)
    List<Owner> findForComplianceSearch(
            @Param("name") String name,
            @Param("email") String email,
            @Param("phone") String phone);

    List<Owner> findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select owner from Owner owner where owner.id = :ownerId and owner.deletedAt is null")
    Optional<Owner> findByIdForUpdate(@Param("ownerId") Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Owner> findTop500ByDeletedAtLessThanEqualAndAnonymizedAtIsNullOrderByIdAsc(
            Instant deletedAt);
}
