package com.example.gateway;

import graphql.ExecutionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class GraphQLController {

    private final FederationGateway gateway;

    public GraphQLController(FederationGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/graphql")
    public ResponseEntity<Map<String, Object>> graphql(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.get("variables");

        ExecutionResult result = gateway.execute(query, variables);

        Map<String, Object> response = Map.of(
                "data", result.getData() != null ? result.getData() : Map.of(),
                "errors", result.getErrors() != null ? result.getErrors() : java.util.List.of()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reload")
    public ResponseEntity<String> reload() {
        gateway.buildSchema();
        return ResponseEntity.ok("Schema reloaded");
    }
}
