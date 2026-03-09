package com.ghatana.yappc.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.graphql.CollaborationResolver;
import com.ghatana.yappc.api.graphql.WorkspaceResolver;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for GraphQL requests.
 *
 * <p>Delegates data fetching to domain-specific resolver classes
 * ({@link WorkspaceResolver}, {@link CollaborationResolver}) to keep
 * this controller focused on schema wiring and HTTP transport.</p>
 *
 * @doc.type class
 * @doc.purpose GraphQL HTTP controller with extracted resolvers
 * @doc.layer product
 * @doc.pattern Controller
 */
public class GraphQLController {
    private static final Logger logger = LoggerFactory.getLogger(GraphQLController.class);
    private final GraphQL graphQL;
    private final ObjectMapper objectMapper;

    @Inject
    public GraphQLController(
            ObjectMapper objectMapper,
            WorkspaceResolver workspaceResolver,
            CollaborationResolver collaborationResolver,
            TenantContextExtractor tenantContextExtractor
    ) {
        this.objectMapper = objectMapper;
        this.graphQL = initGraphQL(workspaceResolver, collaborationResolver);
    }

    private GraphQL initGraphQL(
            WorkspaceResolver workspaceResolver,
            CollaborationResolver collaborationResolver
    ) {
        try (var stream = getClass().getResourceAsStream("/graphql/schema.graphqls")) {
            if (stream == null) throw new RuntimeException("schema.graphqls not found");

            TypeDefinitionRegistry typeRegistry = new SchemaParser()
                    .parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            RuntimeWiring runtimeWiring = buildWiring(workspaceResolver, collaborationResolver);

            GraphQLSchema graphQLSchema = new SchemaGenerator()
                    .makeExecutableSchema(typeRegistry, runtimeWiring);
            return GraphQL.newGraphQL(graphQLSchema).build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize GraphQL", e);
        }
    }

    private RuntimeWiring buildWiring(
            WorkspaceResolver workspaceResolver,
            CollaborationResolver collaborationResolver
    ) {
        return RuntimeWiring.newRuntimeWiring()
                .type("Query", typeWiring -> typeWiring
                        .dataFetcher("workspaces", workspaceResolver.workspaces())
                        .dataFetcher("workspace", workspaceResolver.workspace())
                        .dataFetcher("teams", collaborationResolver.teams())
                        .dataFetcher("notifications", collaborationResolver.notifications())
                        .dataFetcher("channels", collaborationResolver.channels())
                        .dataFetcher("channel", collaborationResolver.channel())
                )
                .type("Mutation", typeWiring -> typeWiring
                        .dataFetcher("createWorkspace", workspaceResolver.createWorkspace())
                        .dataFetcher("createChannel", collaborationResolver.createChannel())
                )
                .scalar(graphql.scalars.ExtendedScalars.DateTime)
                .scalar(graphql.scalars.ExtendedScalars.Json)
                .build();
    }

    /**
     * Handle incoming GraphQL POST request.
     *
     * @param request HTTP request containing GraphQL query
     * @return Promise of HTTP response with GraphQL result
     */
    public Promise<HttpResponse> handleRequest(HttpRequest request) {
        return request.loadBody().map($ -> {
            try {
                String bodyString = request.getBody().asString(StandardCharsets.UTF_8);
                Map<String, Object> json = objectMapper.readValue(bodyString, new TypeReference<>() {});
                String query = (String) json.get("query");
                Object rawVariables = json.get("variables");
                Map<String, Object> variables;
                if (rawVariables instanceof Map<?, ?> rawMap) {
                    Map<String, Object> mapped = new HashMap<>();
                    rawMap.forEach((k, v) -> mapped.put(String.valueOf(k), v));
                    variables = mapped;
                } else {
                    variables = Collections.emptyMap();
                }

                ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                        .query(query)
                        .variables(variables)
                        .graphQLContext(contextBuilder ->
                                contextBuilder.of("requestContext", TenantContextExtractor.extract(request)))
                        .build();

                ExecutionResult executionResult = graphQL.execute(executionInput);

                return HttpResponse.ok200()
                        .withJson(objectMapper.writeValueAsString(executionResult.toSpecification()))
                        .build();
            } catch (Exception e) {
                logger.error("GraphQL Execution Error", e);
                return HttpResponse.ofCode(500)
                        .withJson("{\"errors\":[{\"message\":\"Internal Server Error\"}]}")
                        .build();
            }
        });
    }
}
