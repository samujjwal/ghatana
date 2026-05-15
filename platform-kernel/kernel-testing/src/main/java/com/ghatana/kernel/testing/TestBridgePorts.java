/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.testing;

import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import io.activej.promise.Promise;

/**
 * Test-only bridge port implementations for unit and integration tests.
 *
 * @doc.type class
 * @doc.purpose Provide explicit test bridge ports without production allow-all factories
 * @doc.layer testing
 * @doc.pattern TestUtility
 */
public final class TestBridgePorts {

    private TestBridgePorts() {
    }

    public static BridgeAuthorizationService allowAllAuthorization() {
        return (context, resource, action) -> Promise.of(Boolean.TRUE);
    }

    public static BridgeAuthorizationService denyAllAuthorization() {
        return (context, resource, action) -> Promise.of(Boolean.FALSE);
    }

    public static BridgeAuditEmitter noOpAuditEmitter() {
        return event -> { };
    }

    public static BridgeHealthIndicator noOpHealthIndicator() {
        return new BridgeHealthIndicator() {
            @Override
            public void reportHealthy(String bridgeId) {
            }

            @Override
            public void reportDegraded(String bridgeId, String reason) {
            }

            @Override
            public void reportUnhealthy(String bridgeId, String reason) {
            }
        };
    }
}
