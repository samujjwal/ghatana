/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

/**
 * Tracing instrumentation for governance operations.
 *
 * Provides span creation and trace context propagation for governance actions
 * such as kill switch activation, degradation mode changes, and compliance checks.
 *
 * @doc.type class
 * @doc.purpose Distributed tracing instrumentation for governance operations
 * @doc.layer product
 * @doc.pattern Instrumentation
 */
public final class GovernanceTracingInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(GovernanceTracingInstrumentation.class);

    private GovernanceTracingInstrumentation() {
        // Utility class
    }

    /**
     * Creates a span for kill switch activation.
     */
    public static GovernanceSpan createKillSwitchSpan(
        String tenantId,
        String reason,
        String incidentId,
        String operator
    ) {
        GovernanceSpan span = new GovernanceSpan(
            "aep.governance.kill-switch.activate",
            Map.of(
                "tenant.id", tenantId,
                "incident.id", incidentId,
                "reason", reason,
                "operator", operator
            )
        );
        
        span.event("kill-switch.activation.started", Map.of(
            "timestamp", Instant.now().toString()
        ));
        log.debug("Created kill switch activation span: tenantId={}, incidentId={}", tenantId, incidentId);
        
        return span;
    }

    /**
     * Creates a span for kill switch deactivation.
     */
    public static GovernanceSpan createKillSwitchDeactivationSpan(
        String tenantId,
        String reason,
        String operator
    ) {
        GovernanceSpan span = new GovernanceSpan(
            "aep.governance.kill-switch.deactivate",
            Map.of(
                "tenant.id", tenantId,
                "reason", reason,
                "operator", operator
            )
        );
        
        span.event("kill-switch.deactivation.started", Map.of(
            "timestamp", Instant.now().toString()
        ));
        log.debug("Created kill switch deactivation span: tenantId={}", tenantId);
        
        return span;
    }

    /**
     * Creates a span for degradation mode change.
     */
    public static GovernanceSpan createDegradationSpan(
        String tenantId,
        String fromMode,
        String toMode,
        String reason
    ) {
        GovernanceSpan span = new GovernanceSpan(
            "aep.governance.degradation.change",
            Map.of(
                "tenant.id", tenantId,
                "from.mode", fromMode,
                "to.mode", toMode,
                "reason", reason
            )
        );
        
        span.event("degradation.change.started", Map.of(
            "timestamp", Instant.now().toString()
        ));
        log.debug("Created degradation mode change span: tenantId={}, from={}, to={}", 
            tenantId, fromMode, toMode);
        
        return span;
    }

    /**
     * Creates a span for compliance check.
     */
    public static GovernanceSpan createComplianceSpan(
        String tenantId,
        String complianceType,
        String policyId
    ) {
        GovernanceSpan span = new GovernanceSpan(
            "aep.governance.compliance.check",
            Map.of(
                "tenant.id", tenantId,
                "compliance.type", complianceType,
                "policy.id", policyId
            )
        );
        
        span.event("compliance.check.started", Map.of(
            "timestamp", Instant.now().toString()
        ));
        log.debug("Created compliance check span: tenantId={}, type={}", tenantId, complianceType);
        
        return span;
    }

    /**
     * Records kill switch activation in span.
     */
    public static void recordKillSwitchActivated(
        GovernanceSpan span,
        boolean activated,
        String previousState
    ) {
        span.attribute("activated", activated);
        span.attribute("previous.state", previousState);
        
        span.event("kill-switch.activated", Map.of(
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Records degradation mode change in span.
     */
    public static void recordDegradationChanged(
        GovernanceSpan span,
        boolean changed,
        String currentState
    ) {
        span.attribute("changed", changed);
        span.attribute("current.state", currentState);
        
        span.event("degradation.changed", Map.of(
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Records compliance check result in span.
     */
    public static void recordComplianceResult(
        GovernanceSpan span,
        boolean compliant,
        String violationType,
        int violationCount
    ) {
        span.attribute("compliant", compliant);
        span.attribute("violation.type", violationType);
        span.attribute("violation.count", violationCount);
        
        span.event("compliance.check.completed", Map.of(
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Governance span implementation.
     */
    public static class GovernanceSpan implements Span {
        private final String name;
        private final Map<String, Object> attributes;
        private final Map<String, Object> events = new java.util.LinkedHashMap<>();
        private String status = "OK";
        private String statusDescription;
        private boolean ended = false;

        GovernanceSpan(String name, Map<String, Object> attributes) {
            this.name = name;
            this.attributes = new java.util.HashMap<>(attributes);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.copyOf(attributes);
        }

        @Override
        public void attribute(String key, Object value) {
            if (!ended) {
                attributes.put(key, value);
            }
        }

        @Override
        public void event(String eventName, Map<String, Object> attributes) {
            if (!ended) {
                events.put(eventName, Map.copyOf(attributes));
            }
        }

        @Override
        public void recordException(Exception error) {
            if (!ended) {
                attribute("error.type", error.getClass().getSimpleName());
                attribute("error.message", error.getMessage());
                attribute("error.stacktrace", java.util.Arrays.stream(error.getStackTrace())
                    .limit(10)
                    .map(StackTraceElement::toString)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(""));
            }
        }

        @Override
        public void status(String status, String description) {
            if (!ended) {
                this.status = status;
                this.statusDescription = description;
            }
        }

        @Override
        public void end() {
            if (!ended) {
                ended = true;
                log.debug("Governance span ended: name={}, status={}", name, status);
                // In production, this would export to tracing backend
            }
        }

        @Override
        public boolean isEnded() {
            return ended;
        }

        public Map<String, Object> getEvents() {
            return Map.copyOf(events);
        }
    }

    /**
     * Span interface.
     */
    public interface Span {
        String getName();
        Map<String, Object> getAttributes();
        void attribute(String key, Object value);
        void event(String eventName, Map<String, Object> attributes);
        void recordException(Exception error);
        void status(String status, String description);
        void end();
        boolean isEnded();
    }
}
