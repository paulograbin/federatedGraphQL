package com.example.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.*;
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
    private final SubgraphProperties subgraphProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private GraphQL graphQL;

    private final Set<String> FEDERATION_DIRECTIVES = Set.of("key", "extends", "external", "requires", "provides");

    public FederationGateway(SubgraphClient subgraphClient, SubgraphProperties subgraphProperties) {
        this.subgraphClient = subgraphClient;
        this.subgraphProperties = subgraphProperties;
    }

    @PostConstruct
    public void init() {
        buildSchema();
    }

    public void buildSchema() {
        log.info("Fetching schemas from subgraphs...");

        String showsSdl = subgraphClient.fetchServiceSdl(subgraphProperties.getShows().getUrl());
        log.info("Fetched shows-service SDL ({} chars)", showsSdl.length());

        String reviewsSdl = subgraphClient.fetchServiceSdl(subgraphProperties.getReviews().getUrl());
        log.info("Fetched reviews-service SDL ({} chars)", reviewsSdl.length());

        String composedSdl = composeSchemas(showsSdl, reviewsSdl);
        log.info("Composed supergraph SDL:\n{}", composedSdl);

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

        log.info("Federation gateway schema built successfully from live subgraphs");
    }

    private String composeSchemas(String showsSdl, String reviewsSdl) {
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry showsRegistry = parser.parse(showsSdl);
        TypeDefinitionRegistry reviewsRegistry = parser.parse(reviewsSdl);

        Map<String, ObjectTypeDefinition> composedTypes = new LinkedHashMap<>();
        List<FieldDefinition> queryFields = new ArrayList<>();

        extractTypes(showsRegistry, composedTypes, queryFields);
        extractTypes(reviewsRegistry, composedTypes, queryFields);

        StringBuilder sdl = new StringBuilder();

        if (!queryFields.isEmpty()) {
            sdl.append("type Query {\n");
            for (FieldDefinition field : queryFields) {
                sdl.append("    ").append(printField(field)).append("\n");
            }
            sdl.append("}\n\n");
        }

        for (Map.Entry<String, ObjectTypeDefinition> entry : composedTypes.entrySet()) {
            String typeName = entry.getKey();
            if (typeName.equals("Query")) continue;

            ObjectTypeDefinition typeDef = entry.getValue();
            sdl.append("type ").append(typeName).append(" {\n");
            for (FieldDefinition field : typeDef.getFieldDefinitions()) {
                if (hasDirective(field, "external")) continue;
                sdl.append("    ").append(printField(field)).append("\n");
            }
            sdl.append("}\n\n");
        }

        return sdl.toString();
    }

    private void extractTypes(TypeDefinitionRegistry registry,
                              Map<String, ObjectTypeDefinition> composedTypes,
                              List<FieldDefinition> queryFields) {
        for (Map.Entry<String, TypeDefinition> entry : registry.types().entrySet()) {
            String typeName = entry.getKey();
            TypeDefinition<?> typeDef = entry.getValue();

            if (typeName.startsWith("_") || typeName.equals("ErrorDetail") || typeName.equals("ErrorType")) {
                continue;
            }

            if (!(typeDef instanceof ObjectTypeDefinition objType)) continue;

            if (typeName.equals("Query")) {
                for (FieldDefinition field : objType.getFieldDefinitions()) {
                    if (!field.getName().startsWith("_")) {
                        queryFields.add(field);
                    }
                }
                continue;
            }

            if (composedTypes.containsKey(typeName)) {
                ObjectTypeDefinition existing = composedTypes.get(typeName);
                List<FieldDefinition> mergedFields = new ArrayList<>(existing.getFieldDefinitions());
                Set<String> existingFieldNames = mergedFields.stream()
                        .map(FieldDefinition::getName)
                        .collect(Collectors.toSet());
                for (FieldDefinition field : objType.getFieldDefinitions()) {
                    if (!existingFieldNames.contains(field.getName())) {
                        mergedFields.add(field);
                    }
                }
                composedTypes.put(typeName, existing.transform(b -> b.fieldDefinitions(mergedFields)));
            } else {
                composedTypes.put(typeName, objType);
            }
        }
    }

    private boolean hasDirective(FieldDefinition field, String directiveName) {
        return field.getDirectives().stream()
                .anyMatch(d -> d.getName().equals(directiveName));
    }

    private String printField(FieldDefinition field) {
        StringBuilder sb = new StringBuilder();
        sb.append(field.getName());

        if (field.getInputValueDefinitions() != null && !field.getInputValueDefinitions().isEmpty()) {
            sb.append("(");
            sb.append(field.getInputValueDefinitions().stream()
                    .map(iv -> iv.getName() + ": " + printType(iv.getType()))
                    .collect(Collectors.joining(", ")));
            sb.append(")");
        }

        sb.append(": ").append(printType(field.getType()));
        return sb.toString();
    }

    private String printType(Type<?> type) {
        if (type instanceof NonNullType nnt) {
            return printType(nnt.getType()) + "!";
        } else if (type instanceof ListType lt) {
            return "[" + printType(lt.getType()) + "]";
        } else if (type instanceof TypeName tn) {
            return tn.getName();
        }
        return "String";
    }

    private DataFetcher<List<Map<String, Object>>> showsFetcher() {
        return env -> {
            JsonNode result = subgraphClient.execute(
                    subgraphProperties.getShows().getUrl(),
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
                    subgraphProperties.getShows().getUrl(),
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
                    subgraphProperties.getReviews().getUrl(),
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
        return objectMapper.convertValue(node, List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, Map.class);
    }
}
