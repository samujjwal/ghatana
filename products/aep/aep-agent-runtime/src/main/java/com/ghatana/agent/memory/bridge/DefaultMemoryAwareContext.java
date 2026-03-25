package com.ghatana.agent.memory.bridge;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.DefaultAgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.persistence.MemoryStoreAdapter;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementation of {@link MemoryAwareContext} that wraps a standard
 * {@link AgentContext} and adds memory plane access.
 *
 * <p>Also exposes the legacy {@code MemoryStore} via a backward-compat adapter.
 *
 * @doc.type class
 * @doc.purpose Memory plane-aware agent context implementation
 * @doc.layer agent-memory
 */
public class DefaultMemoryAwareContext implements MemoryAwareContext {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryAwareContext.class);

    private final AgentContext delegate;
    private final MemoryPlane memoryPlane;
    private final MemoryStore legacyAdapter;

    private DefaultMemoryAwareContext(AgentContext delegate, MemoryPlane memoryPlane) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
        this.legacyAdapter = new MemoryStoreAdapter(memoryPlane);
    }

    /**
     * Wraps an existing context with memory plane support.
     *
     * @param context Base context
     * @param memoryPlane Memory plane to inject
     * @return Memory-aware context
     */
    @NotNull
    public static DefaultMemoryAwareContext wrap(
            @NotNull AgentContext context,
            @NotNull MemoryPlane memoryPlane) {
        return new DefaultMemoryAwareContext(context, memoryPlane);
    }

    // ========== MemoryAwareContext ==========

    @Override
    @NotNull
    public MemoryPlane getMemoryPlane() {
        return memoryPlane;
    }

    // ========== AgentContext delegation ==========

    @Override @NotNull public String getTurnId() { return delegate.getTurnId(); }
    @Override @NotNull public String getAgentId() { return delegate.getAgentId(); }
    @Override @NotNull public String getTenantId() { return delegate.getTenantId(); }
    @Override @Nullable public String getUserId() { return delegate.getUserId(); }
    @Override @Nullable public String getSessionId() { return delegate.getSessionId(); }
    @Override @NotNull public Instant getStartTime() { return delegate.getStartTime(); }

    /**
     * Returns the legacy MemoryStore adapter backed by the MemoryPlane.
     * Existing code calling {@code context.getMemoryStore()} will transparently
     * use the new memory plane through the adapter.
     */
    @Override
    @NotNull
    public MemoryStore getMemoryStore() {
        return legacyAdapter;
    }

    @Override @NotNull public Logger getLogger() { return delegate.getLogger(); }
    @Override @Nullable public Object getConfig(@NotNull String key) { return delegate.getConfig(key); }
    @Override @NotNull public <T> T getConfigOrDefault(@NotNull String key, @NotNull T defaultValue) { return delegate.getConfigOrDefault(key, defaultValue); }
    @Override @NotNull public Map<String, Object> getAllConfig() { return delegate.getAllConfig(); }
    @Override public void recordMetric(@NotNull String name, double value) { delegate.recordMetric(name, value); }
    @Override public void recordMetric(@NotNull String name, double value, @NotNull Map<String, String> tags) { delegate.recordMetric(name, value, tags); }
    @Override public void addTraceTag(@NotNull String key, @NotNull String value) { delegate.addTraceTag(key, value); }
    @Override @Nullable public Double getRemainingBudget() { return delegate.getRemainingBudget(); }
    @Override public void deductCost(double cost) throws BudgetExceededException { delegate.deductCost(cost); }
    @Override @Nullable public String getTraceId() { return delegate.getTraceId(); }
    @Override @NotNull public Map<String, Object> getMetadata() { return delegate.getMetadata(); }
    @Override public void setMetadata(@NotNull String key, @NotNull Object value) { delegate.setMetadata(key, value); }

    @Override
    @NotNull
    public AgentContext deriveChild(@NotNull String childAgentId) {
        AgentContext childBase = delegate.deriveChild(childAgentId);
        return new DefaultMemoryAwareContext(childBase, memoryPlane);
    }

    @Override
    @NotNull
    public Builder toBuilder() {
        return delegate.toBuilder();
    }
}
