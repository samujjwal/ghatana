package com.ghatana.yappc.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.tools.provider.ToolProvider;
import com.ghatana.yappc.agent.tools.provider.ToolRegistry;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ToolRegistry} — the capability-based tool provider registry
 * that maps capability IDs to tool implementations.
 *
 * @doc.type class
 * @doc.purpose Unit tests for ToolRegistry registration, lookup, execution, and fallback
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ToolRegistry Tests")
class ToolRegistryTest extends EventloopTestBase {

  private ToolRegistry registry;
  private AgentContext ctx;

  @BeforeEach
  void setUp() {
    registry = new ToolRegistry();
    ctx = AgentContext.builder()
        .agentId("test-agent")
        .turnId("turn-001")
        .tenantId("tenant-1")
        .sessionId("session-1")
        .memoryStore(MemoryStore.noOp())
        .build();
  }

  // ===== Registration Tests =====

  @Nested
  @DisplayName("Registration")
  class Registration {

    @Test
    @DisplayName("Should register provider and track capability")
    void shouldRegisterProvider() {
      ToolProvider provider = createMockProvider("code-generation", "CodeGenTool");

      registry.register(provider);

      assertThat(registry.hasCapability("code-generation")).isTrue();
      assertThat(registry.getCapabilityCount()).isEqualTo(1);
      assertThat(registry.getProviderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should support method chaining")
    void shouldSupportChaining() {
      ToolProvider p1 = createMockProvider("code-gen", "CodeGenTool");
      ToolProvider p2 = createMockProvider("test-gen", "TestGenTool");

      ToolRegistry result = registry.register(p1).register(p2);

      assertThat(result).isSameAs(registry);
      assertThat(registry.getCapabilityCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should register multiple providers for same capability")
    void shouldRegisterMultipleForSameCapability() {
      ToolProvider p1 = createMockProvider("code-generation", "CodeGenV1");
      ToolProvider p2 = createMockProvider("code-generation", "CodeGenV2");

      registry.register(p1).register(p2);

      List<ToolProvider> providers = registry.getProvidersForCapability("code-generation");
      assertThat(providers).hasSize(2);
      assertThat(registry.getCapabilityCount()).isEqualTo(1); // same capability
      assertThat(registry.getProviderCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should reject null provider")
    void shouldRejectNullProvider() {
      assertThatThrownBy(() -> registry.register(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject provider with blank capability ID")
    void shouldRejectBlankCapability() {
      ToolProvider provider = mock(ToolProvider.class);
      when(provider.getCapabilityId()).thenReturn("");

      assertThatThrownBy(() -> registry.register(provider))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should register all providers at once")
    void shouldRegisterAll() {
      ToolProvider p1 = createMockProvider("cap-a", "ToolA");
      ToolProvider p2 = createMockProvider("cap-b", "ToolB");
      ToolProvider p3 = createMockProvider("cap-c", "ToolC");

      registry.registerAll(p1, p2, p3);

      assertThat(registry.getCapabilityCount()).isEqualTo(3);
    }
  }

  // ===== Lookup Tests =====

  @Nested
  @DisplayName("Lookup")
  class Lookup {

    @Test
    @DisplayName("Should find provider by tool name")
    void shouldFindByName() {
      ToolProvider provider = createMockProvider("code-gen", "MyCodeGen");
      registry.register(provider);

      ToolProvider found = registry.getProviderByName("MyCodeGen");

      assertThat(found).isSameAs(provider);
    }

    @Test
    @DisplayName("Should return null for unknown tool name")
    void shouldReturnNullForUnknown() {
      assertThat(registry.getProviderByName("nonexistent")).isNull();
    }

    @Test
    @DisplayName("Should return all registered capabilities")
    void shouldReturnAllCapabilities() {
      registry.register(createMockProvider("cap-a", "ToolA"));
      registry.register(createMockProvider("cap-b", "ToolB"));

      Set<String> capabilities = registry.getRegisteredCapabilities();

      assertThat(capabilities).containsExactlyInAnyOrder("cap-a", "cap-b");
    }

    @Test
    @DisplayName("Should return false for unregistered capability")
    void shouldReturnFalseForUnregistered() {
      assertThat(registry.hasCapability("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Should return empty list for unregistered capability providers")
    void shouldReturnEmptyListForUnregistered() {
      List<ToolProvider> providers = registry.getProvidersForCapability("nonexistent");
      assertThat(providers).isEmpty();
    }
  }

  // ===== Execution Tests =====

  @Nested
  @DisplayName("Execution")
  class Execution {

    @Test
    @DisplayName("Should execute tool via capability ID")
    void shouldExecuteByCapability() {
      ToolProvider provider = mock(ToolProvider.class);
      when(provider.getCapabilityId()).thenReturn("code-gen");
      when(provider.getToolName()).thenReturn("CodeGenTool");
      when(provider.validateParams(any())).thenReturn(null);
      when(provider.isHealthy()).thenReturn(true);
      when(provider.execute(any(), any())).thenReturn(
          Promise.of(ToolProvider.ToolResult.success("generated code", Map.of())));

      registry.register(provider);

      ToolProvider.ToolResult result = runPromise(
          () -> registry.execute(ctx, "code-gen", Map.of("spec", "build API")));

      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("generated code");
    }

    @Test
    @DisplayName("Should return failure for unknown capability")
    void shouldFailForUnknownCapability() {
      ToolProvider.ToolResult result = runPromise(
          () -> registry.execute(ctx, "nonexistent", Map.of()));

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("No provider for capability");
    }

    @Test
    @DisplayName("Should skip unhealthy provider and try next")
    void shouldSkipUnhealthyProvider() {
      ToolProvider unhealthy = mock(ToolProvider.class);
      when(unhealthy.getCapabilityId()).thenReturn("code-gen");
      when(unhealthy.getToolName()).thenReturn("UnhealthyTool");
      when(unhealthy.validateParams(any())).thenReturn(null);
      when(unhealthy.isHealthy()).thenReturn(false);

      ToolProvider healthy = mock(ToolProvider.class);
      when(healthy.getCapabilityId()).thenReturn("code-gen");
      when(healthy.getToolName()).thenReturn("HealthyTool");
      when(healthy.validateParams(any())).thenReturn(null);
      when(healthy.isHealthy()).thenReturn(true);
      when(healthy.execute(any(), any())).thenReturn(
          Promise.of(ToolProvider.ToolResult.success("fallback output", Map.of())));

      registry.register(unhealthy);
      registry.register(healthy);

      ToolProvider.ToolResult result = runPromise(
          () -> registry.execute(ctx, "code-gen", Map.of()));

      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("fallback output");
    }

    @Test
    @DisplayName("Should skip provider with validation failures and try next")
    void shouldSkipValidationFailure() {
      ToolProvider invalid = mock(ToolProvider.class);
      when(invalid.getCapabilityId()).thenReturn("code-gen");
      when(invalid.getToolName()).thenReturn("InvalidTool");
      when(invalid.validateParams(any())).thenReturn("Missing required param");

      ToolProvider valid = mock(ToolProvider.class);
      when(valid.getCapabilityId()).thenReturn("code-gen");
      when(valid.getToolName()).thenReturn("ValidTool");
      when(valid.validateParams(any())).thenReturn(null);
      when(valid.isHealthy()).thenReturn(true);
      when(valid.execute(any(), any())).thenReturn(
          Promise.of(ToolProvider.ToolResult.success("valid output", Map.of())));

      registry.register(invalid);
      registry.register(valid);

      ToolProvider.ToolResult result = runPromise(
          () -> registry.execute(ctx, "code-gen", Map.of()));

      assertThat(result.success()).isTrue();
      assertThat(result.output()).isEqualTo("valid output");
    }

    @Test
    @DisplayName("Should return failure when all providers fail")
    void shouldReturnFailureWhenAllFail() {
      ToolProvider failing = mock(ToolProvider.class);
      when(failing.getCapabilityId()).thenReturn("code-gen");
      when(failing.getToolName()).thenReturn("FailingTool");
      when(failing.validateParams(any())).thenReturn(null);
      when(failing.isHealthy()).thenReturn(true);
      when(failing.execute(any(), any())).thenReturn(
          Promise.of(ToolProvider.ToolResult.failure("tool error", Map.of())));

      registry.register(failing);

      ToolProvider.ToolResult result = runPromise(
          () -> registry.execute(ctx, "code-gen", Map.of()));

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage()).contains("All providers failed");
    }
  }

  // ===== Health Status Tests =====

  @Nested
  @DisplayName("Health Status")
  class HealthStatus {

    @Test
    @DisplayName("Should report health status for all providers")
    void shouldReportHealthStatus() {
      ToolProvider healthy = createMockProvider("cap-a", "HealthyTool");
      when(healthy.isHealthy()).thenReturn(true);

      ToolProvider unhealthy = createMockProvider("cap-b", "UnhealthyTool");
      when(unhealthy.isHealthy()).thenReturn(false);

      registry.register(healthy);
      registry.register(unhealthy);

      Map<String, Boolean> status = registry.getHealthStatus();

      assertThat(status).containsEntry("HealthyTool", true);
      assertThat(status).containsEntry("UnhealthyTool", false);
    }

    @Test
    @DisplayName("Should return empty status for empty registry")
    void shouldReturnEmptyStatusForEmptyRegistry() {
      assertThat(registry.getHealthStatus()).isEmpty();
    }
  }

  // ===== Empty Registry =====

  @Nested
  @DisplayName("Empty Registry")
  class EmptyRegistryTests {

    @Test
    @DisplayName("Should report zero counts when empty")
    void shouldReportZeroCounts() {
      assertThat(registry.getCapabilityCount()).isEqualTo(0);
      assertThat(registry.getProviderCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return empty capabilities set")
    void shouldReturnEmptyCapabilities() {
      assertThat(registry.getRegisteredCapabilities()).isEmpty();
    }
  }

  // ===== Test Helpers =====

  private ToolProvider createMockProvider(String capabilityId, String toolName) {
    ToolProvider provider = mock(ToolProvider.class);
    when(provider.getCapabilityId()).thenReturn(capabilityId);
    when(provider.getToolName()).thenReturn(toolName);
    when(provider.isHealthy()).thenReturn(true);
    return provider;
  }
}
