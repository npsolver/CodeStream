package com.pipeline.worker.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.pipeline.schema.CodeSubmission;
import com.pipeline.worker.service.CodeExecutionService;

@Service
public class SubmissionConsumer {

    private final CodeExecutionService executor;

    public SubmissionConsumer(CodeExecutionService executor) {
        this.executor = executor;
    }

    @KafkaListener(topics = "code-submissions", groupId = "worker-group")
    public void consume(CodeSubmission submission) {

        try {
            String output = executor.executePython(submission.getCode().toString());

            System.out.println("Execution result:");
            System.out.println(output);

        } catch (Exception e) {
            System.out.println("Execution failed: " + e.getMessage());
        }
    }
}