package com.pipeline.api.controller;

import org.springframework.web.bind.annotation.*;

import com.pipeline.api.producer.SubmissionProducer;
import com.pipeline.schema.CodeSubmission;
import com.pipeline.schema.Language;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/execute")
public class ExecutionController {

    private final SubmissionProducer producer;

    public ExecutionController(SubmissionProducer producer) {
        this.producer = producer;
    }

    @PostMapping
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
}