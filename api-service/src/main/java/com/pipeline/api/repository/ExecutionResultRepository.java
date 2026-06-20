package com.pipeline.api.repository;

import com.pipeline.api.entity.ExecutionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ExecutionResultRepository extends JpaRepository<ExecutionResultEntity, String> {

    long deleteByExecutedAtBefore(Instant cutoff);
}
