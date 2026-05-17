# GraphQL Federation Gateway - Learning Experiment

A fully Java-based GraphQL federation system built from scratch for learning purposes. Implements a custom federation gateway that composes schemas from multiple subgraphs at runtime, inspired by Apollo Federation but without any Apollo tooling.

## Architecture

```
Client
  │
  ▼
┌─────────────────────────────┐
│  Federation Gateway (:9000) │  ← Custom Java gateway (graphql-java)
│  - Schema composition       │
│  - Query routing            │
│  - DataLoader batching      │
│  - Circuit breaker / retry  │
└──────────┬──────────┬───────┘
           │          │
     ┌─────┘          └─────┐
     ▼                      ▼
┌──────────────┐   ┌────────────────┐
│ Shows Service│   │ Reviews Service│
│   (:8081)    │   │    (:8082)     │
│  Netflix DGS │   │   Netflix DGS  │
└──────────────┘   └────────────────┘
```

## What This Demonstrates

- **GraphQL Federation** — `@key`, `@extends`, `@external` directives; `_entities` query for entity resolution across service boundaries
- **Custom Gateway in Java** — No Apollo Router or Gateway; schema fetched from subgraphs via `_service { sdl }`, parsed and composed with graphql-java
- **DataLoader Batching** — Solves the N+1 problem by batching all entity resolution into a single `_entities` call per subgraph
- **Resilience Patterns** — Per-subgraph circuit breakers and retries via Resilience4j
- **Virtual Threads (Java 25)** — Tomcat handles requests on virtual threads; blocking HTTP calls don't consume platform threads
- **Distributed Tracing** — W3C `traceparent` header propagation across services; traces visible end-to-end in Jaeger
- **Metrics & Dashboards** — Micrometer + Prometheus + Grafana with pre-provisioned dashboards
- **Dynamic Schema Reload** — `POST /reload` rebuilds the supergraph from live subgraph SDLs without restart

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 3.5.5 |
| Subgraph GraphQL | Netflix DGS 10.6.0 |
| Gateway GraphQL | graphql-java 22.3 + java-dataloader 3.4.0 |
| Resilience | Resilience4j 2.2.0 (circuit breaker + retry) |
| Metrics | Micrometer → Prometheus |
| Tracing | Micrometer Tracing → OpenTelemetry → Jaeger |
| Dashboards | Grafana (provisioned) |
| Containers | Docker multi-stage builds |
| Build | Maven (multi-module) |

## Project Structure

```
graphqlExperiment/
├── gateway/                  # Custom federation gateway
├── shows-service/            # Subgraph: owns Show type (id, title, releaseYear)
├── reviews-service/          # Subgraph: extends Show with reviews
├── monitoring/
│   ├── prometheus/           # Prometheus scrape config
│   └── grafana/
│       ├── provisioning/     # Datasources (Prometheus, Jaeger)
│       └── dashboards/       # Pre-built dashboard JSON
├── docker-compose.yml        # Full stack orchestration
└── pom.xml                   # Parent POM
```

## Running

```bash
docker compose up --build
```

Services will be available at:

| Service | URL |
|---------|-----|
| Gateway (GraphQL) | http://localhost:9000/graphql |
| Shows Service | http://localhost:8081/graphql |
| Reviews Service | http://localhost:8082/graphql |
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9091 |
| Jaeger UI | http://localhost:16686 |

## Example Queries

**All shows with reviews (exercises federation + DataLoader batching):**

```graphql
{
  shows {
    id
    title
    releaseYear
    reviews {
      starRating
      comment
    }
  }
}
```

```bash
curl -s http://localhost:9000/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ shows { id title releaseYear reviews { starRating comment } } }"}'
```

**Single show by ID:**

```graphql
{
  show(id: "1") {
    title
    reviews {
      starRating
      comment
    }
  }
}
```

## Observability

- **Traces** — Open Jaeger at http://localhost:16686, select `federation-gateway` service. Queries that fetch reviews will show spans crossing from gateway → shows-service and gateway → reviews-service.
- **Metrics** — Open Grafana at http://localhost:3000. The "GraphQL Federation Gateway" dashboard shows subgraph p95 latency, request rates, error rates, and JVM memory.
- **Health** — `GET http://localhost:9000/actuator/health` reports subgraph connectivity.

## Key Design Decisions

1. **No Apollo tooling** — The gateway is pure Java to understand what federation actually does under the hood (schema composition, entity resolution, representation forwarding).
2. **Blocking HTTP + virtual threads** — Instead of reactive/async complexity, uses simple blocking `HttpClient` calls on Java 25 virtual threads for equivalent scalability with simpler code.
3. **DataLoader for batching** — graphql-java's DataLoader collects all entity keys during execution and dispatches a single batched `_entities` request per subgraph.
4. **Per-subgraph resilience** — Each subgraph gets its own circuit breaker and retry policy so one failing service doesn't cascade.
5. **Simulated latency** — Reviews service adds random 100ms-1000ms delay to make tracing and metrics more interesting to observe.

## Remaining Ideas

- Field selection forwarding (only request fields the client asked for)
- Query depth/complexity limits to prevent abuse
- Integration tests for gateway composition logic
