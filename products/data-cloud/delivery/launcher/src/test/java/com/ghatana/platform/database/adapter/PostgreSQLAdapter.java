package com.ghatana.platform.database.adapter;

import io.activej.promise.Promise;

/**
 * Test-scope compatibility shim for legacy launcher resilience/atomic tests.
 */
public interface PostgreSQLAdapter {
    Promise<Object> executeQuery(String statement, Object[] params);

    Promise<Object> executeWrite(String statement, Object[] params);

    Promise<Void> executeRollback(String resource);

    void executeCompensation(String step);
}
