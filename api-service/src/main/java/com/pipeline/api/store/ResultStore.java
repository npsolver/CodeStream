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
                result.jobId(),
                result.output(),
                result.error(),
                result.status().name(),
                result.timestamp());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public ExecutionResult get(String jobId) {
        return repository.findById(jobId)
                .map(this::toModel)
                .orElse(null);
    }

    private ExecutionResult toModel(ExecutionResultEntity entity) {
        return new ExecutionResult(
                entity.getJobId(),
                entity.getOutput(),
                entity.getError(),
                ExecutionStatus.valueOf(entity.getStatus()),
                entity.getExecutedAt());
    }
}
