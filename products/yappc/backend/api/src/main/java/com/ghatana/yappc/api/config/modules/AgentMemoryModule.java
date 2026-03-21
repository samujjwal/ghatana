/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for agent memory plane, governance, and history.
 *
 * <p>Provides persistent memory plane (JDBC-backed), memory redaction filter, security manager,
 * history controller, feedback learning, and learned policy controller.
 *
 * @doc.type class
 * @doc.purpose Agent memory and GAA DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 * @doc.gaa.memory episodic|semantic|procedural|preference
 */
public class AgentMemoryModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(AgentMemoryModule.class);

  // ========== Memory Store ==========

  /**
   * @doc.gaa.memory episodic, semantic, procedural
   */
  @Provides
  com.ghatana.yappc.api.repository.jdbc.JdbcMemoryStore jdbcMemoryStore(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcMemoryStore — agent memory persisted to PostgreSQL");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcMemoryStore(dataSource, objectMapper);
  }

  @Provides
  com.ghatana.agent.framework.memory.MemoryStore memoryStore(
      com.ghatana.yappc.api.repository.jdbc.JdbcMemoryStore impl) {
    return impl;
  }

  // ========== Persistent Memory Plane ==========

  @Provides
  com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository jdbcMemoryItemRepository(
      DataSource dataSource) {
    logger.info("Creating JdbcMemoryItemRepository");
    return new com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository(dataSource);
  }

  @Provides
  com.ghatana.agent.memory.store.taskstate.TaskStateStore taskStateStore(DataSource dataSource) {
    logger.info("Creating JdbcTaskStateStore");
    com.ghatana.agent.memory.persistence.JdbcTaskStateRepository repo =
        new com.ghatana.agent.memory.persistence.JdbcTaskStateRepository(dataSource);
    return new com.ghatana.agent.memory.store.taskstate.JdbcTaskStateStore(repo);
  }

  /**
   * @doc.gaa.memory episodic|semantic|procedural|preference
   */
  @Provides
  com.ghatana.agent.memory.persistence.PersistentMemoryPlane persistentMemoryPlane(
      com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository memoryItemRepository,
      com.ghatana.agent.memory.store.taskstate.TaskStateStore taskStateStore) {
    logger.info("Creating PersistentMemoryPlane (JDBC-backed, no EventCloud)");
    com.ghatana.agent.memory.model.working.WorkingMemoryConfig workingMemoryConfig =
        com.ghatana.agent.memory.model.working.WorkingMemoryConfig.builder()
            .maxEntries(5000)
            .build();
    return new com.ghatana.agent.memory.persistence.PersistentMemoryPlane(
        memoryItemRepository, taskStateStore, workingMemoryConfig);
  }

  // ========== Memory Governance ==========

  @Provides
  com.ghatana.agent.memory.security.MemoryRedactionFilter memoryRedactionFilter() {
    String configDir = System.getProperty("yappc.config.dir");
    if (configDir != null && !configDir.isBlank()) {
      java.nio.file.Path rulesPath =
          java.nio.file.Paths.get(configDir, "memory/redaction-rules.yaml");
      if (java.nio.file.Files.exists(rulesPath)) {
        try {
          com.ghatana.agent.memory.security.YamlRedactionPatternProvider provider =
              com.ghatana.agent.memory.security.YamlRedactionPatternProvider.fromPath(rulesPath);
          logger.info("MemoryRedactionFilter: loaded YAML rules from '{}'", rulesPath);
          return new com.ghatana.agent.memory.security.MemoryRedactionFilter(true, true, provider);
        } catch (java.io.IOException e) {
          logger.warn(
              "MemoryRedactionFilter: failed to load '{}', using defaults: {}",
              rulesPath,
              e.getMessage());
        }
      }
    }
    logger.info("MemoryRedactionFilter: using built-in default patterns");
    return com.ghatana.agent.memory.security.MemoryRedactionFilter.defaultFilter();
  }

  @Provides
  com.ghatana.agent.memory.security.MemorySecurityManager memorySecurityManager() {
    logger.info("Creating TenantIsolatedMemorySecurityManager");
    return new com.ghatana.yappc.api.memory.TenantIsolatedMemorySecurityManager();
  }

  // ========== Feedback Learning ==========

  /**
   * @doc.gaa.lifecycle reflect
   */
  @Provides
  com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService feedbackLearningService(
      MetricsCollector metricsCollector) {
    logger.info(
        "Creating FeedbackLearningService with MetricsCollector: {}",
        metricsCollector.getClass().getSimpleName());
    return new com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService(
        metricsCollector);
  }

  // ========== History & Policy Controllers ==========

  /**
   * @doc.gaa.lifecycle capture
   */
  @Provides
  com.ghatana.yappc.api.history.AgentHistoryController agentHistoryController(
      com.ghatana.agent.memory.persistence.PersistentMemoryPlane persistentMemoryPlane) {
    logger.info("Creating AgentHistoryController");
    return new com.ghatana.yappc.api.history.AgentHistoryController(persistentMemoryPlane);
  }

  /**
   * @doc.gaa.lifecycle reflect
   */
  @Provides
  com.ghatana.yappc.api.policy.LearnedPolicyController learnedPolicyController(
      com.ghatana.yappc.api.repository.LearnedPolicyRepository learnedPolicyRepository,
      com.ghatana.yappc.api.common.TenantContextExtractor tenantContextExtractor) {
    logger.info("Creating LearnedPolicyController");
    return new com.ghatana.yappc.api.policy.LearnedPolicyController(
        learnedPolicyRepository, tenantContextExtractor);
  }
}
