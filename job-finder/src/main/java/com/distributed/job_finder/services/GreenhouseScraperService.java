package com.distributed.job_finder.services;

import com.distributed.job_finder.dtos.JobDto;
import com.distributed.job_finder.dtos.greenhouse.GreenhouseJobResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Slf4j
@Service
public class GreenhouseScraperService {

    private final WebClient webClient;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String JOB_INGESTION_STREAM = "job:ingestion:stream";

    @Autowired
    public GreenhouseScraperService(WebClient webClient, RedisTemplate<String, Object> redisTemplate) {
        this.webClient = webClient;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Reaches out to a specific company's Greenhouse board and pushes scraped jobs to Redis Stream.
     */
    public void fetchJobsFromGreenhouse(UUID companyId, String boardToken) {
        String greenhouseApiUrl = "https://boards-api.greenhouse.io/v1/boards/" + boardToken + "/jobs";

        log.info("Fetching jobs from Greenhouse for board: {}", boardToken);

        webClient.get()
                .uri(greenhouseApiUrl)
                .retrieve()
                // WebFlux automatically parses the JSON array into our GreenhouseJobResponse record
                .bodyToMono(GreenhouseJobResponse.class)
                .doOnSuccess(response -> {
                    if (response != null && response.jobs() != null) {
                        log.info("Fetched {} jobs from Greenhouse for {}", response.jobs().size(), boardToken);

                        // Process each job and slap it onto the Redis Stream
                        response.jobs().forEach(ghJob -> {
                            JobDto jobDto = new JobDto(
                                    String.valueOf(ghJob.id()),
                                    companyId,
                                    ghJob.title(),
                                    ghJob.location() != null ? ghJob.location().name() : "Remote / Unspecified",
                                    "General", // Department placeholder
                                    ghJob.absoluteUrl(),
                                    "" // Full description can be loaded on demand or detail endpoint
                            );

                            pushToRedisStream(jobDto);
                        });
                    }
                })
                .doOnError(error -> log.error("Failed to fetch jobs for board {}: {}", boardToken, error.getMessage()))
                .subscribe(); // Non-blocking async execution
    }

    private void pushToRedisStream(JobDto jobDto) {
        ObjectRecord<String, JobDto> record = StreamRecords.newRecord()
                .ofObject(jobDto)
                .withStreamKey(JOB_INGESTION_STREAM);

        redisTemplate.opsForStream().add(record);
        log.debug("Pushed job ticket to Redis Stream: {}", jobDto.atsJobId());
    }
}