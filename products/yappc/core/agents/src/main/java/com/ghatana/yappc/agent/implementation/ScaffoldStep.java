package com.ghatana.yappc.agent.implementation;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation Phase - Step 2: Scaffold Implementation Units.
 *
 * <p>
 * Generates boilerplate code and project structure for planned implementation
 * units. Creates
 * directory structures, configuration files, build files, and baseline code
 * templates following
 * Ghatana patterns (Hexagonal architecture, ActiveJ, Data-Cloud integration).
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 * <li>Load planned implementation units from previous step
 * <li>Generate directory structure (src/main/java, src/test/java, etc.)
 * <li>Create build files (build.gradle.kts, pom.xml)
 * <li>Generate boilerplate classes (Service, Repository, Controller)
 * <li>Create configuration files (application.yml, logging config)
 * <li>Update unit status to SCAFFOLDED
 * <li>Persist scaffold metadata to Data-Cloud
 * <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Scaffolds implementation units - generates boilerplate code and
 *              structure
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ScaffoldStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldStep.class);

  private static final String COLLECTION_IMPL_UNITS = "implementation_units";
  private static final String COLLECTION_SCAFFOLDS = "implementation_scaffolds";
  private static final String EVENT_TOPIC = "implementation.workflow";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ScaffoldStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "implementation.scaffold";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadPlannedUnits(context))
        .then(units -> generateScaffolds(units, tenantId, runId))
        .then(scaffolds -> persistScaffolds(scaffolds, tenantId, runId))
        .then(scaffolds -> updateUnitStatus(scaffolds))
        .then(scaffolds -> publishEvents(scaffolds, tenantId, runId))
        .then(scaffolds -> buildOutputContext(context, scaffolds, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("units") && !context.containsKey("runId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: units or runId from previous step"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  @SuppressWarnings("unchecked")
  private Promise<List<Map<String, Object>>> loadPlannedUnits(WorkflowContext context) {
    // If units are in context from previous step, use them
    if (context.containsKey("units")) {
      return Promise.of((List<Map<String, Object>>) context.get("units"));
    }

    // Otherwise load from database
    String previousRunId = (String) context.get("runId");
    String tenantId = (String) context.get("tenantId");

    Map<String, Object> query = Map.of(
        "runId", previousRunId,
        "tenantId", tenantId,
        "status", "PLANNED");

    return dbClient.query(COLLECTION_IMPL_UNITS, query, 100);
  }

  private Promise<List<Map<String, Object>>> generateScaffolds(
      List<Map<String, Object>> units, String tenantId, String runId) {

    List<Map<String, Object>> scaffolds = new ArrayList<>();

    for (Map<String, Object> unit : units) {
      String unitId = (String) unit.get("unitId");
      String type = (String) unit.get("type");
      String name = (String) unit.get("name");
      String repo = (String) unit.get("repo");
      String module = (String) unit.get("module");

      Map<String, Object> scaffold = new LinkedHashMap<>();
      scaffold.put("scaffoldId", UUID.randomUUID().toString());
      scaffold.put("unitId", unitId);
      scaffold.put("tenantId", tenantId);
      scaffold.put("runId", runId);
      scaffold.put("type", type);
      scaffold.put("name", name);
      scaffold.put("repo", repo);
      scaffold.put("module", module);

      // Generate directory structure
      List<String> directories = generateDirectoryStructure(type, module);
      scaffold.put("directories", directories);

      // Generate build files
      Map<String, String> buildFiles = generateBuildFiles(name, module);
      scaffold.put("buildFiles", buildFiles);

      // Generate source files
      Map<String, String> sourceFiles = generateSourceFiles(type, name, module);
      scaffold.put("sourceFiles", sourceFiles);

      // Generate config files
      Map<String, String> configFiles = generateConfigFiles(name);
      scaffold.put("configFiles", configFiles);

      scaffold.put("createdAt", Instant.now().toString());

      scaffolds.add(scaffold);
    }

    return Promise.of(scaffolds);
  }

  private List<String> generateDirectoryStructure(String type, String module) {
    List<String> dirs = new ArrayList<>();
    String basePath = module.replace("products/", "");

    if (type.equals("CONTAINER")) {
      dirs.add(basePath + "/src/main/java");
      dirs.add(basePath + "/src/main/resources");
      dirs.add(basePath + "/src/test/java");
      dirs.add(basePath + "/src/test/resources");
      dirs.add(basePath + "/config");
      dirs.add(basePath + "/docs");
    } else if (type.equals("COMPONENT")) {
      dirs.add(basePath + "/domain");
      dirs.add(basePath + "/application");
      dirs.add(basePath + "/infrastructure");
      dirs.add(basePath + "/api");
    }

    return dirs;
  }

  private Map<String, String> generateBuildFiles(String name, String module) {
    Map<String, String> files = new LinkedHashMap<>();

    String buildGradle = generateGradleBuildFile(name);
    files.put("build.gradle.kts", buildGradle);

    String settingsGradle = "rootProject.name = \"" + name.toLowerCase() + "\"";
    files.put("settings.gradle.kts", settingsGradle);

    return files;
  }

  private String generateGradleBuildFile(String name) {
    return """
        plugins {
            java
            id("io.activej.gradle") version "0.1.0"
        }

        group = "com.ghatana"
        version = "1.0.0-SNAPSHOT"

        repositories {
            mavenCentral()
        }

        dependencies {
            implementation("io.activej:activej-promise:6.0")
            implementation(project(":libs:workflow-api"))
            implementation(project(":libs:database"))
            implementation(project(":libs:event-cloud"))

            testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
            testImplementation(project(":libs:activej-test-utils"))
        }

        tasks.test {
            useJUnitPlatform()
        }
        """;
  }

  private Map<String, String> generateSourceFiles(String type, String name, String module) {
    Map<String, String> files = new LinkedHashMap<>();

    String packageName = module.replace("products/", "com.ghatana.").replace("/", ".").replace("-", "");

    // Generate Service class
    String serviceClass = generateServiceClass(packageName, name);
    files.put(packageName.replace(".", "/") + "/" + name + "Service.java", serviceClass);

    // Generate Repository interface
    String repoInterface = generateRepositoryInterface(packageName, name);
    files.put(packageName.replace(".", "/") + "/" + name + "Repository.java", repoInterface);

    return files;
  }

  private String generateServiceClass(String packageName, String name) {
    return """
        package %s;

        import io.activej.promise.Promise;
        import java.util.Objects;

        /**
         * Service for %s.
         *
         * @doc.type class
         * @doc.purpose %s domain service
         * @doc.layer product
         * @doc.pattern Service
         */
        public final class %sService {

            private final %sRepository repository;

            public %sService(%sRepository repository) {
                this.repository = Objects.requireNonNull(repository, "repository must not be null");
            }

            /**
             * Executes the service operation with proper error handling.
             *
             * @return Promise that completes when execution is done
             */
            public Promise<Void> execute() {
                return repository.findById("test-id")
                    .then(entity -> {
                        if (entity == null) {
                            return Promise.ofException(
                                new IllegalStateException("Entity not found")
                            );
                        }
                        // Process entity
                        return repository.save(entity);
                    })
                    .then($ -> Promise.complete())
                    .whenComplete((v, e) -> {
                        if (e != null) {
                            log.error("Error executing service: {}", e.getMessage());
                        }
                    });
            }
        }
        """
        .formatted(packageName, name, name, name, name, name, name);
  }

  private String generateRepositoryInterface(String packageName, String name) {
    return """
        package %s;

        import io.activej.promise.Promise;
        import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

        /**
         * Repository for %s.
         *
         * @doc.type interface
         * @doc.purpose %s data access
         * @doc.layer product
         * @doc.pattern Repository
         */
        public interface %sRepository {
            Promise<Map<String, Object>> findById(String id);
            Promise<Void> save(Map<String, Object> entity);
        }
        """
        .formatted(packageName, name, name, name);
  }

  private Map<String, String> generateConfigFiles(String name) {
    Map<String, String> files = new LinkedHashMap<>();

    String appConfig = """
        server:
          port: 8080

        logging:
          level:
            com.ghatana: DEBUG
        """;
    files.put("application.yml", appConfig);

    return files;
  }

  private Promise<List<Map<String, Object>>> persistScaffolds(
      List<Map<String, Object>> scaffolds, String tenantId, String runId) {

    List<Promise<Void>> persistPromises = scaffolds.stream()
        .map(scaffold -> dbClient.insert(COLLECTION_SCAFFOLDS, scaffold))
        .collect(Collectors.toList());

    return io.activej.promise.Promises.all(persistPromises).map($ -> scaffolds);
  }

  private Promise<List<Map<String, Object>>> updateUnitStatus(List<Map<String, Object>> scaffolds) {
    List<Promise<Void>> updatePromises = scaffolds.stream()
        .map(
            scaffold -> {
              String unitId = (String) scaffold.get("unitId");
              Map<String, Object> update = Map.of("status", "SCAFFOLDED");
              return dbClient.update(COLLECTION_IMPL_UNITS, Map.of("unitId", unitId), update);
            })
        .collect(Collectors.toList());

    return io.activej.promise.Promises.all(updatePromises).map($ -> scaffolds);
  }

  private Promise<List<Map<String, Object>>> publishEvents(
      List<Map<String, Object>> scaffolds, String tenantId, String runId) {

    Map<String, Object> eventPayload = Map.of(
        "runId",
        runId,
        "eventType",
        "SCAFFOLD_COMPLETED",
        "scaffoldCount",
        scaffolds.size(),
        "scaffolds",
        scaffolds.stream()
            .map(
                s -> Map.of(
                    "scaffoldId", s.get("scaffoldId"),
                    "unitId", s.get("unitId"),
                    "name", s.get("name"),
                    "fileCount",
                    ((Map<?, ?>) s.get("sourceFiles")).size()
                        + ((Map<?, ?>) s.get("buildFiles")).size()))
            .collect(Collectors.toList()));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> scaffolds);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, List<Map<String, Object>> scaffolds, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "Scaffold");
    output.put("scaffoldCount", scaffolds.size());
    output.put("scaffolds", scaffolds);
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String runId, Instant startTime) {

    Map<String, Object> errorPayload = Map.of(
        "runId",
        runId,
        "eventType",
        "SCAFFOLD_FAILED",
        "error",
        ex.getMessage(),
        "timestamp",
        Instant.now().toString());

    return eventClient
        .publish(EVENT_TOPIC, tenantId, errorPayload)
        .then(
            $ -> {
              WorkflowContext errorContext = context.copy();
              errorContext.put("status", "FAILED");
              errorContext.put("error", ex.getMessage());
              errorContext.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
              return Promise.of(errorContext);
            });
  }
}
