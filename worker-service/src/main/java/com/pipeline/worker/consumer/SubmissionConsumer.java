package com.pipeline.worker.consumer;

import com.pipeline.messaging.ReceivedMessage;
import com.pipeline.messaging.SqsJsonMessenger;
import com.pipeline.schema.CodeSubmission;
import com.pipeline.schema.ExecutionResult;
import com.pipeline.schema.ExecutionStatus;
import com.pipeline.worker.config.SqsProperties;
import com.pipeline.worker.producer.ResultProducer;
import com.pipeline.worker.service.CodeExecutionService;
import com.pipeline.worker.service.ExecutionResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SubmissionConsumer {

    private final CodeExecutionService executor;
    private final ResultProducer producer;
    private final SqsJsonMessenger messenger;
    private final SqsProperties sqsProperties;

    private ExecutionStatus mapStatus(ExecutionResponse.Status status) {
        return switch (status) {
            case SUCCESS -> ExecutionStatus.SUCCESS;
            case ERROR -> ExecutionStatus.ERROR;
            case TIMEOUT -> ExecutionStatus.TIMEOUT;
        };
    }

    public SubmissionConsumer(
            CodeExecutionService executor,
            ResultProducer producer,
            SqsJsonMessenger messenger,
            SqsProperties sqsProperties) {
        this.executor = executor;
        this.producer = producer;
        this.messenger = messenger;
        this.sqsProperties = sqsProperties;
    }

    @Scheduled(fixedDelayString = "${codestream.sqs.poll-interval-ms:1000}")
    public void pollSubmissions() {
        for (ReceivedMessage<CodeSubmission> message : messenger.receive(
                sqsProperties.getSubmissionQueueUrl(),
                CodeSubmission.class,
                sqsProperties.getMaxMessages(),
                sqsProperties.getVisibilityTimeoutSeconds())) {
            process(message.payload());
            messenger.delete(sqsProperties.getSubmissionQueueUrl(), message.receiptHandle());
        }
    }

    private void process(CodeSubmission submission) {
        String jobId = submission.jobId();

        try {
            ExecutionResponse response = executor.executePython(submission.code());

            ExecutionResult result = new ExecutionResult(
                    jobId,
                    response.output,
                    response.error,
                    mapStatus(response.status),
                    Instant.now());

            producer.send(result);

        } catch (Exception e) {
            ExecutionResult result = new ExecutionResult(
                    jobId,
                    null,
                    e.getMessage(),
                    ExecutionStatus.ERROR,
                    Instant.now());

            producer.send(result);
        }
    }
}
