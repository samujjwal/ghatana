/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Execution context passed to every agent invocation.
 *
 * <p>Provides turn identity, tenant isolation, memory access, and
 * observability hooks without coupling agents to infrastructure.
 *
 * @doc.type interface
 * @doc.purpose Agent execution context contract
 * @doc.layer core
 * @doc.pattern Contract
 */
public interface AgentContext {

    String getTurnId();

    String getAgentId();

    String getTenantId();

    Optional<String> getUserId();

    Optional<String> getSessionId();

    Instant getStartTime();

    Object getConfig(String key);

    <T> T getConfigOrDefault(String key, T defaultValue);

    Map<String, Object> getAllConfig();

    void recordMetric(String name, double value);
}
