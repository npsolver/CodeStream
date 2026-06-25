package com.pipeline.messaging;

public record ReceivedMessage<T>(T payload, String receiptHandle) {
}
