package com.jobportal.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobportal.model.Job;

public interface JobRepository extends JpaRepository<Job, Long> {

	List<Job> findByLocationContainingIgnoreCase(String location);

	List<Job> findTop3ByStatusOrderByPostedDateDesc(String status);

	@Query("select j from Job j where j.status = :status and upper(trim(coalesce(j.title, ''))) not like 'DEMO JOB%' order by j.postedDate desc")
	List<Job> findTopPublicJobsExcludingDemo(@Param("status") String status, Pageable pageable);

	List<Job> findByOwnerIdOrderByPostedDateDesc(Long ownerId);

	long countByOwnerId(Long ownerId);

	boolean existsByOwnerId(Long ownerId);

	long countByOwnerIdAndStatus(Long ownerId, String status);

	boolean existsByTitleAndCompanyAndOwnerIdIsNull(String title, String company);
}
