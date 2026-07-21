package com.distributed.job_finder.dtos.greenhouse;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GreenhouseJob(
    Long id,
    String title,
    @JsonProperty("absolute_url") String absoluteUrl,
    GreenhouseLocation location,
    @JsonProperty("updated_at") String updatedAt
) {}