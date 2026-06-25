package com.pipeline.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codestream.sqs")
public class SqsProperties {

    private String region = "us-east-1";
    private String endpoint = "";
    private String submissionQueueUrl = "";
    private String resultQueueUrl = "";
    private long pollIntervalMs = 1000;
    private int maxMessages = 10;
    private int visibilityTimeoutSeconds = 60;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getSubmissionQueueUrl() {
        return submissionQueueUrl;
    }

    public void setSubmissionQueueUrl(String submissionQueueUrl) {
        this.submissionQueueUrl = submissionQueueUrl;
    }

    public String getResultQueueUrl() {
        return resultQueueUrl;
    }

    public void setResultQueueUrl(String resultQueueUrl) {
        this.resultQueueUrl = resultQueueUrl;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getVisibilityTimeoutSeconds() {
        return visibilityTimeoutSeconds;
    }

    public void setVisibilityTimeoutSeconds(int visibilityTimeoutSeconds) {
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
    }
}
