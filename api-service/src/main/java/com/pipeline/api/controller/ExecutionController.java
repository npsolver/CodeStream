package com.pipeline.api.controller;

import org.springframework.web.bind.annotation.*;

import com.pipeline.api.producer.SubmissionProducer;
import com.pipeline.api.store.ResultStore;
import com.pipeline.schema.CodeSubmission;
import com.pipeline.schema.ExecutionResult;
import com.pipeline.schema.Language;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class ExecutionController {

    private final SubmissionProducer producer;
    private final ResultStore resultStore;

    public ExecutionController(SubmissionProducer producer, ResultStore resultStore) {
        this.producer = producer;
        this.resultStore = resultStore;
    }

    @PostMapping("/execute")
    public String execute(@RequestBody Map<String, String> request) {

        CodeSubmission submission = new CodeSubmission(
                UUID.randomUUID().toString(),
                Language.PYTHON,
                request.get("code"),
                null,
                Instant.now());

        producer.send(submission);

        return submission.jobId();
    }

    @GetMapping("/result/{jobId}")
    public Object getResult(@PathVariable("jobId") String jobId) {

        ExecutionResult result = resultStore.get(jobId);

        if (result == null) {
            return Map.of("status", "PROCESSING");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", result.jobId());
        response.put("status", result.status().toString());
        response.put("output", result.output());
        response.put("error", result.error());

        return response;
    }

}
