package com.ghatana.yappc.sdlc.agent.generators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.prompts.AgentPromptTemplate;
import com.ghatana.yappc.sdlc.agent.prompts.YAPPCPromptTemplates;
import com.ghatana.yappc.sdlc.agent.specialists.*;
import java.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating LLM-powered OutputGenerators wired to YAPPC agent prompts.
 *
 * <p>This factory:
 *
 * <ul>
 *   <li>Retrieves prompts from {@link YAPPCPromptTemplates}
 *   <li>Configures context builders specific to each agent input type
 *   <li>Provides JSON parsers for each agent output type
 *   <li>Wires everything together into ready-to-use generators
 * </ul>
 *
 * <p><strong>Usage</strong>:
 *
 * <pre>{@code
 * LLMGenerator.LLMGateway gateway = createGateway();
 *
 * // Create intake generator
 * OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>> intakeGen =
 *     LLMGeneratorFactory.createIntakeGenerator(gateway);
 *
 * // Create design generator
 * OutputGenerator<StepRequest<DesignInput>, StepResult<DesignOutput>> designGen =
 *     LLMGeneratorFactory.createDesignGenerator(gateway);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for LLM-powered agent generators
 * @doc.layer product
 * @doc.pattern Factory
 */
public class LLMGeneratorFactory {

  private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

  /**
   * Creates an LLM-powered IntakeSpecialistAgent generator.
   *
   * <p>Uses the "architecture.intake" prompt template to extract structured requirements.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for IntakeSpecialistAgent
   */
  public static OutputGenerator<StepRequest<IntakeInput>, StepResult<IntakeOutput>>
      createIntakeGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("architecture.intake");

    return LLMPoweredGenerator.<IntakeInput, IntakeOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "requirements", input.requirements(),
                    "source", input.source(),
                    "constraints", "Check requirements for constraints"))
        .responseParser(LLMGeneratorFactory::parseIntakeOutput)
        .name("LLM-IntakeGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered DesignSpecialistAgent generator.
   *
   * <p>Uses the "architecture.design" prompt template to create architecture designs.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for DesignSpecialistAgent
   */
  public static OutputGenerator<StepRequest<DesignInput>, StepResult<DesignOutput>>
      createDesignGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("architecture.design");

    return LLMPoweredGenerator.<DesignInput, DesignOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "requirements",
                    "Requirements ID: " + input.requirementsId(),
                    "constraints",
                    input.constraints(),
                    "designPrinciples",
                    "SOLID, DRY, KISS"))
        .responseParser(LLMGeneratorFactory::parseDesignOutput)
        .name("LLM-DesignGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered PlanUnitsSpecialistAgent generator.
   *
   * <p>Uses the "architecture.plan_units" prompt template to break down work.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for PlanUnitsSpecialistAgent
   */
  public static OutputGenerator<StepRequest<PlanUnitsInput>, StepResult<PlanUnitsOutput>>
      createPlanUnitsGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("architecture.plan_units");

    return LLMPoweredGenerator.<PlanUnitsInput, PlanUnitsOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "designDescription", "Design from scaffold: " + input.scaffoldId(),
                    "components", "Architecture: " + input.architectureId()))
        .responseParser(LLMGeneratorFactory::parsePlanUnitsOutput)
        .name("LLM-PlanUnitsGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered ScaffoldSpecialistAgent generator.
   *
   * <p>Uses the "architecture.scaffold" prompt template to generate code scaffolding.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for ScaffoldSpecialistAgent
   */
  public static OutputGenerator<StepRequest<ScaffoldInput>, StepResult<ScaffoldOutput>>
      createScaffoldGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("architecture.scaffold");

    return LLMPoweredGenerator.<ScaffoldInput, ScaffoldOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "architectureId",
                    input.architectureId(),
                    "planId",
                    input.planId(),
                    "targetLanguage",
                    "Java",
                    "framework",
                    "ActiveJ"))
        .responseParser(LLMGeneratorFactory::parseScaffoldOutput)
        .name("LLM-ScaffoldGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered ImplementSpecialistAgent generator.
   *
   * <p>Uses the "implementation.implement" prompt template to implement code units.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for ImplementSpecialistAgent
   */
  public static OutputGenerator<StepRequest<ImplementInput>, StepResult<ImplementOutput>>
      createImplementGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("implementation.implement");

    return LLMPoweredGenerator.<ImplementInput, ImplementOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "planId",
                    input.planId(),
                    "unitName",
                    input.unitName(),
                    "codingStandards",
                    "Follow SOLID principles, comprehensive error handling",
                    "testingApproach",
                    "TDD with comprehensive test coverage"))
        .responseParser(LLMGeneratorFactory::parseImplementOutput)
        .name("LLM-ImplementGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered BuildSpecialistAgent generator.
   *
   * <p>Uses the "implementation.build" prompt template to configure and execute builds.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for BuildSpecialistAgent
   */
  public static OutputGenerator<StepRequest<BuildInput>, StepResult<BuildOutput>>
      createBuildGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("implementation.build");

    return LLMPoweredGenerator.<BuildInput, BuildOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "implementationId",
                    input.implementationId(),
                    "buildTarget",
                    input.buildTarget(),
                    "buildTool",
                    "Gradle",
                    "optimizations",
                    "Enable incremental compilation, parallel execution"))
        .responseParser(LLMGeneratorFactory::parseBuildOutput)
        .name("LLM-BuildGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered ReviewSpecialistAgent generator.
   *
   * <p>Uses the "implementation.review" prompt template to conduct code reviews.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for ReviewSpecialistAgent
   */
  public static OutputGenerator<StepRequest<ReviewInput>, StepResult<ReviewOutput>>
      createReviewGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("implementation.review");

    return LLMPoweredGenerator.<ReviewInput, ReviewOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "implementationId",
                    input.implementationId(),
                    "unitName",
                    input.unitName(),
                    "reviewCriteria",
                    "Code quality, security, performance, maintainability",
                    "standards",
                    "Ghatana coding standards, GAA framework patterns"))
        .responseParser(LLMGeneratorFactory::parseReviewOutput)
        .name("LLM-ReviewGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered GenerateTestsSpecialistAgent generator.
   *
   * <p>Uses the "testing.generate_tests" prompt template to generate test suites.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for GenerateTestsSpecialistAgent
   */
  public static OutputGenerator<StepRequest<GenerateTestsInput>, StepResult<GenerateTestsOutput>>
      createGenerateTestsGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("testing.generate_tests");

    return LLMPoweredGenerator.<GenerateTestsInput, GenerateTestsOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "testPlanId", input.testPlanId(),
                    "testCases", input.testCases(),
                    "testType", input.testType(),
                    "targetLanguage", input.targetLanguage(),
                    "testFramework", input.testFramework(),
                    "requirements", "Generate comprehensive test coverage"))
        .responseParser(LLMGeneratorFactory::parseGenerateTestsOutput)
        .name("LLM-GenerateTestsGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered DeployStagingSpecialistAgent generator.
   *
   * <p>Uses the "operations.deploy_staging" prompt template to deploy to staging.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for DeployStagingSpecialistAgent
   */
  public static OutputGenerator<StepRequest<DeployStagingInput>, StepResult<DeployStagingOutput>>
      createDeployStagingGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("operations.deploy_staging");

    return LLMPoweredGenerator.<DeployStagingInput, DeployStagingOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "buildId",
                    input.buildId(),
                    "environment",
                    input.environment(),
                    "deploymentStrategy",
                    "Blue-green deployment with health checks",
                    "rollbackPlan",
                    "Automated rollback on health check failure"))
        .responseParser(LLMGeneratorFactory::parseDeployStagingOutput)
        .name("LLM-DeployStagingGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered MonitorSpecialistAgent generator.
   *
   * <p>Uses the "operations.monitor" prompt template to setup monitoring.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for MonitorSpecialistAgent
   */
  public static OutputGenerator<StepRequest<MonitorInput>, StepResult<MonitorOutput>>
      createMonitorGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("operations.monitor");

    return LLMPoweredGenerator.<MonitorInput, MonitorOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "deploymentId",
                    input.deploymentId(),
                    "durationMinutes",
                    String.valueOf(input.durationMinutes()),
                    "metrics",
                    "Error rate, latency P95/P99, throughput, CPU, memory",
                    "alertThresholds",
                    "Error rate > 5%, latency P95 > 500ms"))
        .responseParser(LLMGeneratorFactory::parseMonitorOutput)
        .name("LLM-MonitorGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered IncidentResponseSpecialistAgent generator.
   *
   * <p>Uses the "operations.incident_response" prompt template to handle incidents.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for IncidentResponseSpecialistAgent
   */
  public static OutputGenerator<
          StepRequest<IncidentResponseInput>, StepResult<IncidentResponseOutput>>
      createIncidentResponseGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("operations.incident_response");

    return LLMPoweredGenerator.<IncidentResponseInput, IncidentResponseOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "deploymentId", input.deploymentId(),
                    "severity", input.severity(),
                    "symptoms", input.symptoms(),
                    "responseProtocol",
                        "Follow incident severity protocol, escalate P0/P1 immediately",
                    "runbooks", "Check deployment runbooks for common issues"))
        .responseParser(LLMGeneratorFactory::parseIncidentResponseOutput)
        .name("LLM-IncidentResponseGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  /**
   * Creates an LLM-powered CanarySpecialistAgent generator.
   *
   * <p>Uses the "operations.canary" prompt template to execute canary deployments.
   *
   * @param llmGateway LLM gateway for sending prompts
   * @param config LLM configuration (temperature, max tokens, etc.)
   * @return OutputGenerator for CanarySpecialistAgent
   */
  public static OutputGenerator<StepRequest<CanaryInput>, StepResult<CanaryOutput>>
      createCanaryGenerator(
          @NotNull LLMGenerator.LLMGateway llmGateway, @NotNull LLMGenerator.LLMConfig config) {

    AgentPromptTemplate template = YAPPCPromptTemplates.get("operations.canary");

    return LLMPoweredGenerator.<CanaryInput, CanaryOutput>builder()
        .llmGateway(llmGateway)
        .promptTemplate(template)
        .contextBuilder(
            (input, context) ->
                Map.of(
                    "deploymentId", input.deploymentId(),
                    "trafficPercentage", String.valueOf(input.trafficPercentage()),
                    "durationMinutes", String.valueOf(input.durationMinutes()),
                    "successCriteria", "Error rate < baseline + 2%, latency P95 < baseline + 10%",
                    "rollbackStrategy", "Automatic rollback if success criteria not met"))
        .responseParser(LLMGeneratorFactory::parseCanaryOutput)
        .name("LLM-CanaryGenerator")
        .version("1.0.0")
        .llmConfig(config)
        .build();
  }

  // ============================================
  // JSON PARSERS
  // ============================================

  /**
   * Parses IntakeOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "functionalRequirements": ["req1", "req2"],
   *   "nonFunctionalRequirements": ["nfr1", "nfr2"],
   *   "constraints": {"tech": "Java", "budget": "$100k"},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static IntakeOutput parseIntakeOutput(JsonNode json, ObjectMapper mapper) {
    try {
      List<String> functionalReqs = parseStringArray(json.get("functionalRequirements"));
      List<String> nonFunctionalReqs = parseStringArray(json.get("nonFunctionalRequirements"));
      Map<String, String> constraints = parseStringMap(json.get("constraints"));
      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new IntakeOutput(functionalReqs, nonFunctionalReqs, constraints, metadata);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse IntakeOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses DesignOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "architectureId": "arch-001",
   *   "components": ["UserService", "ProductService"],
   *   "patterns": {"backend": "Microservices", "data": "Event Sourcing"},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static DesignOutput parseDesignOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String archId =
          json.has("architectureId")
              ? json.get("architectureId").asText()
              : "generated-arch-" + System.currentTimeMillis();

      List<String> components =
          json.has("components") ? parseStringArray(json.get("components")) : List.of();

      Map<String, String> patterns =
          json.has("patterns") ? parseStringMap(json.get("patterns")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new DesignOutput(archId, components, patterns, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse DesignOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses PlanUnitsOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "planId": "plan-001",
   *   "implementationUnits": ["unit-1", "unit-2"],
   *   "dependencies": {"unit-2": ["unit-1"]},
   *   "estimatedEffort": {"unit-1": 5, "unit-2": 3},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static PlanUnitsOutput parsePlanUnitsOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String planId =
          json.has("planId")
              ? json.get("planId").asText()
              : "generated-plan-" + System.currentTimeMillis();

      List<String> units =
          json.has("implementationUnits")
              ? parseStringArray(json.get("implementationUnits"))
              : List.of();

      Map<String, List<String>> dependencies =
          json.has("dependencies") ? parseDependenciesMap(json.get("dependencies")) : Map.of();

      Map<String, Integer> effort =
          json.has("estimatedEffort") ? parseEffortMap(json.get("estimatedEffort")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new PlanUnitsOutput(planId, units, dependencies, effort, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse PlanUnitsOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses ScaffoldOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "scaffoldId": "scaffold-001",
   *   "generatedFiles": ["UserService.java", "UserRepository.java"],
   *   "filesByType": {"java": 5, "test": 3},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static ScaffoldOutput parseScaffoldOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String scaffoldId =
          json.has("scaffoldId")
              ? json.get("scaffoldId").asText()
              : "generated-scaffold-" + System.currentTimeMillis();

      List<String> generatedFiles =
          json.has("generatedFiles") ? parseStringArray(json.get("generatedFiles")) : List.of();

      Map<String, Integer> filesByType =
          json.has("filesByType") ? parseIntegerMap(json.get("filesByType")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new ScaffoldOutput(scaffoldId, generatedFiles, filesByType, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse ScaffoldOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses ImplementOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "implementationId": "impl-001",
   *   "unitName": "UserService",
   *   "implementedFiles": ["UserService.java", "UserServiceTest.java"],
   *   "metrics": {"linesOfCode": 250, "cyclomaticComplexity": 5},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static ImplementOutput parseImplementOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String implementationId =
          json.has("implementationId")
              ? json.get("implementationId").asText()
              : "generated-impl-" + System.currentTimeMillis();

      String unitName = json.has("unitName") ? json.get("unitName").asText() : "UnknownUnit";

      List<String> implementedFiles =
          json.has("implementedFiles") ? parseStringArray(json.get("implementedFiles")) : List.of();

      Map<String, Integer> metrics =
          json.has("metrics") ? parseIntegerMap(json.get("metrics")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new ImplementOutput(implementationId, unitName, implementedFiles, metrics, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse ImplementOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses BuildOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "buildId": "build-001",
   *   "success": true,
   *   "artifacts": ["app.jar", "app-sources.jar"],
   *   "buildMetrics": {"duration": 45000, "warnings": 2},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static BuildOutput parseBuildOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String buildId =
          json.has("buildId")
              ? json.get("buildId").asText()
              : "generated-build-" + System.currentTimeMillis();

      boolean success = json.has("success") ? json.get("success").asBoolean() : false;

      List<String> artifacts =
          json.has("artifacts") ? parseStringArray(json.get("artifacts")) : List.of();

      Map<String, Object> buildMetrics =
          json.has("buildMetrics") ? parseMetadata(json.get("buildMetrics")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new BuildOutput(buildId, success, artifacts, buildMetrics, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse BuildOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses ReviewOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "reviewId": "review-001",
   *   "approved": true,
   *   "findings": ["Minor style issue in line 42", "Missing null check"],
   *   "qualityMetrics": {"codeQuality": 85, "testCoverage": 90},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static ReviewOutput parseReviewOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String reviewId =
          json.has("reviewId")
              ? json.get("reviewId").asText()
              : "generated-review-" + System.currentTimeMillis();

      boolean approved = json.has("approved") ? json.get("approved").asBoolean() : false;

      List<String> findings =
          json.has("findings") ? parseStringArray(json.get("findings")) : List.of();

      Map<String, Integer> qualityMetrics =
          json.has("qualityMetrics") ? parseIntegerMap(json.get("qualityMetrics")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new ReviewOutput(reviewId, approved, findings, qualityMetrics, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse ReviewOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses GenerateTestsOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "testPlanId": "test-plan-001",
   *   "generatedTestFiles": ["UserServiceTest.java", "UserControllerTest.java"],
   *   "totalTests": 25,
   *   "linesOfTestCode": 850,
   *   "testType": "unit",
   *   "estimatedCoverage": 85.5
   * }
   * }</pre>
   */
  private static GenerateTestsOutput parseGenerateTestsOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String testPlanId =
          json.has("testPlanId")
              ? json.get("testPlanId").asText()
              : "generated-testplan-" + System.currentTimeMillis();

      List<String> generatedTestFiles =
          json.has("generatedTestFiles")
              ? parseStringArray(json.get("generatedTestFiles"))
              : List.of();

      int totalTests = json.has("totalTests") ? json.get("totalTests").asInt() : 0;

      int linesOfTestCode = json.has("linesOfTestCode") ? json.get("linesOfTestCode").asInt() : 0;

      String testType = json.has("testType") ? json.get("testType").asText() : "unit";

      double estimatedCoverage =
          json.has("estimatedCoverage") ? json.get("estimatedCoverage").asDouble() : 0.0;

      return new GenerateTestsOutput(
          testPlanId, generatedTestFiles, totalTests, linesOfTestCode, testType, estimatedCoverage);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse GenerateTestsOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses DeployStagingOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "deploymentId": "deploy-001",
   *   "environment": "staging",
   *   "status": "SUCCESS",
   *   "deploymentUrl": "https://staging.app.example.com",
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static DeployStagingOutput parseDeployStagingOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String deploymentId =
          json.has("deploymentId")
              ? json.get("deploymentId").asText()
              : "generated-deploy-" + System.currentTimeMillis();

      String environment = json.has("environment") ? json.get("environment").asText() : "staging";

      String status = json.has("status") ? json.get("status").asText() : "PENDING";

      String deploymentUrl = json.has("deploymentUrl") ? json.get("deploymentUrl").asText() : "";

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new DeployStagingOutput(deploymentId, environment, status, deploymentUrl, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse DeployStagingOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses MonitorOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "monitoringId": "monitor-001",
   *   "health": "HEALTHY",
   *   "alerts": ["High latency detected"],
   *   "metrics": {"errorRate": 0.02, "latencyP95": 450},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static MonitorOutput parseMonitorOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String monitoringId =
          json.has("monitoringId")
              ? json.get("monitoringId").asText()
              : "generated-monitor-" + System.currentTimeMillis();

      String health = json.has("health") ? json.get("health").asText() : "UNKNOWN";

      List<String> alerts = json.has("alerts") ? parseStringArray(json.get("alerts")) : List.of();

      Map<String, Object> metrics =
          json.has("metrics") ? parseMetadata(json.get("metrics")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new MonitorOutput(monitoringId, health, alerts, metrics, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse MonitorOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses IncidentResponseOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "incidentId": "incident-001",
   *   "status": "RESOLVED",
   *   "actionsTaken": ["Restarted service", "Scaled up instances"],
   *   "runbookUrl": "https://wiki.example.com/runbook-db-timeout",
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static IncidentResponseOutput parseIncidentResponseOutput(
      JsonNode json, ObjectMapper mapper) {
    try {
      String incidentId =
          json.has("incidentId")
              ? json.get("incidentId").asText()
              : "generated-incident-" + System.currentTimeMillis();

      String status = json.has("status") ? json.get("status").asText() : "INVESTIGATING";

      List<String> actionsTaken =
          json.has("actionsTaken") ? parseStringArray(json.get("actionsTaken")) : List.of();

      String runbookUrl = json.has("runbookUrl") ? json.get("runbookUrl").asText() : "";

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new IncidentResponseOutput(incidentId, status, actionsTaken, runbookUrl, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse IncidentResponseOutput: " + e.getMessage(), e);
    }
  }

  /**
   * Parses CanaryOutput from JSON response.
   *
   * <p>Expected JSON structure:
   *
   * <pre>{@code
   * {
   *   "canaryId": "canary-001",
   *   "status": "SUCCESS",
   *   "errorRate": 0.015,
   *   "latencyP95": 320.5,
   *   "metrics": {"throughput": 1500, "cpuUsage": 45},
   *   "metadata": {...}
   * }
   * }</pre>
   */
  private static CanaryOutput parseCanaryOutput(JsonNode json, ObjectMapper mapper) {
    try {
      String canaryId =
          json.has("canaryId")
              ? json.get("canaryId").asText()
              : "generated-canary-" + System.currentTimeMillis();

      String status = json.has("status") ? json.get("status").asText() : "PENDING";

      double errorRate = json.has("errorRate") ? json.get("errorRate").asDouble() : 0.0;

      double latencyP95 = json.has("latencyP95") ? json.get("latencyP95").asDouble() : 0.0;

      Map<String, Object> metrics =
          json.has("metrics") ? parseMetadata(json.get("metrics")) : Map.of();

      Map<String, Object> metadata = parseMetadata(json.get("metadata"));

      return new CanaryOutput(canaryId, status, errorRate, latencyP95, metrics, metadata);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse CanaryOutput: " + e.getMessage(), e);
    }
  }

  // ============================================
  // HELPER METHODS
  // ============================================
  private static Map<String, List<String>> parseDependenciesMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Map.of();
    }

    Map<String, List<String>> result = new java.util.HashMap<>();
    node.fields()
        .forEachRemaining(
            entry -> {
              String key = entry.getKey();
              JsonNode value = entry.getValue();
              if (value.isArray()) {
                result.put(key, parseStringArray(value));
              } else {
                result.put(key, List.of(value.asText()));
              }
            });
    return result;
  }

  /** Parses effort map: {"unit-1": 5, "unit-2": 3}. */
  private static Map<String, Integer> parseEffortMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Map.of();
    }

    Map<String, Integer> result = new java.util.HashMap<>();
    node.fields()
        .forEachRemaining(
            entry -> {
              String key = entry.getKey();
              int value = entry.getValue().asInt(0);
              result.put(key, value);
            });
    return result;
  }

  /**
   * Parses integer map: {"key1": 10, "key2": 20}. Used for metrics, counts, and other numeric
   * mappings.
   */
  private static Map<String, Integer> parseIntegerMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Map.of();
    }

    Map<String, Integer> result = new java.util.HashMap<>();
    node.fields()
        .forEachRemaining(
            entry -> {
              String key = entry.getKey();
              int value = entry.getValue().asInt(0);
              result.put(key, value);
            });
    return result;
  }

  // ============================================
  // HELPER METHODS
  // ============================================

  private static List<String> parseStringArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (JsonNode item : node) {
      result.add(item.asText());
    }
    return result;
  }

  private static Map<String, String> parseStringMap(JsonNode node) {
    if (node == null || !node.isObject()) {
      return Map.of();
    }
    Map<String, String> result = new HashMap<>();
    node.fields()
        .forEachRemaining(
            entry -> {
              result.put(entry.getKey(), entry.getValue().asText());
            });
    return result;
  }

  private static Map<String, Object> parseMetadata(JsonNode node) {
    if (node == null || !node.isObject()) {
      return Map.of();
    }
    Map<String, Object> result = new HashMap<>();
    node.fields()
        .forEachRemaining(
            entry -> {
              JsonNode value = entry.getValue();
              if (value.isTextual()) {
                result.put(entry.getKey(), value.asText());
              } else if (value.isNumber()) {
                result.put(entry.getKey(), value.asDouble());
              } else if (value.isBoolean()) {
                result.put(entry.getKey(), value.asBoolean());
              } else {
                result.put(entry.getKey(), value.toString());
              }
            });
    return result;
  }
}
