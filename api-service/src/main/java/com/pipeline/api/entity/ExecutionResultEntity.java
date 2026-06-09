package com.pipeline.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "execution_results")
public class ExecutionResultEntity {

    @Id
    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected ExecutionResultEntity() {
    }

    public ExecutionResultEntity(
            String jobId,
            String output,
            String error,
            String status,
            Instant executedAt) {
        this.jobId = jobId;
        this.output = output;
        this.error = error;
        this.status = status;
        this.executedAt = executedAt;
    }

    public String getJobId() {
        return jobId;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
