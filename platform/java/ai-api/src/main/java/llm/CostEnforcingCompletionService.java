package com.ghatana.ai.llm;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decorator that enforces per-tenant cost budgets around any {@link CompletionService}.
 *
 * <p>Before each completion request the decorator checks that the tenant has not exceeded
 * its configured monthly token budget. After each successful call it records the actual
 * token consumption. Requests that would exceed the budget are rejected immediately
 * with a descriptive error.</p>
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * CompletionService raw = new OpenAICompletionService(config);
 * CostEnforcingCompletionService enforced = new CostEnforcingCompletionService(raw);
 * enforced.setTenantBudget("tenant-123", 1_000_000); // 1M tokens/month
 *
 * // This call will be rejected when budget is exhausted:
 * Promise<CompletionResult> result = enforced.complete(request, "tenant-123");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Per-tenant AI cost enforcement decorator
 * @doc.layer platform
 * @doc.pattern Decorator
 */
public class CostEnforcingCompletionService implements CompletionService {

    private static final Logger log = LoggerFactory.getLogger(CostEnforcingCompletionService.class);

    /** Default monthly token budget per tenant when none is configured (10M tokens). */
    private static final long DEFAULT_BUDGET_TOKENS = 10_000_000L;

    private final CompletionService delegate;
    private final ConcurrentHashMap<String, Long> tenantBudgets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> tenantUsage = new ConcurrentHashMap<>();

    /**
     * Wrap the given completion service with cost enforcement.
     *
     * @param delegate the underlying completion service
     */
    public CostEnforcingCompletionService(CompletionService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * Set the monthly token budget for a tenant.
     *
     * @param tenantId tenant identifier
     * @param maxTokens maximum total tokens (prompt + completion) per month
     * @throws IllegalArgumentException if maxTokens is non-positive
     */
    public void setTenantBudget(String tenantId, long maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("Budget must be positive, got: " + maxTokens);
        }
        tenantBudgets.put(tenantId, maxTokens);
        log.info("Set token budget for tenant {}: {} tokens", tenantId, maxTokens);
    }

    /**
     * Get current token usage for a tenant.
     *
     * @param tenantId tenant identifier
     * @return tokens consumed in current period
     */
    public long getUsage(String tenantId) {
        AtomicLong usage = tenantUsage.get(tenantId);
        return usage != null ? usage.get() : 0L;
    }

    /**
     * Get remaining budget for a tenant.
     *
     * @param tenantId tenant identifier
     * @return remaining tokens in budget
     */
    public long getRemainingBudget(String tenantId) {
        long budget = tenantBudgets.getOrDefault(tenantId, DEFAULT_BUDGET_TOKENS);
        long used = getUsage(tenantId);
        return Math.max(0, budget - used);
    }

    /**
     * Reset usage counters for all tenants (call at start of new billing period).
     */
    public void resetAllUsage() {
        tenantUsage.clear();
        log.info("Reset usage counters for all tenants");
    }

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        String tenantId = extractTenantId(request);
        long budget = tenantBudgets.getOrDefault(tenantId, DEFAULT_BUDGET_TOKENS);
        long currentUsage = getUsage(tenantId);

        // Pre-flight check: reject if already over budget
        if (currentUsage >= budget) {
            log.warn("Tenant {} exceeded token budget ({}/{})", tenantId, currentUsage, budget);
            return Promise.ofException(new BudgetExceededException(
                    "Tenant " + tenantId + " has exceeded its token budget (" + currentUsage + "/" + budget + ")"));
        }

        // Pre-flight check: reject if maxTokens would certainly exceed budget
        long headroom = budget - currentUsage;
        if (request.getMaxTokens() > headroom) {
            log.warn("Tenant {} request maxTokens ({}) exceeds remaining budget ({})",
                    tenantId, request.getMaxTokens(), headroom);
            return Promise.ofException(new BudgetExceededException(
                    "Requested maxTokens (" + request.getMaxTokens() + ") exceeds remaining budget (" + headroom + ")"));
        }

        return delegate.complete(request)
                .map(result -> {
                    recordUsage(tenantId, result);
                    return result;
                });
    }

    @Override
    public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
        // For batch, check total maxTokens against budget
        if (!requests.isEmpty()) {
            String tenantId = extractTenantId(requests.get(0));
            long budget = tenantBudgets.getOrDefault(tenantId, DEFAULT_BUDGET_TOKENS);
            long currentUsage = getUsage(tenantId);
            long totalMaxTokens = requests.stream().mapToLong(CompletionRequest::getMaxTokens).sum();

            if (currentUsage + totalMaxTokens > budget) {
                return Promise.ofException(new BudgetExceededException(
                        "Batch for tenant " + tenantId + " would exceed token budget"));
            }
        }

        return delegate.completeBatch(requests)
                .map(results -> {
                    if (!requests.isEmpty()) {
                        String tenantId = extractTenantId(requests.get(0));
                        for (CompletionResult result : results) {
                            recordUsage(tenantId, result);
                        }
                    }
                    return results;
                });
    }

    @Override
    public LLMConfiguration getConfig() {
        return delegate.getConfig();
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return delegate.getMetricsCollector();
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    // ---- internal helpers ----

    private void recordUsage(String tenantId, CompletionResult result) {
        long tokens = result.getTokensUsed();
        if (tokens <= 0) {
            tokens = result.getPromptTokens() + result.getCompletionTokens();
        }
        if (tokens > 0) {
            AtomicLong usage = tenantUsage.computeIfAbsent(tenantId, k -> new AtomicLong(0));
            long newTotal = usage.addAndGet(tokens);
            long budget = tenantBudgets.getOrDefault(tenantId, DEFAULT_BUDGET_TOKENS);

            // Warn at 80% and 95% thresholds
            double utilisation = (double) newTotal / budget;
            if (utilisation >= 0.95) {
                log.warn("Tenant {} at {:.1f}% of token budget ({}/{})", tenantId,
                        utilisation * 100, newTotal, budget);
            } else if (utilisation >= 0.80) {
                log.info("Tenant {} at {:.1f}% of token budget ({}/{})", tenantId,
                        utilisation * 100, newTotal, budget);
            }
        }
    }

    private String extractTenantId(CompletionRequest request) {
        Map<String, Object> meta = request.getMetadata();
        if (meta != null && meta.containsKey("tenantId")) {
            return String.valueOf(meta.get("tenantId"));
        }
        return "default";
    }

    /**
     * Exception thrown when a tenant exceeds their AI token budget.
     *
     * @doc.type class
     * @doc.purpose Budget enforcement exception
     * @doc.layer platform
     * @doc.pattern Exception
     */
    public static class BudgetExceededException extends RuntimeException {
        /**
         * @param message descriptive budget error message
         */
        public BudgetExceededException(String message) {
            super(message);
        }
    }
}
