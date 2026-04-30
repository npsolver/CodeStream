package com.pipeline.api.producer;

import com.pipeline.schema.CodeSubmission;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class SubmissionProducer {

    private final KafkaTemplate<String, CodeSubmission> kafkaTemplate;

    public SubmissionProducer(KafkaTemplate<String, CodeSubmission> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(CodeSubmission submission) {
        kafkaTemplate.send("code-submissions", submission.getJobId().toString(), submission);
    }
}