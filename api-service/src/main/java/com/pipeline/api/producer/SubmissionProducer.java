package com.pipeline.api.producer;

import com.pipeline.api.config.SqsProperties;
import com.pipeline.messaging.SqsJsonMessenger;
import com.pipeline.schema.CodeSubmission;
import org.springframework.stereotype.Service;

@Service
public class SubmissionProducer {

    private final SqsJsonMessenger messenger;
    private final SqsProperties sqsProperties;

    public SubmissionProducer(SqsJsonMessenger messenger, SqsProperties sqsProperties) {
        this.messenger = messenger;
        this.sqsProperties = sqsProperties;
    }

    public void send(CodeSubmission submission) {
        messenger.send(sqsProperties.getSubmissionQueueUrl(), submission);
    }
}
