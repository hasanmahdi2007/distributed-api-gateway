package com.distributed.job_finder.repos;

import com.distributed.job_finder.entities.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepo extends JpaRepository<Job, UUID> {

    Optional<Job> findByAtsJobIdAndCompanyId(String atsJobId, UUID companyId);

    // Search jobs by title or location (case-insensitive) with pagination
    @Query("SELECT j FROM Job j WHERE " +
           "(:search IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%')))")
    Page<Job> searchJobs(@Param("search") String search, 
                         @Param("location") String location, 
                         Pageable pageable);
}