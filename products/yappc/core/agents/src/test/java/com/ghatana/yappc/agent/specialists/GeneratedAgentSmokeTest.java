package com.ghatana.yappc.agent.specialists;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for generated specialist agents — verifies all agents
 * instantiate correctly and execute the full GAA lifecycle.
 *
 * @doc.type class
 * @doc.purpose Smoke tests verifying agent scaffolding correctness
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Generated Agent Smoke Tests")
class GeneratedAgentSmokeTest extends EventloopTestBase {

  private MemoryStore memoryStore;

  @BeforeEach
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    YAPPCAgentBase.configureAepEventPublisher(
        (eventType, tenantId, payload) -> Promise.complete());
  }

  private StepContext ctx() {
    return new StepContext("run-smoke", "tenant-1", "smoke-test", "config-1",
        new StepBudget(50.0, 120_000));
  }

  // ===== L1 Strategic Agents =====

  @Test
  @DisplayName("ProductsOfficerAgent should execute")
  void productsOfficer() {
    var agent = new ProductsOfficerAgent(memoryStore,
        new ProductsOfficerAgent.ProductsOfficerGenerator());
    var result = runPromise(() -> agent.execute(
        new ProductsOfficerInput("portfolio-1", List.of("grow", "scale"), Map.of()), ctx()));
    assertThat(result.success()).isTrue();
    assertThat(agent.stepName()).isEqualTo("strategic.products-officer");
  }

  @Test
  @DisplayName("SystemsArchitectAgent should execute")
  void systemsArchitect() {
    var agent = new SystemsArchitectAgent(memoryStore,
        new SystemsArchitectAgent.SystemsArchitectGenerator());
    var result = runPromise(() -> agent.execute(
        new SystemsArchitectInput("sys-1", "microservices vs monolith", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("FullLifecycleOrchestratorAgent should execute")
  void fullLifecycle() {
    var agent = new FullLifecycleOrchestratorAgent(memoryStore,
        new FullLifecycleOrchestratorAgent.FullLifecycleOrchestratorGenerator());
    var result = runPromise(() -> agent.execute(
        new FullLifecycleOrchestratorInput("proj-1", "intake", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  // ===== L2 Expert Agents =====

  @Test
  @DisplayName("JavaExpertAgent should execute")
  void javaExpert() {
    var agent = new JavaExpertAgent(memoryStore,
        new JavaExpertAgent.JavaExpertGenerator());
    var result = runPromise(() -> agent.execute(
        new JavaExpertInput("class Foo {}", "How to add logging?", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("SentinelAgent should execute")
  void sentinel() {
    var agent = new SentinelAgent(memoryStore,
        new SentinelAgent.SentinelGenerator());
    var result = runPromise(() -> agent.execute(
        new SentinelInput("service-1", "dependency-scan", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("DebugOrchestratorAgent should execute")
  void debugOrchestrator() {
    var agent = new DebugOrchestratorAgent(memoryStore,
        new DebugOrchestratorAgent.DebugOrchestratorGenerator());
    var result = runPromise(() -> agent.execute(
        new DebugOrchestratorInput("incident-1", "NullPointerException at line 42", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  // ===== L3 Worker Agents: Code Generation =====

  @Test
  @DisplayName("JavaClassWriterAgent should execute")
  void javaClassWriter() {
    var agent = new JavaClassWriterAgent(memoryStore,
        new JavaClassWriterAgent.JavaClassWriterGenerator());
    var result = runPromise(() -> agent.execute(
        new JavaClassWriterInput("UserService", "com.example", "CRUD service for User entity", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("UnitTestWriterAgent should execute")
  void unitTestWriter() {
    var agent = new UnitTestWriterAgent(memoryStore,
        new UnitTestWriterAgent.UnitTestWriterGenerator());
    var result = runPromise(() -> agent.execute(
        new UnitTestWriterInput("class Foo { void bar() {} }", "Foo", "junit5", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  // ===== L3 Worker Agents: Debug =====

  @Test
  @DisplayName("LogAnalysisAgent should execute")
  void logAnalysis() {
    var agent = new LogAnalysisAgent(memoryStore,
        new LogAnalysisAgent.LogAnalysisGenerator());
    var result = runPromise(() -> agent.execute(
        new LogAnalysisInput("/var/log/app.log", "last-1h", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("StackTraceAnalyzerAgent should execute")
  void stackTraceAnalyzer() {
    var agent = new StackTraceAnalyzerAgent(memoryStore,
        new StackTraceAnalyzerAgent.StackTraceAnalyzerGenerator());
    var result = runPromise(() -> agent.execute(
        new StackTraceAnalyzerInput("java.lang.NullPointerException\n\tat com.example.Main.run(Main.java:42)", "java", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("RootCauseAnalysisAgent should execute")
  void rootCauseAnalysis() {
    var agent = new RootCauseAnalysisAgent(memoryStore,
        new RootCauseAnalysisAgent.RootCauseAnalysisGenerator());
    var result = runPromise(() -> agent.execute(
        new RootCauseAnalysisInput("inc-1", List.of("high CPU", "slow response"), Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  // ===== L3 Worker Agents: Release Governance =====

  @Test
  @DisplayName("SbomGeneratorAgent should execute")
  void sbomGenerator() {
    var agent = new SbomGeneratorAgent(memoryStore,
        new SbomGeneratorAgent.SbomGeneratorGenerator());
    var result = runPromise(() -> agent.execute(
        new SbomGeneratorInput("proj-1", "build-123", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("ReleaseGateAgent should execute")
  void releaseGate() {
    var agent = new ReleaseGateAgent(memoryStore,
        new ReleaseGateAgent.ReleaseGateGenerator());
    var result = runPromise(() -> agent.execute(
        new ReleaseGateInput("release-1", Map.of("coverage", 80), Map.of("minCoverage", 75)), ctx()));
    assertThat(result.success()).isTrue();
  }

  // ===== L3 Worker Agents: Integration Bridges =====

  @Test
  @DisplayName("RepoIntegrationAgent should execute")
  void repoIntegration() {
    var agent = new RepoIntegrationAgent(memoryStore,
        new RepoIntegrationAgent.RepoIntegrationGenerator());
    var result = runPromise(() -> agent.execute(
        new RepoIntegrationInput("https://github.com/example/repo", "clone", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("NotificationAgent should execute")
  void notification() {
    var agent = new NotificationAgent(memoryStore,
        new NotificationAgent.NotificationGenerator());
    var result = runPromise(() -> agent.execute(
        new NotificationInput("slack", "user-1", "Build succeeded", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  // ===== L3 Worker Agents: Governance =====

  @Test
  @DisplayName("BudgetGateAgent should execute")
  void budgetGate() {
    var agent = new BudgetGateAgent(memoryStore,
        new BudgetGateAgent.BudgetGateGenerator());
    var result = runPromise(() -> agent.execute(
        new BudgetGateInput("req-1", 5.0, "ai-inference", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  // ===== L3 Worker Agents: Stack Experts =====

  @Test
  @DisplayName("ActivejExpertAgent should execute")
  void activejExpert() {
    var agent = new ActivejExpertAgent(memoryStore,
        new ActivejExpertAgent.ActivejExpertGenerator());
    var result = runPromise(() -> agent.execute(
        new ActivejExpertInput("Promise.of(42)", "How to chain promises?", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }

  @Test
  @DisplayName("PrismaExpertAgent should execute")
  void prismaExpert() {
    var agent = new PrismaExpertAgent(memoryStore,
        new PrismaExpertAgent.PrismaExpertGenerator());
    var result = runPromise(() -> agent.execute(
        new PrismaExpertInput("model User { id Int @id }", "Add relations", Map.of()), ctx()));
    assertThat(result.success()).isTrue();
  }
}
