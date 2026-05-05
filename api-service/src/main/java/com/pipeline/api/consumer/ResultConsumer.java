package com.pipeline.api.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.pipeline.schema.ExecutionResult;
import com.pipeline.api.store.ResultStore;

@Service
public class ResultConsumer {

    private final ResultStore store;

    public ResultConsumer(ResultStore store) {
        this.store = store;
    }

    @KafkaListener(topics = "execution-results", groupId = "api-group")
    public void consume(ExecutionResult result) {
        store.save(result);
        System.out.println("Stored result for job: " + result.getJobId());
    }
}