package com.ghatana.yappc.agents.testing;

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
import com.ghatana.yappc.agents.architecture.*;
import com.ghatana.yappc.agents.code.*;

/**
 * Smoke tests for generated specialist agents — verifies all agents
 * instantiate correctly and execute the full GAA lifecycle.
 *
 * @doc.type class
 * @doc.purpose Smoke tests verifying agent scaffolding correctness
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Generated Agent Smoke Tests [GH-90000]")
class GeneratedAgentSmokeTest extends EventloopTestBase {

  private MemoryStore memoryStore;

  @BeforeEach
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    YAPPCAgentBase.setGlobalAepEventPublisher( // GH-90000
        (eventType, tenantId, payload) -> Promise.complete()); // GH-90000
  }

  private StepContext ctx() { // GH-90000
    return new StepContext("run-smoke", "tenant-1", "smoke-test", "config-1", // GH-90000
        new StepBudget(50.0, 120_000)); // GH-90000
  }

  // ===== L1 Strategic Agents =====

  @Test
  @DisplayName("ProductsOfficerAgent should execute [GH-90000]")
  void productsOfficer() { // GH-90000
    var agent = new ProductsOfficerAgent(memoryStore, // GH-90000
        new ProductsOfficerAgent.ProductsOfficerGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new ProductsOfficerInput("portfolio-1", List.of("grow", "scale"), Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
    assertThat(agent.stepName()).isEqualTo("strategic.products-officer [GH-90000]");
  }

  @Test
  @DisplayName("SystemsArchitectAgent should execute [GH-90000]")
  void systemsArchitect() { // GH-90000
    var agent = new SystemsArchitectAgent(memoryStore, // GH-90000
        new SystemsArchitectAgent.SystemsArchitectGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new SystemsArchitectInput("sys-1", "microservices vs monolith", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("FullLifecycleOrchestratorAgent should execute [GH-90000]")
  void fullLifecycle() { // GH-90000
    var agent = new FullLifecycleOrchestratorAgent(memoryStore, // GH-90000
        new FullLifecycleOrchestratorAgent.FullLifecycleOrchestratorGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new FullLifecycleOrchestratorInput("proj-1", "intake", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  // ===== L2 Expert Agents =====

  @Test
  @DisplayName("JavaExpertAgent should execute [GH-90000]")
  void javaExpert() { // GH-90000
    var agent = new JavaExpertAgent(memoryStore, // GH-90000
        new JavaExpertAgent.JavaExpertGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new JavaExpertInput("class Foo {}", "How to add logging?", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("SentinelAgent should execute [GH-90000]")
  void sentinel() { // GH-90000
    var agent = new SentinelAgent(memoryStore, // GH-90000
        new SentinelAgent.SentinelGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new SentinelInput("service-1", "dependency-scan", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("DebugOrchestratorAgent should execute [GH-90000]")
  void debugOrchestrator() { // GH-90000
    var agent = new DebugOrchestratorAgent(memoryStore, // GH-90000
        new DebugOrchestratorAgent.DebugOrchestratorGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new DebugOrchestratorInput("incident-1", "NullPointerException at line 42", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  // ===== L3 Worker Agents: Code Generation =====

  @Test
  @DisplayName("JavaClassWriterAgent should execute [GH-90000]")
  void javaClassWriter() { // GH-90000
    var agent = new JavaClassWriterAgent(memoryStore, // GH-90000
        new JavaClassWriterAgent.JavaClassWriterGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new JavaClassWriterInput("UserService", "com.example", "CRUD service for User entity", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("UnitTestWriterAgent should execute [GH-90000]")
  void unitTestWriter() { // GH-90000
    var agent = new UnitTestWriterAgent(memoryStore, // GH-90000
        new UnitTestWriterAgent.UnitTestWriterGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new UnitTestWriterInput("class Foo { void bar() {} }", "Foo", "junit5", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  // ===== L3 Worker Agents: Debug =====

  @Test
  @DisplayName("LogAnalysisAgent should execute [GH-90000]")
  void logAnalysis() { // GH-90000
    var agent = new LogAnalysisAgent(memoryStore, // GH-90000
        new LogAnalysisAgent.LogAnalysisGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new LogAnalysisInput("/var/log/app.log", "last-1h", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("StackTraceAnalyzerAgent should execute [GH-90000]")
  void stackTraceAnalyzer() { // GH-90000
    var agent = new StackTraceAnalyzerAgent(memoryStore, // GH-90000
        new StackTraceAnalyzerAgent.StackTraceAnalyzerGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new StackTraceAnalyzerInput("java.lang.NullPointerException\n\tat com.example.Main.run(Main.java:42)", "java", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("RootCauseAnalysisAgent should execute [GH-90000]")
  void rootCauseAnalysis() { // GH-90000
    var agent = new RootCauseAnalysisAgent(memoryStore, // GH-90000
        new RootCauseAnalysisAgent.RootCauseAnalysisGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new RootCauseAnalysisInput("inc-1", List.of("high CPU", "slow response"), Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  // ===== L3 Worker Agents: Release Governance =====

  @Test
  @DisplayName("SbomGeneratorAgent should execute [GH-90000]")
  void sbomGenerator() { // GH-90000
    var agent = new SbomGeneratorAgent(memoryStore, // GH-90000
        new SbomGeneratorAgent.SbomGeneratorGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new SbomGeneratorInput("proj-1", "build-123", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("ReleaseGateAgent should execute [GH-90000]")
  void releaseGate() { // GH-90000
    var agent = new ReleaseGateAgent(memoryStore, // GH-90000
        new ReleaseGateAgent.ReleaseGateGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new ReleaseGateInput("release-1", Map.of("coverage", 80), Map.of("minCoverage", 75)), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  // ===== L3 Worker Agents: Integration Bridges =====

  @Test
  @DisplayName("RepoIntegrationAgent should execute [GH-90000]")
  void repoIntegration() { // GH-90000
    var agent = new RepoIntegrationAgent(memoryStore, // GH-90000
        new RepoIntegrationAgent.RepoIntegrationGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new RepoIntegrationInput("https://github.com/example/repo", "clone", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("NotificationAgent should execute [GH-90000]")
  void notification() { // GH-90000
    var agent = new NotificationAgent(memoryStore, // GH-90000
        new NotificationAgent.NotificationGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new NotificationInput("slack", "user-1", "Build succeeded", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  // ===== L3 Worker Agents: Governance =====

  @Test
  @DisplayName("BudgetGateAgent should execute [GH-90000]")
  void budgetGate() { // GH-90000
    var agent = new BudgetGateAgent(memoryStore, // GH-90000
        new BudgetGateAgent.BudgetGateGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new BudgetGateInput("req-1", 5.0, "ai-inference", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  // ===== L3 Worker Agents: Stack Experts =====

  @Test
  @DisplayName("ActivejExpertAgent should execute [GH-90000]")
  void activejExpert() { // GH-90000
    var agent = new ActivejExpertAgent(memoryStore, // GH-90000
        new ActivejExpertAgent.ActivejExpertGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new ActivejExpertInput("Promise.of(42)", "How to chain promises?", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }

  @Test
  @DisplayName("PrismaExpertAgent should execute [GH-90000]")
  void prismaExpert() { // GH-90000
    var agent = new PrismaExpertAgent(memoryStore, // GH-90000
        new PrismaExpertAgent.PrismaExpertGenerator()); // GH-90000
    var result = runPromise(() -> agent.execute( // GH-90000
        new PrismaExpertInput("model User { id Int @id }", "Add relations", Map.of()), ctx())); // GH-90000
    assertThat(result.success()).isTrue(); // GH-90000
  }
}
