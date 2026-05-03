package com.ghatana.digitalmarketing.persistence.campaign;

/**
 * Unchecked exception thrown when a DMOS persistence operation fails.
 *
 * @doc.type class
 * @doc.purpose Wraps JDBC SQLExceptions in a domain-safe unchecked exception for DMOS persistence adapters
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class DmPersistenceException extends RuntimeException {

    public DmPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
