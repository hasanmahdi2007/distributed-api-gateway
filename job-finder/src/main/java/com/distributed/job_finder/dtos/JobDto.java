package com.distributed.job_finder.dtos;

import java.util.UUID;

/**
 * A lightweight object to carry scraped job data from the ATS to our Redis Stream.
 */
public record JobDto(
    String atsJobId,
    UUID companyId,         // Using UUID strictly as defined in our architecture
    String title,
    String location,
    String department,
    String applyUrl,
    String descriptionText
) {}