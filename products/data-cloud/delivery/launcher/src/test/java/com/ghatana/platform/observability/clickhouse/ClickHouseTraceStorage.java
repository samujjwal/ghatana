package com.ghatana.platform.observability.clickhouse;

import io.activej.promise.Promise;

/**
 * Test-scope compatibility shim for launcher resilience tests.
 */
public class ClickHouseTraceStorage {
    public Promise<Void> storeTrace(String traceId, String traceData) {
        return Promise.complete();
    }
}
