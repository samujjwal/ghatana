/*
 * Copyright (c) 2026 Ghatana Technologies
 * Platform Connectors — EventLogStore transport exception
 */
package com.ghatana.core.connectors.eventlog;

/**
 * Thrown by {@link GrpcEventLogStore} and {@link HttpEventLogStore} when the
 * remote Data-Cloud server responds with an error status or is unreachable.
 *
 * @doc.type class
 * @doc.purpose Runtime exception wrapping remote Data-Cloud communication failures
 * @doc.layer core
 * @doc.pattern Exception
 */
public final class DataCloudRemoteException extends RuntimeException {

    /**
     * @param message human-readable error description
     */
    public DataCloudRemoteException(String message) {
        super(message);
    }

    /**
     * @param message human-readable error description
     * @param cause   the underlying exception
     */
    public DataCloudRemoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
