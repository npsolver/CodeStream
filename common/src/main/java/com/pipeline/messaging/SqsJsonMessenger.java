package com.pipeline.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.List;

public class SqsJsonMessenger {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    public SqsJsonMessenger(SqsClient sqsClient, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    public void send(String queueUrl, Object payload) {
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize SQS message", ex);
        }
    }

    public <T> List<ReceivedMessage<T>> receive(
            String queueUrl,
            Class<T> type,
            int maxMessages,
            int visibilityTimeoutSeconds) {
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(maxMessages)
                        .waitTimeSeconds(20)
                        .visibilityTimeout(visibilityTimeoutSeconds)
                        .build())
                .messages();

        List<ReceivedMessage<T>> received = new ArrayList<>(messages.size());
        for (Message message : messages) {
            try {
                T payload = objectMapper.readValue(message.body(), type);
                received.add(new ReceivedMessage<>(payload, message.receiptHandle()));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to deserialize SQS message", ex);
            }
        }
        return received;
    }

    public void delete(String queueUrl, String receiptHandle) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build());
    }
}
