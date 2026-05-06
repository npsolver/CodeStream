package com.pipeline.worker.service;

public class ExecutionResponse {

    public final String output;
    public final String error;
    public final Status status;

    public enum Status {
        SUCCESS,
        ERROR,
        TIMEOUT
    }

    private ExecutionResponse(String output, String error, Status status) {
        this.output = output;
        this.error = error;
        this.status = status;
    }

    public static ExecutionResponse from(String stdout, String stderr, int exitCode) {
        if (exitCode == 0 && (stderr == null || stderr.isEmpty())) {
            return new ExecutionResponse(
                    stdout.isEmpty() ? null : stdout,
                    null,
                    Status.SUCCESS);
        } else {
            return new ExecutionResponse(
                    stdout.isEmpty() ? null : stdout,
                    stderr.isEmpty() ? "Execution failed" : stderr,
                    Status.ERROR);
        }
    }

    public static ExecutionResponse timeout() {
        return new ExecutionResponse(
                null,
                "Execution timed out",
                Status.TIMEOUT);
    }

    public static ExecutionResponse error(String message) {
        return new ExecutionResponse(
                null,
                message,
                Status.ERROR);
    }
}