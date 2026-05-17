package com.example.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FederationGateway {

    private static final Logger log = LoggerFactory.getLogger(FederationGateway.class);

    private final SubgraphClient subgraphClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private GraphQL graphQL;

    private final List<SubgraphConfig> subgraphs = List.of(
            new SubgraphConfig("shows", "http://localhost:8081/graphql"),
            new SubgraphConfig("reviews", "http://localhost:8082/graphql")
    );

    public FederationGateway(SubgraphClient subgraphClient) {
        this.subgraphClient = subgraphClient;
    }

    @PostConstruct
    public void init() {
        buildSchema();
    }

    public void buildSchema() {
        String composedSdl = """
                type Query {
                    shows: [Show]
                    show(id: ID!): Show
                }

                type Show {
                    id: ID!
                    title: String
                    releaseYear: Int
                    reviews: [Review]
                }

                type Review {
                    id: ID!
                    starRating: Int
                    comment: String
                }
                """;

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry registry = schemaParser.parse(composedSdl);

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("shows", showsFetcher())
                        .dataFetcher("show", showFetcher())
                )
                .type("Show", builder -> builder
                        .dataFetcher("reviews", reviewsFetcher())
                )
                .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema schema = schemaGenerator.makeExecutableSchema(registry, wiring);
        this.graphQL = GraphQL.newGraphQL(schema).build();

        log.info("Federation gateway schema built successfully");
    }

    private DataFetcher<List<Map<String, Object>>> showsFetcher() {
        return env -> {
            JsonNode result = subgraphClient.execute(
                    "http://localhost:8081/graphql",
                    "{ shows { id title releaseYear } }",
                    null
            );
            return parseList(result.at("/data/shows"));
        };
    }

    private DataFetcher<Map<String, Object>> showFetcher() {
        return env -> {
            String id = env.getArgument("id");
            JsonNode result = subgraphClient.execute(
                    "http://localhost:8081/graphql",
                    "query ($id: ID!) { show(id: $id) { id title releaseYear } }",
                    Map.of("id", id)
            );
            return parseObject(result.at("/data/show"));
        };
    }

    private DataFetcher<List<Map<String, Object>>> reviewsFetcher() {
        return env -> {
            Map<String, Object> show = env.getSource();
            String showId = (String) show.get("id");

            String query = """
                    query ($representations: [_Any!]!) {
                        _entities(representations: $representations) {
                            ... on Show {
                                reviews {
                                    id
                                    starRating
                                    comment
                                }
                            }
                        }
                    }
                    """;

            List<Map<String, Object>> representations = List.of(
                    Map.of("__typename", "Show", "id", showId)
            );

            JsonNode result = subgraphClient.execute(
                    "http://localhost:8082/graphql",
                    query,
                    Map.of("representations", representations)
            );

            JsonNode entity = result.at("/data/_entities/0/reviews");
            return parseList(entity);
        };
    }

    public ExecutionResult execute(String query, Map<String, Object> variables) {
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .variables(variables != null ? variables : Map.of())
                .build();
        return graphQL.execute(input);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(node, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
