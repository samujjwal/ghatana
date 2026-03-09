/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.domain.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An enriched audit event capturing who-what-when-where with typed details,
 * supporting Jackson YAML/JSON serialization and an immutable builder pattern.
 *
 * <p>Required fields ({@code tenantId}, {@code eventType}, {@code timestamp})
 * are enforced during Jackson deserialization; null or missing values produce
 * a {@link com.fasterxml.jackson.databind.exc.MismatchedInputException}.
 *
 * @see AuditEntry
 * @see AuditTrail
 *
 * @doc.type class
 * @doc.purpose Immutable enriched audit event with serialization support
 * @doc.layer domain
 * @doc.pattern ValueObject
 */
@JsonDeserialize(builder = AuditEvent.Builder.class)
public class AuditEvent {

    private final String tenantId;
    private final String eventType;
    private final String principal;
    private final Instant timestamp;
    private final Map<String, Object> details;
    private final Boolean success;
    private final String resourceId;
    private final String resourceType;

    private AuditEvent(Builder builder) {
        this.tenantId = builder.tenantId;
        this.eventType = builder.eventType;
        this.principal = builder.principal;
        this.timestamp = builder.timestamp;
        this.details = builder.details != null
                ? new LinkedHashMap<>(builder.details)
                : new LinkedHashMap<>();
        this.success = builder.success;
        this.resourceId = builder.resourceId;
        this.resourceType = builder.resourceType;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getTenantId() {
        return tenantId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPrincipal() {
        return principal;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    /**
     * Returns the detail value for the given key, cast to the inferred type.
     *
     * @param key the detail key
     * @param <T> the expected value type
     * @return the value, or {@code null} if the key is absent
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key) {
        return (T) details.get(key);
    }

    /**
     * Returns the detail value for the given key, or a default if absent.
     *
     * @param key          the detail key
     * @param defaultValue the fallback value
     * @param <T>          the expected value type
     * @return the value, or {@code defaultValue} if the key is absent
     */
    @SuppressWarnings("unchecked")
    public <T> T getDetail(String key, T defaultValue) {
        Object value = details.get(key);
        return value != null ? (T) value : defaultValue;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new builder pre-populated with this event's values,
     * allowing immutable modification.
     */
    public Builder toBuilder() {
        return new Builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .principal(principal)
                .timestamp(timestamp)
                .details(new LinkedHashMap<>(details))
                .success(success)
                .resourceId(resourceId)
                .resourceType(resourceType);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link AuditEvent}. Required fields are enforced
     * during Jackson deserialization via {@code @JsonProperty(required = true)}
     * and {@code @JsonSetter(nulls = Nulls.FAIL)}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        private String tenantId;
        private String eventType;
        private String principal;
        private Instant timestamp;
        private Map<String, Object> details = new LinkedHashMap<>();
        private Boolean success;
        private String resourceId;
        private String resourceType;

        @JsonProperty(required = true)
        @JsonSetter(nulls = Nulls.FAIL)
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        @JsonProperty(required = true)
        @JsonSetter(nulls = Nulls.FAIL)
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        @JsonProperty(required = true)
        @JsonSetter(nulls = Nulls.FAIL)
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details != null
                    ? new LinkedHashMap<>(details)
                    : new LinkedHashMap<>();
            return this;
        }

        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        public Builder success(Boolean success) {
            this.success = success;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
