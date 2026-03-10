package com.ghatana.yappc.ai.requirements.api.graphql.config;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionEngine;
import com.ghatana.yappc.ai.requirements.api.graphql.mutation.SuggestionMutations;
import com.ghatana.yappc.ai.requirements.api.graphql.resolver.QueryResolver;
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
   * Full constructor wiring Query and Mutation resolvers.
   *
   * @param queryResolver    handles read queries
   * @param suggestionEngine LLM-backed engine used in generateSuggestions mutation
   * @param feedbackService  learning service used in recordFeedback mutation
   */
  public GraphQLConfiguration(
      QueryResolver queryResolver,
      SuggestionEngine suggestionEngine,
      FeedbackLearningService feedbackService) {
    try {
      String schema = loadSchema();
      SchemaParser schemaParser = new SchemaParser();
      TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
      RuntimeWiring runtimeWiring = buildRuntimeWiring(queryResolver, suggestionEngine, feedbackService);
      SchemaGenerator schemaGenerator = new SchemaGenerator();
      GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(
          typeDefinitionRegistry, runtimeWiring);
      this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
      logger.info("GraphQL configuration initialised (Query + Mutation wired)");
    } catch (Exception e) {
      logger.error("Failed to initialise GraphQL", e);
      throw new RuntimeException("Failed to initialise GraphQL", e);
    }
  }

  /**
   * Backward-compatible constructor — Mutation type uses no-op stubs.
   *
   * @deprecated Prefer {@link #GraphQLConfiguration(QueryResolver, SuggestionEngine, FeedbackLearningService)}
   */
  @Deprecated
  public GraphQLConfiguration(QueryResolver queryResolver) {
    this(queryResolver, null, null);
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
   * Build runtime wiring with Query and Mutation data fetchers.
   *
   * <p>Query resolvers delegate to {@link QueryResolver}.
   * Mutation resolvers use typed {@link SuggestionMutations} fetchers when services
   * are available; they return empty results when invoked without services (dev noop).
   *
   * @return runtime wiring configuration
   */
  private RuntimeWiring buildRuntimeWiring(
      QueryResolver queryResolver,
      SuggestionEngine suggestionEngine,
      FeedbackLearningService feedbackService) {

    // Mutation fetchers — use real services when available, noop otherwise
    graphql.schema.DataFetcher<java.util.concurrent.CompletableFuture<java.util.List<java.util.Map<String, Object>>>> generateFetcher =
        suggestionEngine != null
            ? new SuggestionMutations.GenerateSuggestionsMutationFetcher(suggestionEngine)
            : env -> java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of());

    graphql.schema.DataFetcher<java.util.concurrent.CompletableFuture<java.util.Map<String, Object>>> feedbackFetcher =
        feedbackService != null
            ? new SuggestionMutations.RecordFeedbackMutationFetcher(feedbackService)
            : env -> java.util.concurrent.CompletableFuture.completedFuture(java.util.Map.of(
                "id", "", "type", "NOOP", "rating", 0, "feedbackText", ""));

    return RuntimeWiring.newRuntimeWiring()
        .type("Query", typeWiring -> typeWiring
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
        .type("Mutation", typeWiring -> typeWiring
            .dataFetcher("generateSuggestions", generateFetcher)
            .dataFetcher("recordFeedback", feedbackFetcher)
        )
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