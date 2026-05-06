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

        CodeSubmission submission = CodeSubmission.newBuilder()
                .setJobId(UUID.randomUUID().toString())
                .setLanguage(Language.PYTHON)
                .setCode(request.get("code"))
                .setTimestamp(Instant.now())
                .build();

        producer.send(submission);

        return submission.getJobId().toString();
    }

    @GetMapping("/result/{jobId}")
    public Object getResult(@PathVariable("jobId") String jobId) {

        ExecutionResult result = resultStore.get(jobId);

        if (result == null) {
            return Map.of("status", "PROCESSING");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", result.getJobId().toString());
        response.put("status", result.getStatus().toString());
        response.put("output", result.getOutput() != null ? result.getOutput().toString() : null);
        response.put("error", result.getError() != null ? result.getError().toString() : null);

        return response;
    }

}