package com.distributed.job_finder.repos;

import com.distributed.job_finder.entities.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepo extends JpaRepository<Job, UUID> {
    Optional<Job> findByAtsJobIdAndCompanyId(String atsJobId, UUID companyId);
}