package com.pipeline.api.store;

import org.springframework.stereotype.Component;
import com.pipeline.schema.ExecutionResult;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResultStore {

    private final ConcurrentHashMap<String, ExecutionResult> store = new ConcurrentHashMap<>();

    public void save(ExecutionResult result) {
        store.put(result.getJobId().toString(), result);
    }

    public ExecutionResult get(String jobId) {
        return store.get(jobId);
    }
}