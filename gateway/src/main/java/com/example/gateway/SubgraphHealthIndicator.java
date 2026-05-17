package com.example.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SubgraphHealthIndicator implements HealthIndicator {

    private final SubgraphClient subgraphClient;
    private final SubgraphProperties subgraphProperties;

    public SubgraphHealthIndicator(SubgraphClient subgraphClient, SubgraphProperties subgraphProperties) {
        this.subgraphClient = subgraphClient;
        this.subgraphProperties = subgraphProperties;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        checkSubgraph(builder, "shows", subgraphProperties.getShows().getUrl());
        checkSubgraph(builder, "reviews", subgraphProperties.getReviews().getUrl());

        return builder.build();
    }

    private void checkSubgraph(Health.Builder builder, String name, String url) {
        try {
            subgraphClient.fetchServiceSdl(url);
            builder.withDetail(name, "UP");
        } catch (Exception e) {
            builder.down().withDetail(name, "DOWN: " + e.getMessage());
        }
    }
}
