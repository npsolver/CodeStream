package com.pipeline.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.api.config.GeminiProperties;
import com.pipeline.api.dto.SuggestFixResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class GeminiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(GeminiSuggestionService.class);

    private static final String PROMPT_TEMPLATE = """
            You are a Python tutor helping fix code execution errors.

            The user ran this Python code and received this error output:

            --- CODE ---
            %s
            --- END CODE ---

            --- ERROR ---
            %s
            --- END ERROR ---

            Respond with ONLY valid JSON (no markdown code fences) using this exact shape:
            {
              "summary": "One short sentence describing what went wrong",
              "explanation": "2-4 sentences explaining the cause and how to fix it",
              "correctedCode": "The full corrected Python source code only"
            }
            """;

    private final GeminiProperties properties;
    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;

    public GeminiSuggestionService(
            GeminiProperties properties,
            RestClient geminiRestClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.geminiRestClient = geminiRestClient;
        this.objectMapper = objectMapper;
    }

    public SuggestFixResponse suggestFix(String code, String error) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini API key is not configured. Set the GEMINI_API_KEY environment variable.");
        }

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
        }

        if (error == null || error.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error is required");
        }

        String prompt = PROMPT_TEMPLATE.formatted(code.trim(), error.trim());
        String modelText = callGemini(prompt);
        return parseModelResponse(modelText);
    }

    private String callGemini(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 2048
                )
        );

        String model = properties.getModel();
        log.debug("Calling Gemini model: {}", model);

        try {
            JsonNode response = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        String errorBody = StreamUtils.copyToString(
                                clientResponse.getBody(), StandardCharsets.UTF_8);
                        String message = extractGoogleErrorMessage(errorBody);
                        log.warn("Gemini API error ({}): {}", clientResponse.getStatusCode(), message);
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
                    })
                    .body(JsonNode.class);

            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Empty response from Gemini API");
            }

            JsonNode textNode = response.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                String blockReason = response.path("candidates").path(0)
                        .path("finishReason").asText("");
                String message = blockReason.isBlank()
                        ? "Gemini returned no text. Try a different GEMINI_MODEL."
                        : "Gemini blocked the response (" + blockReason + ").";
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
            }

            return textNode.asText().trim();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected Gemini client error", ex);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to call Gemini API: " + ex.getMessage());
        }
    }

    private String extractGoogleErrorMessage(String errorBody) {
        try {
            JsonNode node = objectMapper.readTree(errorBody);
            String message = node.path("error").path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return errorBody.isBlank() ? "Unknown Gemini API error" : errorBody;
    }

    private SuggestFixResponse parseModelResponse(String modelText) {
        String json = stripMarkdownFences(modelText);

        try {
            JsonNode node = objectMapper.readTree(json);
            String summary = node.path("summary").asText("").trim();
            String explanation = node.path("explanation").asText("").trim();
            String correctedCode = node.path("correctedCode").asText("").trim();

            if (!summary.isBlank() && !correctedCode.isBlank()) {
                return new SuggestFixResponse(summary, explanation, correctedCode);
            }
        } catch (Exception ignored) {
            // fall through to plain-text fallback
        }

        return new SuggestFixResponse(
                "Suggested fix",
                modelText,
                "");
    }

    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
