package com.pipeline.worker.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.pipeline.schema.*;
import com.pipeline.worker.service.CodeExecutionService;
import com.pipeline.worker.producer.ResultProducer;

import java.time.Instant;

@Service
public class SubmissionConsumer {

    private final CodeExecutionService executor;
    private final ResultProducer producer;

    public SubmissionConsumer(CodeExecutionService executor, ResultProducer producer) {
        this.executor = executor;
        this.producer = producer;
    }

    @KafkaListener(topics = "code-submissions", groupId = "worker-group")
    public void consume(CodeSubmission submission) {

        String jobId = submission.getJobId().toString();

        try {
            String output = executor.executePython(submission.getCode().toString());

            ExecutionResult result = ExecutionResult.newBuilder()
                    .setJobId(jobId)
                    .setOutput(output)
                    .setError(null)
                    .setStatus(ExecutionStatus.SUCCESS)
                    .setTimestamp(Instant.now())
                    .build();

            producer.send(result);

        } catch (Exception e) {

            ExecutionResult result = ExecutionResult.newBuilder()
                    .setJobId(jobId)
                    .setOutput(null)
                    .setError(e.getMessage())
                    .setStatus(ExecutionStatus.ERROR)
                    .setTimestamp(Instant.now())
                    .build();

            producer.send(result);
        }
    }
}