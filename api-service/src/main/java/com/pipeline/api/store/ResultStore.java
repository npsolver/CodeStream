package com.pipeline.api.store;

import com.pipeline.api.entity.ExecutionResultEntity;
import com.pipeline.api.repository.ExecutionResultRepository;
import com.pipeline.schema.ExecutionResult;
import com.pipeline.schema.ExecutionStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ResultStore {

    private final ExecutionResultRepository repository;

    public ResultStore(ExecutionResultRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(ExecutionResult result) {
        ExecutionResultEntity entity = new ExecutionResultEntity(
                result.getJobId().toString(),
                result.getOutput() != null ? result.getOutput().toString() : null,
                result.getError() != null ? result.getError().toString() : null,
                result.getStatus().name(),
                result.getTimestamp());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public ExecutionResult get(String jobId) {
        return repository.findById(jobId)
                .map(this::toAvro)
                .orElse(null);
    }

    private ExecutionResult toAvro(ExecutionResultEntity entity) {
        return ExecutionResult.newBuilder()
                .setJobId(entity.getJobId())
                .setOutput(entity.getOutput())
                .setError(entity.getError())
                .setStatus(ExecutionStatus.valueOf(entity.getStatus()))
                .setTimestamp(entity.getExecutedAt())
                .build();
    }
}
