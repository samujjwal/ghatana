package com.ghatana.kernel.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Durable command for execution.
 *
 * @doc.type class
 * @doc.purpose Command domain model (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Entity
 */
public final class Command {

    private final String id;
    private final String commandType;
    private final String tenantId;
    private final Map<String, Object> payload;
    private final CommandStatus status;
    private final Instant createdAt;
    private final Instant scheduledAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String result;
    private final String error;
    private final int retryCount;
    private final int maxRetries;

    private Command(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.commandType = Objects.requireNonNull(builder.commandType, "commandType must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.payload = Map.copyOf(builder.payload);
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.scheduledAt = builder.scheduledAt;
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.result = builder.result;
        this.error = builder.error;
        this.retryCount = builder.retryCount;
        this.maxRetries = builder.maxRetries;
    }

    public String getId() { return id; }
    public String getCommandType() { return commandType; }
    public String getTenantId() { return tenantId; }
    public Map<String, Object> getPayload() { return payload; }
    public CommandStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getResult() { return result; }
    public String getError() { return error; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }

    public boolean canRetry() {
        return retryCount < maxRetries && status == CommandStatus.FAILED;
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .commandType(commandType)
            .tenantId(tenantId)
            .payload(payload)
            .status(status)
            .createdAt(createdAt)
            .scheduledAt(scheduledAt)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .result(result)
            .error(error)
            .retryCount(retryCount)
            .maxRetries(maxRetries);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String commandType;
        private String tenantId;
        private Map<String, Object> payload = Map.of();
        private CommandStatus status = CommandStatus.PENDING;
        private Instant createdAt = Instant.now();
        private Instant scheduledAt;
        private Instant startedAt;
        private Instant completedAt;
        private String result;
        private String error;
        private int retryCount = 0;
        private int maxRetries = 3;

        public Builder id(String id) { this.id = id; return this; }
        public Builder commandType(String commandType) { this.commandType = commandType; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder payload(Map<String, Object> payload) { this.payload = payload; return this; }
        public Builder status(CommandStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder scheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder result(String result) { this.result = result; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }

        public Command build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (commandType == null || commandType.isBlank()) throw new IllegalArgumentException("commandType must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            return new Command(this);
        }
    }
}
