package com.jobportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobportal.model.Job;

public interface JobRepository extends JpaRepository<Job, Long> {

	List<Job> findByLocationContainingIgnoreCase(String location);

	List<Job> findTop3ByStatusOrderByPostedDateDesc(String status);

	List<Job> findByOwnerIdOrderByPostedDateDesc(Long ownerId);

	long countByOwnerId(Long ownerId);

	long countByOwnerIdAndStatus(Long ownerId, String status);
}
