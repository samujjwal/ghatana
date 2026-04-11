/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector;

import com.ghatana.aep.connector.config.RetryConfig;
import com.ghatana.aep.connector.util.RetryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class providing retry and circuit breaker capabilities for connector implementations.
 *
 * <p>Subclasses inherit:
 * <ul>
 *   <li>{@link #withRetry(String, ThrowingSupplier)} - exponential backoff retry</li>
 *   <li>{@link #ioExecutor} - virtual-thread executor for blocking I/O offloading</li>
 *   <li>{@link #log} - SLF4J logger scoped to the concrete subclass</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Base class with retry and circuit breaker for resilient connectors
 * @doc.layer infrastructure
 * @doc.pattern Template Method
 */
public abstract class AbstractResilientConnector {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final RetryConfig retryConfig;
    protected final ExecutorService ioExecutor;

    protected AbstractResilientConnector(RetryConfig retryConfig) {
        this.retryConfig = retryConfig;
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Execute an operation with exponential-backoff retries according to the configured
     * {@link RetryConfig}.
     *
     * <p>Each failure is logged at WARN level. If all attempts are exhausted, the last
     * exception is rethrown.
     *
     * @param <T>           return type
     * @param operationName descriptive name for logging
     * @param operation     the operation to attempt
     * @return the result of the first successful attempt
     * @throws Exception the last exception if all attempts fail
     */
    protected <T> T withRetry(String operationName, ThrowingSupplier<T> operation) throws Exception {
        return RetryExecutor.execute(retryConfig, log, operationName, operation::get, ignored -> true);
    }

    /**
     * Functional interface for operations that may throw checked exceptions.
     *
     * @param <T> return type
     */
    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
