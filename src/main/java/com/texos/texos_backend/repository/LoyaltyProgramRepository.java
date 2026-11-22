package com.texos.texos_backend.repository;
import com.texos.texos_backend.entity.LoyaltyProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LoyaltyProgramRepository extends JpaRepository<LoyaltyProgram,Long>{ Optional<LoyaltyProgram> findByRestaurant_Id(Long restaurantId); }
