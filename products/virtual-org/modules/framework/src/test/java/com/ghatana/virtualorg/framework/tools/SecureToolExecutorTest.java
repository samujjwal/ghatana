package com.ghatana.virtualorg.framework.tools;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.hitl.AuditEntry;
import com.ghatana.virtualorg.framework.hitl.AuditQuery;
import com.ghatana.virtualorg.framework.hitl.InMemoryAuditTrail;
import io.activej.promise.Promise;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SecureToolExecutor.
 *
 * @doc.type class
 * @doc.purpose Test secure tool execution
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("SecureToolExecutor Tests")
class SecureToolExecutorTest extends EventloopTestBase {

    private ToolRegistry registry;
    private InMemoryAuditTrail auditTrail;
    private SecureToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        auditTrail = new InMemoryAuditTrail();

        // Register a test tool
        registry.register(new TestTool("test.echo", Set.of("test.read")));
        registry.register(new TestTool("test.write", Set.of("test.write")));
        registry.register(new SlowTestTool("test.slow", Set.of("test.read")));

        executor = SecureToolExecutor.builder()
                .toolRegistry(registry)
                .auditTrail(auditTrail)
                .defaultRateLimit(5, Duration.ofSeconds(1))
                .defaultTimeout(Duration.ofMillis(100))
                .build();
    }

    @Test
    @DisplayName("Should execute tool with valid permissions")
    void shouldExecuteWithValidPermissions() {
        // GIVEN
        ToolInput input = ToolInput.builder()
                .put("message", "Hello")
                .build();
        ToolContext context = new ToolContext("agent-001", "task-001", Map.of());
        Set<String> permissions = Set.of("test.read", "test.write");

        // WHEN
        ToolResult result = runPromise(()
                -> executor.execute("test.echo", input, context, permissions)
        );

        // THEN
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().get("echoed")).isEqualTo("Hello");
    }

    @Test
    @DisplayName("Should reject execution without required permissions")
    void shouldRejectWithoutPermissions() {
        // GIVEN
        ToolInput input = ToolInput.builder().build();
        ToolContext context = new ToolContext("agent-001", "task-001", Map.of());
        Set<String> permissions = Set.of("other.permission");

        // WHEN
        ToolResult result = runPromise(()
                -> executor.execute("test.echo", input, context, permissions)
        );

        // THEN
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Permission denied");
    }

    @Test
    @DisplayName("Should return error for unknown tool")
    void shouldReturnErrorForUnknownTool() {
        // GIVEN
        ToolInput input = ToolInput.builder().build();
        ToolContext context = new ToolContext("agent-001", "task-001", Map.of());
        Set<String> permissions = Set.of("test.read");

        // WHEN
        ToolResult result = runPromise(()
                -> executor.execute("unknown.tool", input, context, permissions)
        );

        // THEN
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Tool not found");
    }

    @Test
    @DisplayName("Should enforce rate limits")
    void shouldEnforceRateLimits() {
        // GIVEN
        ToolInput input = ToolInput.builder().build();
        ToolContext context = new ToolContext("agent-001", "task-001", Map.of());
        Set<String> permissions = Set.of("test.read");

        // WHEN - Execute 6 times (limit is 5)
        for (int i = 0; i < 5; i++) {
            ToolResult result = runPromise(()
                    -> executor.execute("test.echo", input, context, permissions)
            );
            assertThat(result.isSuccess()).isTrue();
        }

        // 6th call should be rate limited
        ToolResult result = runPromise(()
                -> executor.execute("test.echo", input, context, permissions)
        );

        // THEN
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Rate limit exceeded");
    }

    @Test
    @DisplayName("Should audit successful executions")
    void shouldAuditSuccessfulExecutions() {
        // GIVEN
        ToolInput input = ToolInput.builder()
                .put("message", "test")
                .build();
        ToolContext context = new ToolContext("agent-001", "task-001", Map.of());
        Set<String> permissions = Set.of("test.read");

        // WHEN
        runPromise(() -> executor.execute("test.echo", input, context, permissions));

        // THEN
        List<AuditEntry> entries = runPromise(() -> auditTrail.query(AuditQuery.builder()
                .agentId("agent-001")
                .eventType("tool.executed")
                .build()));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).data().get("tool")).isEqualTo("test.echo");
        assertThat(entries.get(0).data().get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Should audit failed executions")
    void shouldAuditFailedExecutions() {
        // GIVEN
        ToolInput input = ToolInput.builder().build();
        ToolContext context = new ToolContext("agent-002", "task-002", Map.of());
        Set<String> permissions = Set.of("wrong.permission");

        // WHEN
        runPromise(() -> executor.execute("test.echo", input, context, permissions));

        // THEN
        List<AuditEntry> entries = runPromise(() -> auditTrail.query(AuditQuery.builder()
                .agentId("agent-002")
                .eventType("tool.failed")
                .build()));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).data().get("error_type")).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    @DisplayName("Should provide rate limit info")
    void shouldProvideRateLimitInfo() {
        // GIVEN
        ToolInput input = ToolInput.builder().build();
        ToolContext context = new ToolContext("agent-003", "task-003", Map.of());
        Set<String> permissions = Set.of("test.read");

        // Execute twice
        runPromise(() -> executor.execute("test.echo", input, context, permissions));
        runPromise(() -> executor.execute("test.echo", input, context, permissions));

        // WHEN
        SecureToolExecutor.RateLimitInfo info = executor.getRateLimitInfo("test.echo", "agent-003");

        // THEN
        assertThat(info.limit()).isEqualTo(5);
        assertThat(info.remaining()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should reset rate limit")
    void shouldResetRateLimit() {
        // GIVEN
        ToolInput input = ToolInput.builder().build();
        ToolContext context = new ToolContext("agent-004", "task-004", Map.of());
        Set<String> permissions = Set.of("test.read");

        // Exhaust rate limit
        for (int i = 0; i < 5; i++) {
            runPromise(() -> executor.execute("test.echo", input, context, permissions));
        }

        // WHEN
        executor.resetRateLimit("test.echo", "agent-004");

        // THEN
        ToolResult result = runPromise(()
                -> executor.execute("test.echo", input, context, permissions)
        );
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should have separate rate limits per agent")
    void shouldHaveSeparateRateLimitsPerAgent() {
        // GIVEN
        ToolInput input = ToolInput.builder().build();
        Set<String> permissions = Set.of("test.read");

        ToolContext context1 = new ToolContext("agent-A", "task-A", Map.of());
        ToolContext context2 = new ToolContext("agent-B", "task-B", Map.of());

        // Exhaust rate limit for agent-A
        for (int i = 0; i < 5; i++) {
            runPromise(() -> executor.execute("test.echo", input, context1, permissions));
        }

        // WHEN - agent-B should still have quota
        ToolResult result = runPromise(()
                -> executor.execute("test.echo", input, context2, permissions)
        );

        // THEN
        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * Simple test tool implementation.
     */
    private static class TestTool implements AgentTool {

        private final String name;
        private final Set<String> permissions;

        TestTool(String name, Set<String> permissions) {
            this.name = name;
            this.permissions = permissions;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Test tool";
        }

        @Override
        public ToolSchema getInputSchema() {
            return ToolSchema.empty();
        }

        @Override
        public ToolSchema getOutputSchema() {
            return ToolSchema.empty();
        }

        @Override
        public Set<String> getRequiredPermissions() {
            return permissions;
        }

        @Override
        public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
            return Promise.of(ToolResult.success(Map.of(
                    "echoed", input.getString("message", "default")
            )));
        }
    }

    /**
     * Slow test tool that times out.
     */
    private static class SlowTestTool implements AgentTool {

        private final String name;
        private final Set<String> permissions;

        SlowTestTool(String name, Set<String> permissions) {
            this.name = name;
            this.permissions = permissions;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "Slow test tool";
        }

        @Override
        public ToolSchema getInputSchema() {
            return ToolSchema.empty();
        }

        @Override
        public ToolSchema getOutputSchema() {
            return ToolSchema.empty();
        }

        @Override
        public Set<String> getRequiredPermissions() {
            return permissions;
        }

        @Override
        public Promise<ToolResult> execute(ToolInput input, ToolContext context) {
            // Simulate slow operation
            return Promise.ofBlocking(java.util.concurrent.Executors.newSingleThreadExecutor(), () -> {
                Thread.sleep(500);
                return ToolResult.success(Map.of("result", "done"));
            });
        }
    }
}
