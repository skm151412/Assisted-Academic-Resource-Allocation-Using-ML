package com.college.ara.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GeminiChatService {

    private static final String DEFAULT_MODEL = "meta/llama-4-maverick-17b-128e-instruct";
    private static final String DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String generateReply(String userMessage) {
        String apiKey = readEnv("NVIDIA_API_KEY", "GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NVIDIA API key is not configured on the server.");
        }

        String model = readEnv("NVIDIA_MODEL", "GEMINI_MODEL");
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }

        String baseUrl = readEnv("NVIDIA_BASE_URL", "");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }

        String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("model", model);
        body.put("temperature", 1.0);
        body.put("top_p", 1.0);
        body.put("max_tokens", 512);
        body.put("frequency_penalty", 0.0);
        body.put("presence_penalty", 0.0);
        body.put("stream", false);

        body.putArray("messages")
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("role", "system")
                        .put("content", buildSystemPrompt()))
                .add(OBJECT_MAPPER.createObjectNode()
                        .put("role", "user")
                        .put("content", userMessage));

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(body);
            HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(300).toMillis());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, statusCode);

            if (statusCode >= 400) {
                String bodySnippet = responseBody == null ? "" : responseBody.replaceAll("\\s+", " ");
                if (bodySnippet.length() > 280) {
                    bodySnippet = bodySnippet.substring(0, 280) + "...";
                }
                throw new RuntimeException("NVIDIA API request failed with status " + statusCode + ": " + bodySnippet);
            }

            return parseReply(responseBody);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to call NVIDIA API: " + ex.getMessage(), ex);
        }
    }

    private String readResponseBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String buildSystemPrompt() {
        return """
            You are the ARA (Automated Resource Allocation) assistant for a college timetable and room booking system.
            Answer only questions related to ARA usage: booking rooms, approvals, allocations, analytics, and navigation.
            If the question is outside ARA, politely say you can only help with ARA-related tasks.
            """;
    }

    private String readEnv(String preferred, String fallback) {
        String value = System.getenv(preferred);
        if (value != null && !value.isBlank()) {
            return value;
        }
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return System.getenv(fallback);
    }

    private String parseReply(String responseBody) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        JsonNode messageNode = root.path("choices")
                .path(0)
                .path("message");

        String content = messageNode.path("content").asText("").trim();
        String reasoning = messageNode.path("reasoning_content").asText("").trim();

        if (!content.isEmpty()) {
            return content;
        }

        if (!reasoning.isEmpty()) {
            return reasoning;
        }

        if (root.has("error")) {
            return "AI provider returned an error response.";
        }

        if (content.isEmpty()) {
            return "I could not generate a response right now. Please try again.";
        }

        return content;
    }
}
