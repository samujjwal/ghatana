package com.ghatana.agent.framework.api;

import com.ghatana.agent.framework.memory.MemoryStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of AgentContext.
 * Thread-safe and immutable once built.
 * 
 * @doc.type class
 * @doc.purpose Default AgentContext implementation
 * @doc.layer framework
 * @doc.pattern Context Object
 */
public final class DefaultAgentContext implements AgentContext {
    
    private final String turnId;
    private final String agentId;
    private final String tenantId;
    private final String userId;
    private final String sessionId;
    private final String traceId;
    private final Instant startTime;
    private final MemoryStore memoryStore;
    private final Logger logger;
    private final Map<String, Object> config;
    private final Map<String, String> traceTags;
    private final Map<String, Double> metrics;
    private final ConcurrentHashMap<String, Object> metadata;
    private volatile Double remainingBudget;
    
    private DefaultAgentContext(BuilderImpl builder) {
        this.turnId = Objects.requireNonNull(builder.turnId, "turnId cannot be null");
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId cannot be null");
        this.userId = builder.userId;
        this.sessionId = builder.sessionId;
        this.traceId = builder.traceId;
        this.startTime = Objects.requireNonNull(builder.startTime, "startTime cannot be null");
        this.memoryStore = Objects.requireNonNull(builder.memoryStore, "memoryStore cannot be null");
        this.logger = builder.logger != null ? builder.logger : LoggerFactory.getLogger(agentId);
        this.config = Collections.unmodifiableMap(new HashMap<>(builder.config));
        this.traceTags = new ConcurrentHashMap<>();
        this.metrics = new ConcurrentHashMap<>();
        this.metadata = builder.metadata != null
                ? new ConcurrentHashMap<>(builder.metadata)
                : new ConcurrentHashMap<>();
        this.remainingBudget = builder.remainingBudget;
    }
    
    @Override
    @NotNull
    public String getTurnId() {
        return turnId;
    }
    
    @Override
    @NotNull
    public String getAgentId() {
        return agentId;
    }
    
    @Override
    @NotNull
    public String getTenantId() {
        return tenantId;
    }
    
    @Override
    @Nullable
    public String getUserId() {
        return userId;
    }
    
    @Override
    @Nullable
    public String getSessionId() {
        return sessionId;
    }
    
    @Override
    @NotNull
    public Instant getStartTime() {
        return startTime;
    }
    
    @Override
    @NotNull
    public MemoryStore getMemoryStore() {
        return memoryStore;
    }
    
    @Override
    @NotNull
    public Logger getLogger() {
        return logger;
    }
    
