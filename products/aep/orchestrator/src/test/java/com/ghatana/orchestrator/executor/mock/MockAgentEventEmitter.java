/*
 * Copyright (c) 2024 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor.mock;

import com.ghatana.orchestrator.executor.AgentEventEmitter;
import com.ghatana.orchestrator.executor.model.AgentStepResult;
import io.activej.promise.Promise;

/**
 * Day 40: Mock event emitter for testing without proto dependencies.
 *
 * @doc.type class
 * @doc.purpose Mock event emitter for testing agent execution
 * @doc.layer product
 * @doc.pattern TestDouble
 */
public class MockAgentEventEmitter extends AgentEventEmitter {

    public MockAgentEventEmitter() { // GH-90000
        super(new MockEventLogClient(), "test-tenant"); // GH-90000
    }

    @Override
    public Promise<Void> emitStepResult(AgentStepResult result) { // GH-90000
        // Mock implementation - just return completed promise
        return Promise.complete(); // GH-90000
    }

    private static class MockEventLogClient implements EventLogClient {
        @Override
        public Promise<Void> publishEvent(String tenantId, Object event) { // GH-90000
            return Promise.complete(); // GH-90000
        }
    }
}
