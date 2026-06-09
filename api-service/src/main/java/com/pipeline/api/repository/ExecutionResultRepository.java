package com.pipeline.api.repository;

import com.pipeline.api.entity.ExecutionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionResultRepository extends JpaRepository<ExecutionResultEntity, String> {
}
