/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.registry.YamlAgentCatalogLoader;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import com.ghatana.yappc.api.repository.jdbc.JdbcWorkflowStateStore;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for workflow engine, agent catalog, pipeline loader, and workflow controllers.
 *
 * @doc.type class
 * @doc.purpose Workflow and orchestration DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class WorkflowModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowModule.class);

  // ========== Durable Workflow Engine ==========

  @Provides
  DurableWorkflowEngine durableWorkflowEngine(JdbcWorkflowStateStore stateStore) {
    logger.info("Creating DurableWorkflowEngine with JdbcWorkflowStateStore");
    return DurableWorkflowEngine.builder()
        .stateStore(stateStore)
        .defaultTimeout(java.time.Duration.ofMinutes(30))
        .defaultMaxRetries(3)
        .build();
  }

  // ========== Agent Catalog ==========

  /**
   * @doc.layer product
   * @doc.pattern Registry
   * @doc.gaa.lifecycle perceive
   */
  @Provides
  CatalogRegistry catalogRegistry() {
    YamlAgentCatalogLoader catalogLoader = new YamlAgentCatalogLoader();
    CatalogRegistry registry = CatalogRegistry.empty();
    int agentCount = catalogLoader.loadInto(registry);
    logger.info(
        "CatalogRegistry populated with {} agent definition(s) from '{}'",
        agentCount,
        catalogLoader.getCatalogRoot());
    return registry;
  }

  // ========== Pipeline Definition Loader ==========

  /**
   * @doc.type class
   * @doc.purpose Boot-time YAML pipeline manifest loader and registry
   * @doc.layer product
   * @doc.pattern Registry, Loader
   */
  @Provides
  com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader pipelineDefinitionLoader(
      ObjectMapper objectMapper) {
    com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader loader =
        new com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader(objectMapper);
    logger.info("PipelineDefinitionLoader ready — {} pipeline(s) registered", loader.size());
    return loader;
  }

  // ========== Workflow Agent Services ==========

  @Provides
  com.ghatana.agent.workflow.WorkflowAgentRegistry workflowAgentRegistry() {
    logger.info("Creating InMemoryWorkflowAgentRegistry");
    return new com.ghatana.agent.workflow.InMemoryWorkflowAgentRegistry();
  }

  @Provides
  com.ghatana.agent.workflow.WorkflowAgentService workflowAgentService(
      com.ghatana.agent.workflow.WorkflowAgentRegistry registry,
      com.ghatana.ai.llm.LLMGateway llmGateway,
      MetricsCollector metricsCollector) {
    logger.info("Creating DefaultWorkflowAgentService");
    return new com.ghatana.agent.workflow.DefaultWorkflowAgentService(
        registry, llmGateway, metricsCollector);
  }

  @Provides
  com.ghatana.yappc.api.controller.WorkflowAgentController workflowAgentController(
      com.ghatana.agent.workflow.WorkflowAgentService agentService,
      com.ghatana.agent.workflow.WorkflowAgentRegistry agentRegistry) {
    logger.info("Creating WorkflowAgentController");
    return new com.ghatana.yappc.api.controller.WorkflowAgentController(
        agentService, agentRegistry);
  }

  @Provides
  com.ghatana.yappc.api.service.WorkflowAgentInitializer workflowAgentInitializer(
      com.ghatana.agent.workflow.WorkflowAgentRegistry agentRegistry,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating WorkflowAgentInitializer");
    com.ghatana.yappc.api.service.WorkflowAgentInitializer initializer =
        new com.ghatana.yappc.api.service.WorkflowAgentInitializer(agentRegistry, llmGateway);

    initializer
        .initialize()
        .whenComplete(
            (count, error) -> {
              if (error != null) {
                logger.error("Failed to initialize workflow agents", error);
              } else {
                logger.info("Successfully registered {} workflow agents", count);
              }
            });

    return initializer;
  }

  // ========== Template Engine & Materializer ==========

  @Provides
  com.ghatana.core.template.YamlTemplateEngine yamlTemplateEngine() {
    logger.info("Creating YamlTemplateEngine (YAPPC-Ph3)");
    return new com.ghatana.core.template.YamlTemplateEngine();
  }

  @Provides
  com.ghatana.yappc.api.workflow.WorkflowMaterializer workflowMaterializer(
      DurableWorkflowEngine engine, com.ghatana.core.template.YamlTemplateEngine templateEngine) {
    logger.info("Creating WorkflowMaterializer — materializing canonical workflow templates");
    com.ghatana.yappc.api.workflow.WorkflowMaterializer materializer =
        new com.ghatana.yappc.api.workflow.WorkflowMaterializer(engine, templateEngine);
    int loaded = materializer.materializeAll();
    logger.info("WorkflowMaterializer ready — {} template(s) loaded", loaded);
    return materializer;
  }

  @Provides
  com.ghatana.yappc.api.workflow.WorkflowExecutionController workflowExecutionController(
      com.ghatana.yappc.api.workflow.WorkflowMaterializer workflowMaterializer) {
    logger.info("Creating WorkflowExecutionController");
    return new com.ghatana.yappc.api.workflow.WorkflowExecutionController(workflowMaterializer);
  }

  // ========== DLQ ==========

  @Provides
  com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher dlqPublisher(
      javax.sql.DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcDlqPublisher — DLQ persistence via PostgreSQL");
    return new com.ghatana.yappc.api.dlq.JdbcDlqPublisher(dataSource, objectMapper);
  }

  @Provides
  com.ghatana.yappc.api.dlq.JdbcDlqRepository jdbcDlqRepository(
      javax.sql.DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcDlqRepository");
    return new com.ghatana.yappc.api.dlq.JdbcDlqRepository(dataSource, objectMapper);
  }

  @Provides
  com.ghatana.yappc.api.dlq.DlqController dlqController(
      com.ghatana.yappc.api.dlq.JdbcDlqRepository dlqRepository,
      com.ghatana.yappc.api.workflow.WorkflowMaterializer workflowMaterializer) {
    logger.info("Creating DlqController");
    return new com.ghatana.yappc.api.dlq.DlqController(dlqRepository, workflowMaterializer);
  }
}
