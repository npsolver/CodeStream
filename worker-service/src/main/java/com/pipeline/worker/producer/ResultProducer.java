package com.pipeline.worker.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.pipeline.schema.ExecutionResult;

@Service
public class ResultProducer {

    private final KafkaTemplate<String, ExecutionResult> kafkaTemplate;

    public ResultProducer(KafkaTemplate<String, ExecutionResult> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(ExecutionResult result) {
        kafkaTemplate.send("execution-results", result.getJobId().toString(), result);
    }
}