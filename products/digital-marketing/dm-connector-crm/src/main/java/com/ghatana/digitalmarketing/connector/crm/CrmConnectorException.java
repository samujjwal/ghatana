package com.ghatana.digitalmarketing.connector.crm;

/**
 * Exception thrown by CRM connector operations.
 *
 * @doc.type class
 * @doc.purpose Represents errors from CRM connector operations (DMOS-P3-002)
 * @doc.layer connector
 */
public final class CrmConnectorException extends RuntimeException {

    private final String errorCode;

    public CrmConnectorException(String message) {
        super(message);
        this.errorCode = null;
    }

    public CrmConnectorException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CrmConnectorException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public CrmConnectorException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
