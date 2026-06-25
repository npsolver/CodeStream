package com.pipeline.worker.producer;

import com.pipeline.messaging.SqsJsonMessenger;
import com.pipeline.schema.ExecutionResult;
import com.pipeline.worker.config.SqsProperties;
import org.springframework.stereotype.Service;

@Service
public class ResultProducer {

    private final SqsJsonMessenger messenger;
    private final SqsProperties sqsProperties;

    public ResultProducer(SqsJsonMessenger messenger, SqsProperties sqsProperties) {
        this.messenger = messenger;
        this.sqsProperties = sqsProperties;
    }

    public void send(ExecutionResult result) {
        messenger.send(sqsProperties.getResultQueueUrl(), result);
    }
}
