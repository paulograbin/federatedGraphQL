package com.example.gateway;

import graphql.ExecutionResult;
import graphql.GraphqlErrorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class GraphQLController {

    private static final Logger log = LoggerFactory.getLogger(GraphQLController.class);

    private final FederationGateway gateway;

    public GraphQLController(FederationGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/graphql")
    public ResponseEntity<Map<String, Object>> graphql(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "errors", List.of(Map.of("message", "Query must not be empty"))
            ));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.get("variables");

        ExecutionResult result = gateway.execute(query, variables);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getData());
        if (result.getErrors() != null && !result.getErrors().isEmpty()) {
            response.put("errors", result.getErrors().stream()
                    .map(e -> {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("message", e.getMessage());
                        if (e.getPath() != null && !e.getPath().isEmpty()) {
                            err.put("path", e.getPath());
                        }
                        if (e.getLocations() != null && !e.getLocations().isEmpty()) {
                            err.put("locations", e.getLocations());
                        }
                        return err;
                    })
                    .toList());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reload")
    public ResponseEntity<String> reload() {
        gateway.buildSchema();
        return ResponseEntity.ok("Schema reloaded");
    }
}
