package com.example.app.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpStreamingTemplate {

    private final ObjectMapper objectMapper;

    public void streamJsonResponse(String urlStr, String requestBody,
            Consumer<String> responseCallback) throws Exception {

        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(0);

        connection.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode node = objectMapper.readTree(line);
                    String response = node.has("response") ? node.get("response").asText() : "";
                    if (!response.isEmpty()) {
                        responseCallback.accept(response);
                    }
                } catch (Exception ignored) {
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    public void streamSseResponse(String urlStr, String requestBody, String authHeader,
            Consumer<String> responseCallback) throws Exception {

        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(300000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Connection", "keep-alive");
        if (authHeader != null && !authHeader.isEmpty()) {
            connection.setRequestProperty("Authorization", authHeader);
        }
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(0);

        connection.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;

                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if (data.equals("[DONE]")) {
                        break;
                    }
                    responseCallback.accept(data);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    public void streamJsonWithErrorHandler(String urlStr, String requestBody,
            Consumer<String> responseCallback, Runnable onComplete,
            Consumer<Exception> onError) {
        try {
            streamJsonResponse(urlStr, requestBody, responseCallback);
            if (onComplete != null) {
                onComplete.run();
            }
        } catch (Exception e) {
            log.error("Streaming error: {}", e.getMessage());
            if (onError != null) {
                onError.accept(e);
            } else {
                throw new RuntimeException("AI model connection timeout or service unavailable", e);
            }
        }
    }
}
