package com.ghatana.platform.policyascode;

import io.activej.promise.Promise;

/**
 * Test-scope compatibility shim for legacy launcher resilience tests.
 */
public interface PolicyAsCodeEngine {
    Promise<Boolean> evaluate(String resource, String action);
}
