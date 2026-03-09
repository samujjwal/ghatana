/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.yappc.api.domain.AgentCapabilities;
import com.ghatana.yappc.api.domain.LifecycleConfig;
import com.ghatana.yappc.api.domain.Persona;
import com.ghatana.yappc.api.domain.TaskDomain;
import com.ghatana.yappc.api.domain.Workflow;
import io.activej.promise.Promise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @doc.type class
 * @doc.purpose Load and parse configuration files from /config directory
 * @doc.layer platform
 * @doc.pattern Loader
 */
public class ConfigLoader {
  private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
  private final Path configBasePath;
  private final ObjectMapper yamlMapper;
  private final Executor executor;

  // Configuration cache - loaded once at startup
  private final Map<String, Object> configCache = new ConcurrentHashMap<>();

  public ConfigLoader(Path configBasePath) {
    this.configBasePath = configBasePath;
    this.yamlMapper =
        new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    this.executor = Executors.newFixedThreadPool(4);
  }

  // Core Loading Methods
  public Promise<List<TaskDomain>> loadDomains() {
    return Promise.ofBlocking(
        executor,
        () -> {
          List<TaskDomain> domains = new ArrayList<>();
          Path domainsPath = configBasePath.resolve("domains.yaml");

          try {
            Map<String, Object> yamlData =
                yamlMapper.readValue(Files.newInputStream(domainsPath), Map.class);

            List<Map<String, Object>> domainList =
                (List<Map<String, Object>>) yamlData.get("domains");

            if (domainList != null) {
              for (Map<String, Object> d : domainList) {
                TaskDomain domain = yamlMapper.convertValue(d, TaskDomain.class);
                domains.add(domain);
              }
            }

            // Sort by order field if available, otherwise keep order
            // domains.sort(Comparator.comparingInt(TaskDomain::order));
            configCache.put("domains", domains);

            logger.info("Loaded {} task domains", domains.size());
            return domains;
          } catch (Exception e) {
            logger.warn("Failed to load domains.yaml", e);
            return Collections.emptyList();
          }
        });
  }

  public Promise<List<Workflow>> loadWorkflows() {
    return Promise.ofBlocking(
        executor,
        () -> {
          Path workflowPath = configBasePath.resolve("workflows.yaml");

          try {
            Map<String, Object> yamlData =
                yamlMapper.readValue(Files.newInputStream(workflowPath), Map.class);

            List<Map<String, Object>> workflowList =
                (List<Map<String, Object>>) yamlData.get("workflows");
            List<Workflow> workflows = new ArrayList<>();

            if (workflowList != null) {
              for (Map<String, Object> wf : workflowList) {
                Workflow workflow = yamlMapper.convertValue(wf, Workflow.class);
                workflows.add(workflow);
              }
            }

            configCache.put("workflows", workflows);

            logger.info("Loaded {} workflows", workflows.size());
            return workflows;
          } catch (IOException e) {
            logger.error("Failed to load workflows", e);
            return Collections.emptyList();
          }
        });
  }

  public Promise<LifecycleConfig> loadLifecycleConfig() {
    return Promise.ofBlocking(
        executor,
        () -> {
          Path lifecyclePath = configBasePath.resolve("lifecycle.yaml");

          try {
            LifecycleConfig config =
                yamlMapper.readValue(Files.newInputStream(lifecyclePath), LifecycleConfig.class);

            configCache.put("lifecycle", config);
            logger.info("Loaded lifecycle configuration");
            return config;
          } catch (IOException e) {
            logger.error("Failed to load lifecycle config", e);
            // Return default config
            return new LifecycleConfig(Collections.emptyList(), Collections.emptyList());
          }
        });
  }

  public Promise<AgentCapabilities> loadAgentCapabilities() {
    return Promise.ofBlocking(
        executor,
        () -> {
          Path agentPath = configBasePath.resolve("agents.yaml");

          try {
            AgentCapabilities caps =
                yamlMapper.readValue(Files.newInputStream(agentPath), AgentCapabilities.class);

            configCache.put("agents", caps);
            logger.info("Loaded agent capabilities");
            return caps;
          } catch (IOException e) {
            logger.error("Failed to load agent capabilities", e);
            // Return default capabilities
            return new AgentCapabilities(Collections.emptyList(), Collections.emptyMap());
          }
        });
  }

  public Promise<List<Persona>> loadPersonas() {
    return Promise.ofBlocking(
        executor,
        () -> {
          Path personasPath = configBasePath.resolve("personas.yaml");

          try {
            Map<String, Object> yamlData =
                yamlMapper.readValue(Files.newInputStream(personasPath), Map.class);

            List<Map<String, Object>> personaList =
                (List<Map<String, Object>>) yamlData.get("personas");
            List<Persona> personas = new ArrayList<>();

            if (personaList != null) {
              for (Map<String, Object> p : personaList) {
                Persona persona = yamlMapper.convertValue(p, Persona.class);
                personas.add(persona);
              }
            }

            configCache.put("personas", personas);

            logger.info("Loaded {} personas", personas.size());
            return personas;
          } catch (IOException e) {
            logger.error("Failed to load personas", e);
            return Collections.emptyList();
          }
        });
  }

  // Cache access
  @SuppressWarnings("unchecked")
  public <T> T getCached(String key, Class<T> type) {
    return (T) configCache.get(key);
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getCachedList(String key) {
    return (List<T>) configCache.get(key);
  }

  // Load all configs (called at startup)
  public Promise<Void> loadAllConfigs() {
    return loadDomains()
        .then(domains -> loadWorkflows())
        .then(workflows -> loadLifecycleConfig())
        .then(lifecycle -> loadAgentCapabilities())
        .then(agents -> loadPersonas())
        .then(
            personas -> {
              logger.info("All configurations loaded successfully");
              return Promise.of((Void) null);
            });
  }
}
