package com.ghatana.requirements.api.graphql.config;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import com.ghatana.requirements.api.graphql.resolver.QueryResolver;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for GraphQL schema and runtime setup.
 *
 * <p><b>Purpose:</b> Loads GraphQL schema from schema.graphqls resource file
 * and configures runtime wiring with data fetchers and type resolvers.
 * Provides singleton GraphQL instance for query execution.
 *
 * <p><b>Thread Safety:</b> Immutable after construction. GraphQL instance
 * is thread-safe for concurrent query execution.
 *
 * <p><b>Schema Loading:</b>
 * 1. Reads schema.graphqls from classpath
 * 2. Parses GraphQL SDL (Schema Definition Language)
 * 3. Builds runtime wiring with resolvers
 * 4. Creates executable GraphQL instance
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   GraphQLConfiguration config = new GraphQLConfiguration();
 *   GraphQL graphql = config.graphQL();
 *
 *   ExecutionInput input = ExecutionInput.newExecutionInput()
 *       .query("{ project(id: \"abc\") { name } }")
 *       .build();
 *
 *   ExecutionResult result = graphql.execute(input);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose GraphQL schema and runtime configuration
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 1.0.0
 */
public final class GraphQLConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(GraphQLConfiguration.class);
  private final GraphQL graphQL;

  /**
   * Create GraphQL configuration and load schema.
   */
  public GraphQLConfiguration(QueryResolver queryResolver) {
    try {
      // Load schema
      String schema = loadSchema();
      
      // Parse schema
      SchemaParser schemaParser = new SchemaParser();
      TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
      
      // Create runtime wiring with query resolver
      RuntimeWiring runtimeWiring = buildRuntimeWiring(queryResolver);
      
      // Create schema
      SchemaGenerator schemaGenerator = new SchemaGenerator();
      GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(
          typeDefinitionRegistry, 
          runtimeWiring
      );
      
      // Create GraphQL instance
      this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
      
      logger.info("GraphQL configuration initialized successfully");
    } catch (Exception e) {
      logger.error("Failed to initialize GraphQL", e);
      throw new RuntimeException("Failed to initialize GraphQL", e);
    }
  }

  /**
   * Get the configured GraphQL instance for executing queries.
   *
   * @return executable GraphQL instance
   */
  public GraphQL graphQL() {
    return this.graphQL;
  }

  /**
   * Build runtime wiring with data fetchers and type resolvers.
   *
   * <p>STUB: Replace with actual data fetchers for queries and mutations.
   * Production implementation should wire:
   * - Query resolvers (project, requirement, searchSimilar, etc.)
   * - Mutation resolvers (createProject, generateSuggestions, etc.)
   * - Type resolvers for custom types
   * - Scalar type coercion
   *
   * @return runtime wiring configuration
   */
  private RuntimeWiring buildRuntimeWiring(QueryResolver queryResolver) {
    return RuntimeWiring.newRuntimeWiring()
        .type("Query", typeWiring -> typeWiring
            // Map GraphQL query fields to resolver methods
            .dataFetcher("project", env -> {
              String id = env.getArgument("id");
              return queryResolver.getProject(id);
            })
            .dataFetcher("requirement", env -> {
              String id = env.getArgument("id");
              return queryResolver.getRequirement(id);
            })
            .dataFetcher("projects", env -> {
              int limit = env.getArgument("limit");
              int offset = env.getArgument("offset");
              return queryResolver.getProjects(limit, offset);
            })
            .dataFetcher("projectRequirements", env -> {
              String projectId = env.getArgument("projectId");
              int limit = env.getArgument("limit");
              int offset = env.getArgument("offset");
              return queryResolver.getProjectRequirements(projectId, limit, offset);
            })
            .dataFetcher("suggestion", env -> {
              String id = env.getArgument("id");
              return queryResolver.getSuggestion(id);
            })
            .dataFetcher("searchSimilar", env -> {
              String projectId = env.getArgument("projectId");
              String query = env.getArgument("query");
              int limit = env.getArgument("limit");
              float minSimilarity = env.getArgument("minSimilarity");
              return queryResolver.searchSimilar(projectId, query, limit, minSimilarity);
            })
            .dataFetcher("suggestionStats", env -> {
              String projectId = env.getArgument("projectId");
              return queryResolver.getSuggestionStats(projectId);
            })
        )
        // Add custom scalar types if needed
        // .scalar(GraphQLUUID.graphQLUUID)
        // .scalar(GraphQLDateTime.graphQLDateTime)
        .build();
  }

  /**
   * Load GraphQL schema from classpath resource.
   *
   * @return schema definition as string
   * @throws Exception if schema file not found
   */
  private String loadSchema() throws Exception {
    String schemaPath = "/schema.graphqls";
    InputStream stream = getClass().getResourceAsStream(schemaPath);

    if (stream == null) {
      throw new IllegalStateException("Schema file not found: " + schemaPath);
    }

    byte[] bytes = stream.readAllBytes();
    return new String(bytes, StandardCharsets.UTF_8);
  }
}