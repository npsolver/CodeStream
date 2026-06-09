CREATE TABLE execution_results (
    job_id      VARCHAR(36)  PRIMARY KEY,
    output      TEXT,
    error       TEXT,
    status      VARCHAR(20)  NOT NULL,
    executed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_execution_results_executed_at ON execution_results (executed_at DESC);
