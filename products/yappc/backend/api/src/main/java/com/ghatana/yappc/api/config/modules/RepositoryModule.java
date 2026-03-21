/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.entity.version.VersionRecord;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import com.ghatana.yappc.api.events.EventPublisher;
import com.ghatana.yappc.api.events.EventRepository;
import com.ghatana.yappc.api.repository.AISuggestionRepository;
import com.ghatana.yappc.api.repository.AgentRegistryRepository;
import com.ghatana.yappc.api.repository.AlertRepository;
import com.ghatana.yappc.api.repository.BootstrappingSessionRepository;
import com.ghatana.yappc.api.repository.ChannelRepository;
import com.ghatana.yappc.api.repository.CodeReviewRepository;
import com.ghatana.yappc.api.repository.ComplianceRepository;
import com.ghatana.yappc.api.repository.IncidentRepository;
import com.ghatana.yappc.api.repository.LogEntryRepository;
import com.ghatana.yappc.api.repository.MetricRepository;
import com.ghatana.yappc.api.repository.NotificationRepository;
import com.ghatana.yappc.api.repository.ProjectRepository;
import com.ghatana.yappc.api.repository.RequirementRepository;
import com.ghatana.yappc.api.repository.SecurityScanRepository;
import com.ghatana.yappc.api.repository.SprintRepository;
import com.ghatana.yappc.api.repository.StoryRepository;
import com.ghatana.yappc.api.repository.TeamRepository;
import com.ghatana.yappc.api.repository.TraceRepository;
import com.ghatana.yappc.api.repository.VulnerabilityRepository;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcAgentRegistryRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcAlertRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcBootstrappingSessionRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcCodeReviewRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcComplianceRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcEventRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcIncidentRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcLogEntryRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcMetricRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcProjectRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcRequirementRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcSecurityScanRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcSprintRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcStoryRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcTraceRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcVersionRecord;
import com.ghatana.yappc.api.repository.jdbc.JdbcVulnerabilityRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcWorkflowStateStore;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for all JDBC repository bindings.
 *
 * <p>Centralises the repository → JDBC-implementation wiring for the entire YAPPC API.
 *
 * @doc.type class
 * @doc.purpose Repository DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection, Repository
 */
public class RepositoryModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(RepositoryModule.class);

  // ========== Core Repositories ==========

  @Provides
  RequirementRepository requirementRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based RequirementRepository");
    return new JdbcRequirementRepository(dataSource, objectMapper);
  }

  @Provides
  AISuggestionRepository aiSuggestionRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based AISuggestionRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcAISuggestionRepository(
        dataSource, objectMapper);
  }

  @Provides
  WorkspaceRepository workspaceRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based WorkspaceRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcWorkspaceRepository(
        dataSource, objectMapper);
  }

  @Provides
  BootstrappingSessionRepository bootstrappingSessionRepository(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based BootstrappingSessionRepository");
    return new JdbcBootstrappingSessionRepository(dataSource, objectMapper);
  }

  @Provides
  ProjectRepository projectRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based ProjectRepository");
    return new JdbcProjectRepository(dataSource);
  }

  @Provides
  SprintRepository sprintRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based SprintRepository");
    return new JdbcSprintRepository(dataSource, objectMapper);
  }

  @Provides
  StoryRepository storyRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based StoryRepository");
    return new JdbcStoryRepository(dataSource, objectMapper);
  }

  // ========== Collaboration Repositories ==========

  @Provides
  TeamRepository teamRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC TeamRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcTeamRepository(dataSource, objectMapper);
  }

  @Provides
  CodeReviewRepository codeReviewRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC CodeReviewRepository");
    return new JdbcCodeReviewRepository(dataSource, objectMapper);
  }

  @Provides
  NotificationRepository notificationRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC NotificationRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcNotificationRepository(
        dataSource, objectMapper);
  }

  @Provides
  ChannelRepository channelRepository(DataSource dataSource) {
    logger.info("Creating JDBC ChannelRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcChannelRepository(dataSource);
  }

  // ========== Operations Repositories ==========

  @Provides
  MetricRepository metricRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC MetricRepository");
    return new JdbcMetricRepository(dataSource, objectMapper);
  }

  @Provides
  AlertRepository alertRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC AlertRepository");
    return new JdbcAlertRepository(dataSource);
  }

  @Provides
  IncidentRepository incidentRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC IncidentRepository");
    return new JdbcIncidentRepository(dataSource);
  }

  @Provides
  LogEntryRepository logEntryRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC LogEntryRepository");
    return new JdbcLogEntryRepository(dataSource, objectMapper);
  }

  @Provides
  TraceRepository traceRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC TraceRepository");
    return new JdbcTraceRepository(dataSource, objectMapper);
  }

  // ========== Security Repositories ==========

  @Provides
  VulnerabilityRepository vulnerabilityRepository(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC VulnerabilityRepository");
    return new JdbcVulnerabilityRepository(dataSource, objectMapper);
  }

  @Provides
  SecurityScanRepository securityScanRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC SecurityScanRepository");
    return new JdbcSecurityScanRepository(dataSource);
  }

  @Provides
  ComplianceRepository complianceRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC ComplianceRepository");
    return new JdbcComplianceRepository(dataSource);
  }

  // ========== Agent Registry Repository ==========

  @Provides
  AgentRegistryRepository agentRegistryRepository(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcAgentRegistryRepository");
    return new JdbcAgentRegistryRepository(dataSource, objectMapper);
  }

  // ========== Event Store ==========

  @Provides
  EventRepository eventRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcEventRepository");
    return new JdbcEventRepository(dataSource, objectMapper);
  }

  @Provides
  EventPublisher eventPublisher(EventRepository eventRepository) {
    logger.info("Creating EventPublisher");
    return new EventPublisher(eventRepository);
  }

  // ========== Version Record ==========

  @Provides
  JdbcVersionRecord jdbcVersionRecord(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcVersionRecord — entity version history persisted to PostgreSQL");
    return new JdbcVersionRecord(dataSource, objectMapper);
  }

  @Provides
  VersionRecord versionRecord(JdbcVersionRecord store) {
    return store;
  }

  // ========== Workflow State Store ==========

  @Provides
  JdbcWorkflowStateStore jdbcWorkflowStateStore(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcWorkflowStateStore — workflow runs persisted to PostgreSQL");
    return new JdbcWorkflowStateStore(dataSource, objectMapper);
  }

  @Provides
  DurableWorkflowEngine.WorkflowStateStore workflowStateStore(JdbcWorkflowStateStore store) {
    return store;
  }

  // ========== Learned Policy Repository ==========

  /**
   * @doc.layer infrastructure
   * @doc.pattern Repository
   * @doc.gaa.memory procedural
   */
  @Provides
  com.ghatana.yappc.api.repository.LearnedPolicyRepository learnedPolicyRepository(
      javax.sql.DataSource dataSource) {
    return new com.ghatana.yappc.api.repository.jdbc.JdbcLearnedPolicyRepository(dataSource);
  }
}
