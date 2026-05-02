package com.pipeline.worker.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.pipeline.schema.CodeSubmission;

@Service
public class SubmissionConsumer {

    @KafkaListener(topics = "code-submissions", groupId = "worker-group")
    public void consume(CodeSubmission submission) {

        System.out.println("Received job:");
        System.out.println("ID: " + submission.getJobId());
        System.out.println("Language: " + submission.getLanguage());
        System.out.println("Code: " + submission.getCode());
    }
}