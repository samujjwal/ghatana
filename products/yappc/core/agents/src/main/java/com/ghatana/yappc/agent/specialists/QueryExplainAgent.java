package com.ghatana.yappc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug micro-agent that analyzes SQL query execution plans for optimization.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that analyzes SQL query execution plans for optimization
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class QueryExplainAgent extends YAPPCAgentBase<QueryExplainInput, QueryExplainOutput> {

  private static final Logger log = LoggerFactory.getLogger(QueryExplainAgent.class);

  private final MemoryStore memoryStore;

  public QueryExplainAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<QueryExplainInput>, StepResult<QueryExplainOutput>> generator) {
    super(
        "QueryExplainAgent",
        "debug.query-explain",
        new StepContract(
            "debug.query-explain",
            "#/definitions/QueryExplainInput",
            "#/definitions/QueryExplainOutput",
            List.of("debug", "query-analysis", "database"),
            Map.of("description", "Debug micro-agent that analyzes SQL query execution plans for optimization", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull QueryExplainInput input) {
    if (input.query() == null || input.query().isEmpty()) {
      return ValidationResult.fail("query cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<QueryExplainInput> perceive(
      @NotNull StepRequest<QueryExplainInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.query-explain request: {}", request.input().query().substring(0, Math.min(50, request.input().query().length())));
    return request;
  }

  /** Rule-based generator for debug.query-explain. */
  public static class QueryExplainGenerator
      implements OutputGenerator<StepRequest<QueryExplainInput>, StepResult<QueryExplainOutput>> {

    private static final Logger log = LoggerFactory.getLogger(QueryExplainGenerator.class);

    @Override
    public @NotNull Promise<StepResult<QueryExplainOutput>> generate(
        @NotNull StepRequest<QueryExplainInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      QueryExplainInput stepInput = input.input();

      log.info("Executing debug.query-explain for: {}", stepInput.query());

      QueryExplainOutput output =
          new QueryExplainOutput(
              "debug.query-explain-" + UUID.randomUUID(),
              List.of(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.query-explain", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<QueryExplainInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("QueryExplainGenerator")
          .type("rule-based")
          .description("Debug micro-agent that analyzes SQL query execution plans for optimization")
          .version("1.0.0")
          .build();
    }
  }
}
