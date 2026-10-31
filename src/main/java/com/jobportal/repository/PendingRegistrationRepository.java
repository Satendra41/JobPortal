package com.jobportal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobportal.model.PendingRegistration;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findFirstByEmailIgnoreCase(String email);
    void deleteByEmailIgnoreCase(String email);
}
