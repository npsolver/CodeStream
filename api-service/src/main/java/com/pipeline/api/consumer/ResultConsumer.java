package com.pipeline.api.consumer;

import com.pipeline.api.config.SqsProperties;
import com.pipeline.api.store.ResultStore;
import com.pipeline.messaging.ReceivedMessage;
import com.pipeline.messaging.SqsJsonMessenger;
import com.pipeline.schema.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultConsumer.class);

    private final ResultStore store;
    private final SqsJsonMessenger messenger;
    private final SqsProperties sqsProperties;

    public ResultConsumer(
            ResultStore store,
            SqsJsonMessenger messenger,
            SqsProperties sqsProperties) {
        this.store = store;
        this.messenger = messenger;
        this.sqsProperties = sqsProperties;
    }

    @Scheduled(fixedDelayString = "${codestream.sqs.poll-interval-ms:1000}")
    public void pollResults() {
        for (ReceivedMessage<ExecutionResult> message : messenger.receive(
                sqsProperties.getResultQueueUrl(),
                ExecutionResult.class,
                sqsProperties.getMaxMessages(),
                sqsProperties.getVisibilityTimeoutSeconds())) {
            store.save(message.payload());
            messenger.delete(sqsProperties.getResultQueueUrl(), message.receiptHandle());
            log.debug("Stored result for job {}", message.payload().jobId());
        }
    }
}
