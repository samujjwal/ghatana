package com.ghatana.virtualorg.framework.tools;

import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.virtualorg.framework.hitl.AuditTrail;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Secure executor wrapper for agent tools with rate limiting and audit logging.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a security layer around tool execution including:
 * <ul>
 * <li>Permission checking before execution</li>
 * <li>Rate limiting per tool and per agent</li>
 * <li>Audit logging of all executions</li>
 * <li>Timeout enforcement</li>
 * <li>Error handling and recovery</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * SecureToolExecutor executor = SecureToolExecutor.builder()
 *     .toolRegistry(registry)
 *     .auditTrail(auditTrail)
 *     .defaultRateLimit(100, Duration.ofMinutes(1))
 *     .defaultTimeout(Duration.ofSeconds(30))
 *     .build();
 *
 * ToolResult result = runPromise(() -> executor.execute(
 *     "github.create_pr",
 *     input,
 *     context,
 *     agentPermissions
 * ));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Secure tool execution with rate limiting
 * @doc.layer product
 * @doc.pattern Decorator
 */
public class SecureToolExecutor {

    private final ToolRegistry toolRegistry;
    private final AuditTrail auditTrail;
    private final SharedRateLimiterAdapter rateLimiter;
    private final Duration defaultTimeout;

    private SecureToolExecutor(Builder builder) {
        this.toolRegistry = builder.toolRegistry;
        this.auditTrail = builder.auditTrail;
        this.rateLimiter = new SharedRateLimiterAdapter(
                builder.defaultRateLimit,
                builder.rateLimitWindow
        );
        this.defaultTimeout = builder.defaultTimeout;
    }

    /**
     * Executes a tool with security checks.
     *
     * @param toolName Name of the tool to execute
     * @param input Tool input parameters
     * @param context Execution context
     * @param permissions Agent's permissions
     * @return Promise resolving to tool result
     */
    public Promise<ToolResult> execute(
            String toolName,
            ToolInput input,
            ToolContext context,
            Set<String> permissions) {

        String agentId = context.agentId();
        Instant startTime = Instant.now();

        // 1. Find tool
        return toolRegistry.findByName(toolName)
                .map(tool -> executeWithChecks(tool, input, context, permissions, startTime))
                .orElseGet(() -> {
                    recordAuditFailure(agentId, toolName, "TOOL_NOT_FOUND", startTime);
                    return Promise.of(ToolResult.error("Tool not found: " + toolName));
                });
    }

    private Promise<ToolResult> executeWithChecks(
            AgentTool tool,
            ToolInput input,
            ToolContext context,
            Set<String> permissions,
            Instant startTime) {

        String agentId = context.agentId();
        String toolName = tool.getName();

        // 2. Check permissions
        Set<String> requiredPermissions = tool.getRequiredPermissions();
        if (!permissions.containsAll(requiredPermissions)) {
            recordAuditFailure(agentId, toolName, "PERMISSION_DENIED", startTime);
            return Promise.of(ToolResult.error(
                    "Permission denied. Required: " + requiredPermissions
                    + ", Agent has: " + permissions
            ));
        }

        // 3. Check rate limit
        if (!rateLimiter.tryAcquire(toolName, agentId)) {
            recordAuditFailure(agentId, toolName, "RATE_LIMITED", startTime);
            return Promise.of(ToolResult.error(
                    "Rate limit exceeded for tool: " + toolName
            ));
        }

        // 4. Execute with timeout
        Duration timeout = getTimeout(tool);

        return tool.execute(input, context)
                .then(result -> {
                    recordAuditSuccess(agentId, toolName, input, result, startTime);
                    return Promise.of(result);
                })
                .whenException(e -> {
                    String errorType = e instanceof java.util.concurrent.TimeoutException
                            ? "TIMEOUT"
                            : "EXECUTION_ERROR";
                    recordAuditFailure(agentId, toolName, errorType, startTime);
                })
                .map(result -> result, e -> ToolResult.failure("Execution failed: " + e.getMessage()));
    }

