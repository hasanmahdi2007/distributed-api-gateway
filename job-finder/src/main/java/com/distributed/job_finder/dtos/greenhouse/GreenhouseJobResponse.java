package com.distributed.job_finder.dtos.greenhouse;

import java.util.List;

public record GreenhouseJobResponse(
    List<GreenhouseJob> jobs
) {}