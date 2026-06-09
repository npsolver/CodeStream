package com.pipeline.api.controller;

import com.pipeline.api.dto.SuggestFixRequest;
import com.pipeline.api.dto.SuggestFixResponse;
import com.pipeline.api.service.GeminiSuggestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {

    private final GeminiSuggestionService geminiSuggestionService;

    public AiController(GeminiSuggestionService geminiSuggestionService) {
        this.geminiSuggestionService = geminiSuggestionService;
    }

    @PostMapping("/ai/suggest-fix")
    public SuggestFixResponse suggestFix(@RequestBody SuggestFixRequest request) {
        return geminiSuggestionService.suggestFix(request.code(), request.error());
    }
}
