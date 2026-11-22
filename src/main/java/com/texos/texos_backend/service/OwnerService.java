package com.texos.texos_backend.service;

import com.texos.texos_backend.entity.Owner;
import com.texos.texos_backend.exception.DuplicateResourceException;
import com.texos.texos_backend.exception.ResourceNotFoundException;
import com.texos.texos_backend.repository.OwnerRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Owner registerOwner(Owner owner) {

        owner.setPhone(owner.getPhone().trim());
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            owner.setEmail(owner.getEmail().trim().toLowerCase());

            if (ownerRepository.existsByEmail(owner.getEmail())) {
                throw new DuplicateResourceException("Email is already registered");
            }
            if (owner.getPhone() != null && ownerRepository.existsByPhone(owner.getPhone().trim())) {
                throw new DuplicateResourceException("Mobile number is already registered");
            }
        }

        owner.setPasswordHash(
            passwordEncoder.encode(owner.getPasswordHash())
        );

        return ownerRepository.save(owner);
    }

    public Owner getOwnerById(Long ownerId) {

        return ownerRepository.findById(ownerId)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Owner not found")
                );
    }

    public Owner getOwnerByEmail(String email) {

        return ownerRepository.findByEmail(
                email.trim().toLowerCase()
        ).orElseThrow(() ->
            new ResourceNotFoundException("Owner not found")
        );
    }
}
