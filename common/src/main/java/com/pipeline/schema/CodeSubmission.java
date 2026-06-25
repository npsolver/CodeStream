package com.pipeline.schema;

import java.time.Instant;

public record CodeSubmission(
        String jobId,
        Language language,
        String code,
        String input,
        Instant timestamp) {
}