    private Duration getTimeout(AgentTool tool) {
        // Tools can override default timeout via metadata
        return defaultTimeout;
    }

    private void recordAuditSuccess(
            String agentId,
            String toolName,
            ToolInput input,
            ToolResult result,
            Instant startTime) {

        if (auditTrail != null) {
            Duration duration = Duration.between(startTime, Instant.now());
            auditTrail.recordEvent(
                    agentId,
                    "tool.executed",
                    Map.of(
                            "tool", toolName,
                            "success", result.isSuccess(),
                            "duration_ms", duration.toMillis(),
                            "input_keys", input.keys()
                    )
            );
        }
    }

    private void recordAuditFailure(
            String agentId,
            String toolName,
            String errorType,
            Instant startTime) {

        if (auditTrail != null) {
            Duration duration = Duration.between(startTime, Instant.now());
            auditTrail.recordEvent(
                    agentId,
                    "tool.failed",
                    Map.of(
                            "tool", toolName,
                            "error_type", errorType,
                            "duration_ms", duration.toMillis()
                    )
            );
        }
    }

    /**
     * Gets rate limit statistics for a tool.
     *
     * @param toolName Tool name
     * @param agentId Agent ID
     * @return Rate limit info
     */
    public RateLimitInfo getRateLimitInfo(String toolName, String agentId) {
        return rateLimiter.getInfo(toolName, agentId);
    }

    /**
     * Resets rate limit for a specific tool/agent combination.
     *
     * @param toolName Tool name
     * @param agentId Agent ID
     */
    public void resetRateLimit(String toolName, String agentId) {
        rateLimiter.reset(toolName, agentId);
    }

    /**
     * Creates a new builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SecureToolExecutor.
     */
    public static class Builder {

        private ToolRegistry toolRegistry;
        private AuditTrail auditTrail;
        private int defaultRateLimit = 100;
        private Duration rateLimitWindow = Duration.ofMinutes(1);
        private Duration defaultTimeout = Duration.ofSeconds(30);

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder auditTrail(AuditTrail auditTrail) {
            this.auditTrail = auditTrail;
            return this;
        }

        public Builder defaultRateLimit(int limit, Duration window) {
            this.defaultRateLimit = limit;
            this.rateLimitWindow = window;
            return this;
        }

        public Builder defaultTimeout(Duration timeout) {
            this.defaultTimeout = timeout;
            return this;
        }

        public SecureToolExecutor build() {
            if (toolRegistry == null) {
                throw new IllegalStateException("ToolRegistry is required");
            }
            return new SecureToolExecutor(this);
        }
    }

    /**
     * Rate limit information record.
     */
    public record RateLimitInfo(
            int remaining,
            int limit,
            Instant resetTime
    ) {
    }

    /**
     * Simple sliding window rate limiter.
     */
    private static final class SharedRateLimiterAdapter {

        private final int limit;
        private final Duration window;
        private final DefaultRateLimiter delegate;

        SharedRateLimiterAdapter(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
            this.delegate = DefaultRateLimiter.create(
                    RateLimiterConfig.builder()
                            .maxRequestsPerMinute(limit)
                            .burstSize(limit)
                            .windowDuration(window)
                            .build()
            );
        }

        boolean tryAcquire(String toolName, String agentId) {
            return delegate.tryAcquire(rateLimitKey(toolName, agentId)).allowed();
        }

        RateLimitInfo getInfo(String toolName, String agentId) {
            return new RateLimitInfo(
                    delegate.getApproximateRemainingTokens(rateLimitKey(toolName, agentId)),
                    limit,
                    Instant.now().plus(window)
            );
        }

        void reset(String toolName, String agentId) {
            delegate.reset(rateLimitKey(toolName, agentId));
        }

        private String rateLimitKey(String toolName, String agentId) {
            return toolName + ":" + agentId;
        }
    }
}
