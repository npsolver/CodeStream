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
import java.util.LinkedHashMap;
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
        Map<String, Object> body = buildRequestBody(prompt);

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

            String finishReason = response.path("candidates").path(0)
                    .path("finishReason").asText("");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                String message = finishReason.isBlank()
                        ? "Gemini returned no text. Try a different GEMINI_MODEL."
                        : "Gemini blocked the response (" + finishReason + ").";
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
            }

            if ("MAX_TOKENS".equals(finishReason)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI response was truncated. Please try again.");
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

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> responseSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "summary", Map.of("type", "string"),
                        "explanation", Map.of("type", "string"),
                        "correctedCode", Map.of("type", "string")
                ),
                "required", List.of("summary", "explanation", "correctedCode")
        );

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", 8192);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", responseSchema);
        // Prevent thinking tokens from consuming the output budget on 2.5 models.
        generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(
                Map.of("parts", List.of(
                        Map.of("text", prompt)
                ))
        ));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private SuggestFixResponse parseModelResponse(String modelText) {
        String json = stripMarkdownFences(modelText);

        try {
            JsonNode node = objectMapper.readTree(json);
            String summary = node.path("summary").asText("").trim();
            String explanation = node.path("explanation").asText("").trim();
            String correctedCode = node.path("correctedCode").asText("").trim();

            if (summary.isBlank() || correctedCode.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI response was incomplete. Please try again.");
            }

            return new SuggestFixResponse(summary, explanation, correctedCode);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to parse Gemini JSON response: {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI response could not be parsed. Please try again.");
        }
    }

    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }

        String content = trimmed.substring(firstNewline + 1);
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3).trim();
        }
        return content;
    }
}
