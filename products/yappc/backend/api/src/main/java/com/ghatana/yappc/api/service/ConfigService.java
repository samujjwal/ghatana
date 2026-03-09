/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.yappc.api.domain.AgentCapabilities;
import com.ghatana.yappc.api.domain.LifecycleConfig;
import com.ghatana.yappc.api.domain.Persona;
import com.ghatana.yappc.api.domain.TaskDomain;
import com.ghatana.yappc.api.domain.Workflow;
import io.activej.promise.Promise;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Provide configuration data with caching and transformation
 * @doc.layer platform
 * @doc.pattern Service
 */
public class ConfigService {
  private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
  private final ConfigLoader configLoader;

  public ConfigService(ConfigLoader configLoader) {
    this.configLoader = configLoader;
  }

  // Domain Operations
  public Promise<List<TaskDomain>> getDomains() {
    List<TaskDomain> domains = configLoader.getCachedList("domains");
    if (domains == null) {
      return configLoader.loadDomains();
    }
    return Promise.of(domains);
  }

  public Promise<TaskDomain> getDomainById(String domainId) {
    return getDomains()
        .then(
            domains -> {
              TaskDomain domain =
                  domains.stream().filter(d -> d.id().equals(domainId)).findFirst().orElse(null);

              if (domain == null) {
                return Promise.ofException(new RuntimeException("Domain not found: " + domainId));
              }
              return Promise.of(domain);
            });
  }

  // Workflow Operations
  public Promise<List<Workflow>> getWorkflows() {
    List<Workflow> workflows = configLoader.getCachedList("workflows");
    if (workflows == null) {
      return configLoader.loadWorkflows();
    }
    return Promise.of(workflows);
  }

  public Promise<Workflow> getWorkflowById(String workflowId) {
    return getWorkflows()
        .then(
            workflows -> {
              Workflow workflow =
                  workflows.stream()
                      .filter(w -> w.id().equals(workflowId))
                      .findFirst()
                      .orElse(null);

              if (workflow == null) {
                return Promise.ofException(
                    new RuntimeException("Workflow not found: " + workflowId));
              }
              return Promise.of(workflow);
            });
  }

  // Lifecycle Config
  public Promise<LifecycleConfig> getLifecycleConfig() {
    LifecycleConfig config = configLoader.getCached("lifecycle", LifecycleConfig.class);
    if (config == null) {
      return configLoader.loadLifecycleConfig();
    }
    return Promise.of(config);
  }

  // Agent Capabilities
  public Promise<AgentCapabilities> getAgentCapabilities() {
    AgentCapabilities caps = configLoader.getCached("agents", AgentCapabilities.class);
    if (caps == null) {
      return configLoader.loadAgentCapabilities();
    }
    return Promise.of(caps);
  }

  // Persona Operations
  public Promise<List<Persona>> getPersonas() {
    List<Persona> personas = configLoader.getCachedList("personas");
    if (personas == null) {
      return configLoader.loadPersonas();
    }
    return Promise.of(personas);
  }

  public Promise<Persona> getPersonaById(String personaId) {
    return getPersonas()
        .then(
            personas -> {
              Persona persona =
                  personas.stream().filter(p -> p.id().equals(personaId)).findFirst().orElse(null);

              if (persona == null) {
                return Promise.ofException(new RuntimeException("Persona not found: " + personaId));
              }
              return Promise.of(persona);
            });
  }

  // Utility methods
  public Promise<TaskDomain.Task> getTaskById(String domainId, String taskId) {
    return getDomainById(domainId)
        .then(
            domain -> {
              if (domain.tasks() == null) {
                return Promise.ofException(new RuntimeException("No tasks in domain: " + domainId));
              }

              TaskDomain.Task task =
                  domain.tasks().stream()
                      .filter(t -> t.id().equals(taskId))
                      .findFirst()
                      .orElse(null);

              if (task == null) {
                return Promise.ofException(new RuntimeException("Task not found: " + taskId));
              }
              return Promise.of(task);
            });
  }

  public Promise<List<TaskDomain.Task>> getAllTasks() {
    return getDomains()
        .then(
            domains -> {
              List<TaskDomain.Task> allTasks =
                  domains.stream()
                      .filter(domain -> domain.tasks() != null)
                      .flatMap(domain -> domain.tasks().stream())
                      .toList();
              return Promise.of(allTasks);
            });
  }
}
