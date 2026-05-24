package com.ghatana.phr.kernel.event;

import java.time.Instant;
import java.util.Objects;

/**
 * PHR lifecycle event for tracking product lifecycle state changes.
 *
 * <p>This event is published when PHR transitions between lifecycle phases
 * (validate, test, build, package, deploy, verify). It enables cross-product
 * coordination and evidence collection for regulated healthcare deployments.</p>
 *
 * <p>This class maps to the canonical contract {@link com.ghatana.contracts.events.PhrEventContracts.PhrLifecyclePhaseEvent}
 * in the platform contracts layer. The canonical contract is the single source of truth
 * for the wire format.</p>
 *
 * @doc.type class
 * @doc.purpose PHR lifecycle event contract
 * @doc.layer product
 * @doc.pattern Domain Event
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public final class PhrLifecycleEvent {

    private final String eventId;
    private final String productId;
    private final String phase;
    private final String status;
    private final String runId;
    private final String correlationId;
    private final String environment;
    private final Instant timestamp;
    private final String tenantId;

    private PhrLifecycleEvent(Builder builder) {
        this.eventId = Objects.requireNonNull(builder.eventId, "eventId must not be null");
        this.productId = Objects.requireNonNull(builder.productId, "productId must not be null");
        this.phase = Objects.requireNonNull(builder.phase, "phase must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.runId = Objects.requireNonNull(builder.runId, "runId must not be null");
        this.correlationId = Objects.requireNonNull(builder.correlationId, "correlationId must not be null");
        this.environment = Objects.requireNonNull(builder.environment, "environment must not be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp must not be null");
        this.tenantId = builder.tenantId; // Optional for system-level events
    }

    public String eventId() {
        return eventId;
    }

    public String productId() {
        return productId;
    }

    public String phase() {
        return phase;
    }

    public String status() {
        return status;
    }

    public String runId() {
        return runId;
    }

    public String correlationId() {
        return correlationId;
    }

    public String environment() {
        return environment;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String tenantId() {
        return tenantId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String eventId;
        private String productId = "phr";
        private String phase;
        private String status;
        private String runId;
        private String correlationId;
        private String environment;
        private Instant timestamp;
        private String tenantId;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder phase(String phase) {
            this.phase = phase;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public PhrLifecycleEvent build() {
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (eventId == null) {
                eventId = "phr-lifecycle-" + phase + "-" + System.currentTimeMillis();
            }
            return new PhrLifecycleEvent(this);
        }
    }
}
