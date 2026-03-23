package com.ghatana.agent.registry.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Legacy placeholder implementation retained for compatibility. All runtime and
 * gRPC logic has moved to the service package. This class is intentionally
 * minimal to avoid conflicting legacy proto usages and to keep the module compiling.
 *
 * @doc.type class
 * @doc.purpose Legacy placeholder for agent registry gRPC service retained for backward compatibility
 * @doc.layer product
 * @doc.pattern Service
 */
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class AgentRegistryServiceImpl {
    private static final Logger log = LoggerFactory.getLogger(AgentRegistryServiceImpl.class);
}
