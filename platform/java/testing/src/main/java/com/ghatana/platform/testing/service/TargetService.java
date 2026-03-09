package com.ghatana.platform.testing.service;

/**
 * Canonical enumeration of EventCloud services that can be targeted by test harnesses
 * (chaos, performance, validation, etc).
 * 
 * <p>Moved from multi-agent-system:shared:common to core:testing:test-utils
 * as it's test-related infrastructure.
 
 *
 * @doc.type enum
 * @doc.purpose Target service
 * @doc.layer core
 * @doc.pattern Service
*/
public enum TargetService {
    VALIDATION("validation"),
    PATTERN_ENGINE("pattern-engine"),
    ORCHESTRATOR("orchestrator"),
    PATTERN_LEARNING("pattern-learning"),
    EVENTLOG("eventlog"),
    CATALOG("catalog"),
    INGRESS("ingress"),
    QUERY("query"),
    STATE_STORE("state-store"),
    PLANNER("planner"),
    SECURITY("security"),
    ALL_SERVICES("all");

    private final String serviceName;

    TargetService(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
