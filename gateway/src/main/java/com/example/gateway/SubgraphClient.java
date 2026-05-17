package com.example.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SubgraphClient {

    private static final Logger log = LoggerFactory.getLogger(SubgraphClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode execute(String url, String query, Map<String, Object> variables) {
        Map<String, Object> body = Map.of(
                "query", query,
                "variables", variables != null ? variables : Map.of()
        );

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (Exception e) {
            throw new SubgraphException(url, e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SubgraphException(url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SubgraphException(url, e);
        }

        if (response.statusCode() != 200) {
            log.error("Subgraph {} returned HTTP {}: {}", url, response.statusCode(), response.body());
            throw new SubgraphException(url, response.statusCode(),
                    "HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode result;
        try {
            result = objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new SubgraphException(url, e);
        }

        if (result.has("errors") && !result.get("errors").isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = objectMapper.convertValue(result.get("errors"), List.class);
            log.warn("Subgraph {} returned GraphQL errors: {}", url, errors);
            if (!result.has("data") || result.get("data").isNull()) {
                throw new SubgraphException(url, errors);
            }
        }

        return result;
    }

    public String fetchServiceSdl(String url) {
        JsonNode result = execute(url, "{ _service { sdl } }", null);
        return result.at("/data/_service/sdl").asText();
    }
}
