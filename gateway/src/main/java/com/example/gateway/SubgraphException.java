package com.example.gateway;

import java.util.List;
import java.util.Map;

public class SubgraphException extends RuntimeException {

    private final String subgraphUrl;
    private final int statusCode;
    private final List<Map<String, Object>> graphqlErrors;

    public SubgraphException(String subgraphUrl, int statusCode, String message) {
        super(message);
        this.subgraphUrl = subgraphUrl;
        this.statusCode = statusCode;
        this.graphqlErrors = List.of();
    }

    public SubgraphException(String subgraphUrl, List<Map<String, Object>> graphqlErrors) {
        super("Subgraph " + subgraphUrl + " returned errors: " + graphqlErrors);
        this.subgraphUrl = subgraphUrl;
        this.statusCode = 200;
        this.graphqlErrors = graphqlErrors;
    }

    public SubgraphException(String subgraphUrl, Throwable cause) {
        super("Failed to reach subgraph at " + subgraphUrl, cause);
        this.subgraphUrl = subgraphUrl;
        this.statusCode = -1;
        this.graphqlErrors = List.of();
    }

    public String getSubgraphUrl() { return subgraphUrl; }
    public int getStatusCode() { return statusCode; }
    public List<Map<String, Object>> getGraphqlErrors() { return graphqlErrors; }
}
