package com.pipeline.worker.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.pipeline.schema.*;
import com.pipeline.worker.service.CodeExecutionService;
import com.pipeline.worker.service.ExecutionResponse;
import com.pipeline.worker.producer.ResultProducer;

import java.time.Instant;

@Service
public class SubmissionConsumer {

    private final CodeExecutionService executor;
    private final ResultProducer producer;

    private ExecutionStatus mapStatus(ExecutionResponse.Status status) {
        return switch (status) {
            case SUCCESS -> ExecutionStatus.SUCCESS;
            case ERROR -> ExecutionStatus.ERROR;
            case TIMEOUT -> ExecutionStatus.TIMEOUT;
        };
    }

    public SubmissionConsumer(CodeExecutionService executor, ResultProducer producer) {
        this.executor = executor;
        this.producer = producer;
    }

    @KafkaListener(topics = "code-submissions", groupId = "worker-group")
    public void consume(CodeSubmission submission) {

        String jobId = submission.getJobId().toString();

        try {
            ExecutionResponse response = executor.executePython(submission.getCode().toString());

            ExecutionResult result = ExecutionResult.newBuilder()
                    .setJobId(jobId)
                    .setOutput(response.output)
                    .setError(response.error)
                    .setStatus(mapStatus(response.status))
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