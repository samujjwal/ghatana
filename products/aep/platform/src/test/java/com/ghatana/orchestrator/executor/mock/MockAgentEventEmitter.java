/*
 * Copyright (c) 2024 Ghatana Inc.
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

    public MockAgentEventEmitter() {
        super(new MockEventLogClient(), "test-tenant");
    }

    @Override
    public Promise<Void> emitStepResult(AgentStepResult result) {
        // Mock implementation - just return completed promise
        return Promise.complete();
    }

    private static class MockEventLogClient implements EventLogClient {
        @Override
        public Promise<Void> publishEvent(String tenantId, Object event) {
            return Promise.complete();
        }
    }
}