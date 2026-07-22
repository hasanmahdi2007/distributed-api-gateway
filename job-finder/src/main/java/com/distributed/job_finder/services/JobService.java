package com.distributed.job_finder.services;

import com.distributed.job_finder.dtos.JobDto;
import com.distributed.job_finder.entities.Job;
import com.distributed.job_finder.repos.JobRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JobService {

    private final JobRepo jobRepo;

    @Autowired
    public JobService(JobRepo jobRepo) {
        this.jobRepo = jobRepo;
    }

    public Page<JobDto> getJobs(String search, String location, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        
        Page<Job> jobPage = jobRepo.searchJobs(search, location, pageable);

        // Map Job entity -> JobDto for the REST response
        return jobPage.map(job -> new JobDto(
                job.getAtsJobId(),
                job.getCompanyId(),
                job.getTitle(),
                job.getLocation(),
                job.getDepartment(),
                job.getApplyUrl(),
                job.getDescriptionText()
        ));
    }

    public JobDto getJobById(UUID id) {
        Job job = jobRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        return new JobDto(
                job.getAtsJobId(),
                job.getCompanyId(),
                job.getTitle(),
                job.getLocation(),
                job.getDepartment(),
                job.getApplyUrl(),
                job.getDescriptionText()
        );
    }
}