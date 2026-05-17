package com.example.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
public class SubgraphClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode execute(String url, String query, Map<String, Object> variables) {
        try {
            Map<String, Object> body = Map.of(
                    "query", query,
                    "variables", variables != null ? variables : Map.of()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new RuntimeException("Failed to query subgraph at " + url, e);
        }
    }

    public String fetchServiceSdl(String url) {
        JsonNode result = execute(url, "{ _service { sdl } }", null);
        return result.at("/data/_service/sdl").asText();
    }
}
