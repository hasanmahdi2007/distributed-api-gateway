package com.distributed.job_finder.services;

import com.distributed.job_finder.dtos.JobDto;
import com.distributed.job_finder.entities.Job;
import com.distributed.job_finder.repos.JobRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class JobStreamConsumer implements StreamListener<String, ObjectRecord<String, JobDto>> {

    private final JobRepo jobRepository;

    @Autowired
    public JobStreamConsumer(JobRepo jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public void onMessage(ObjectRecord<String, JobDto> message) {
        JobDto incomingJob = message.getValue();
        log.info("Worker picked up ticket for ATS Job ID: {}", incomingJob.atsJobId());

        try {
            // 1. Check if we already have this job in the database
            jobRepository.findByAtsJobIdAndCompanyId(incomingJob.atsJobId(), incomingJob.companyId())
                .ifPresentOrElse(
                    existingJob -> {
                        // 2a. UPDATING: If the job exists, maybe the title or url changed. Update it!
                        existingJob.setTitle(incomingJob.title());
                        existingJob.setLocation(incomingJob.location());
                        jobRepository.save(existingJob);
                        log.debug("Updated existing job: {}", existingJob.getId());
                    },
                    () -> {
                        // 2b. INSERTING: We have never seen this job before. Create a new entity.
                        Job newJob = new Job();
                        // UUID is generated automatically by your DB or Entity setup
                        newJob.setAtsJobId(incomingJob.atsJobId());
                        newJob.setCompanyId(incomingJob.companyId());
                        newJob.setTitle(incomingJob.title());
                        newJob.setLocation(incomingJob.location());
                        // ... map other fields ...
                        
                        jobRepository.save(newJob);
                        log.info("Saved BRAND NEW job to PostgreSQL: {}", incomingJob.title());
                    }
                );
        } catch (Exception e) {
            log.error("Failed to process job ticket: {}", incomingJob.atsJobId(), e);
            // If this fails, Redis keeps the ticket so we can try again later!
        }
    }
}