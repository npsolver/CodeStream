package com.pipeline.schema;

import java.time.Instant;

public record ExecutionResult(
        String jobId,
        String output,
        String error,
        ExecutionStatus status,
        Instant timestamp) {
}
