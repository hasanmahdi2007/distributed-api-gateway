package com.distributed.job_finder.dtos;

import java.util.UUID;

public record JobDto(
    String atsJobId,
    UUID companyId,
    String title,
    String location,
    String department,
    String url,
    String description
) {}