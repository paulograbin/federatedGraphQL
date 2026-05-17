package com.example.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class SubgraphClient {

    private static final Logger log = LoggerFactory.getLogger(SubgraphClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Retry> retries = new ConcurrentHashMap<>();

    public SubgraphClient() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(IOException.class, HttpTimeoutException.class)
                .ignoreExceptions(SubgraphException.class)
                .build();
        this.retryRegistry = RetryRegistry.of(retryConfig);
    }

    public JsonNode execute(String url, String query, Map<String, Object> variables) {
        String subgraphName = extractSubgraphName(url);
        CircuitBreaker cb = circuitBreakers.computeIfAbsent(subgraphName,
                name -> circuitBreakerRegistry.circuitBreaker(name));
        Retry retry = retries.computeIfAbsent(subgraphName,
                name -> retryRegistry.retry(name));

        Supplier<JsonNode> call = () -> doExecute(url, query, variables);
        Supplier<JsonNode> resilientCall = CircuitBreaker.decorateSupplier(cb,
                Retry.decorateSupplier(retry, call));

        try {
            return resilientCall.get();
        } catch (SubgraphException e) {
            throw e;
        } catch (Exception e) {
            throw new SubgraphException(url, e);
        }
    }

    private JsonNode doExecute(String url, String query, Map<String, Object> variables) {
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

        if (response.statusCode() >= 500) {
            log.error("Subgraph {} returned HTTP {}: {}", url, response.statusCode(), response.body());
            throw new SubgraphException(url, response.statusCode(),
                    "HTTP " + response.statusCode() + ": " + response.body());
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

    private String extractSubgraphName(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() + ":" + uri.getPort();
        } catch (Exception e) {
            return url;
        }
    }
}