    @Override
    @Nullable
    public Object getConfig(@NotNull String key) {
        return config.get(key);
    }
    
    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T getConfigOrDefault(@NotNull String key, @NotNull T defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException ex) {
            logger.warn("Config value for '{}' cannot be cast to {}, using default", 
                key, defaultValue.getClass().getSimpleName());
            return defaultValue;
        }
    }
    
    @Override
    @NotNull
    public Map<String, Object> getAllConfig() {
        return config;
    }
    
    @Override
    public void recordMetric(@NotNull String name, double value) {
        Objects.requireNonNull(name, "metric name cannot be null");
        metrics.put(name, value);
        logger.debug("Metric recorded: {} = {}", name, value);
    }
    
    @Override
    public void recordMetric(@NotNull String name, double value, @NotNull Map<String, String> tags) {
        recordMetric(name, value);
        // In production, send to metrics system with tags
        logger.debug("Metric recorded: {} = {} with tags {}", name, value, tags);
    }
    
    @Override
    public void addTraceTag(@NotNull String key, @NotNull String value) {
        Objects.requireNonNull(key, "tag key cannot be null");
        Objects.requireNonNull(value, "tag value cannot be null");
        traceTags.put(key, value);
        logger.debug("Trace tag added: {} = {}", key, value);
    }
    
    @Override
    @Nullable
    public Double getRemainingBudget() {
        return remainingBudget;
    }
    
    @Override
    public synchronized void deductCost(double cost) throws BudgetExceededException {
        if (remainingBudget == null) {
            // No budget limit
            return;
        }
        
        if (cost > remainingBudget) {
            throw new BudgetExceededException(cost, remainingBudget);
        }
        
        remainingBudget -= cost;
        recordMetric("agent.cost.deducted", cost);
        recordMetric("agent.budget.remaining", remainingBudget);
        logger.debug("Cost deducted: ${}, remaining: ${}", cost, remainingBudget);
    }
    
    @Override
    @NotNull
    public Builder toBuilder() {
        return new BuilderImpl(this);
    }
    
    /**
     * Gets recorded metrics for testing/debugging.
     * @return Metrics map
     */
    @NotNull
    public Map<String, Double> getMetrics() {
        return new HashMap<>(metrics);
    }
    
    /**
     * Gets trace tags for testing/debugging.
     * @return Trace tags map
     */
    @NotNull
    public Map<String, String> getTraceTags() {
        return new HashMap<>(traceTags);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // v2.0 additions
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Nullable
    public String getTraceId() {
        return traceId;
    }

    @Override
    @NotNull
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(@NotNull String key, @NotNull Object value) {
        Objects.requireNonNull(key, "metadata key cannot be null");
        Objects.requireNonNull(value, "metadata value cannot be null");
        metadata.put(key, value);
    }

    @Override
    @NotNull
    public AgentContext deriveChild(@NotNull String childAgentId) {
        return new BuilderImpl(this)
                .turnId(UUID.randomUUID().toString())
                .agentId(childAgentId)
                .startTime(Instant.now())
                .build();
    }
    
    /**
     * Builder implementation.
     */
    static final class BuilderImpl implements Builder {
        private String turnId;
        private String agentId;
        private String tenantId;
        private String userId;
        private String sessionId;
        private String traceId;
        private Instant startTime;
        private MemoryStore memoryStore;
        private Logger logger;
        private Map<String, Object> config;
        private Double remainingBudget;
        private Map<String, Object> metadata;
        
        BuilderImpl() {
            this.turnId = UUID.randomUUID().toString();
            this.startTime = Instant.now();
            this.config = new HashMap<>();
        }
        
        BuilderImpl(DefaultAgentContext context) {
            this.turnId = context.turnId;
            this.agentId = context.agentId;
            this.tenantId = context.tenantId;
            this.userId = context.userId;
            this.sessionId = context.sessionId;
            this.traceId = context.traceId;
            this.startTime = context.startTime;
            this.memoryStore = context.memoryStore;
            this.logger = context.logger;
            this.config = new HashMap<>(context.config);
            this.remainingBudget = context.remainingBudget;
            this.metadata = new HashMap<>(context.metadata);
        }
        
        @Override
        @NotNull
        public Builder turnId(@NotNull String turnId) {
            this.turnId = turnId;
            return this;
        }
        
        @Override
        @NotNull
        public Builder agentId(@NotNull String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        @Override
        @NotNull
        public Builder tenantId(@NotNull String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        @Override
        @NotNull
        public Builder userId(@Nullable String userId) {
            this.userId = userId;
            return this;
        }
        
        @Override
        @NotNull
        public Builder sessionId(@Nullable String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        @Override
        @NotNull
        public Builder startTime(@NotNull Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        @Override
        @NotNull
        public Builder memoryStore(@NotNull MemoryStore memoryStore) {
            this.memoryStore = memoryStore;
            return this;
        }
        
        @Override
        @NotNull
        public Builder logger(@NotNull Logger logger) {
            this.logger = logger;
            return this;
        }
        
        @Override
        @NotNull
        public Builder config(@NotNull Map<String, Object> config) {
            this.config = new HashMap<>(config);
            return this;
        }
        
        @Override
        @NotNull
        public Builder addConfig(@NotNull String key, @NotNull Object value) {
            if (this.config == null) {
                this.config = new HashMap<>();
            }
            this.config.put(key, value);
            return this;
        }
        
        @Override
        @NotNull
        public Builder remainingBudget(@Nullable Double budget) {
            this.remainingBudget = budget;
            return this;
        }

        @Override
        @NotNull
        public Builder traceId(@Nullable String traceId) {
            this.traceId = traceId;
            return this;
        }

        @Override
        @NotNull
        public Builder metadata(@Nullable Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }
        
        @Override
        @NotNull
        public AgentContext build() {
            return new DefaultAgentContext(this);
        }
    }
}
