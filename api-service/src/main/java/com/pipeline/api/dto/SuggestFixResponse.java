package com.pipeline.api.dto;

public record SuggestFixResponse(
        String summary,
        String explanation,
        String correctedCode) {
}
