package com.ghatana.digitalmarketing.connector.metaads;

/**
 * Exception thrown by Meta Ads connector operations.
 *
 * @doc.type class
 * @doc.purpose Represents errors from Meta Ads connector operations
 * @doc.layer connector
 */
public final class MetaAdsConnectorException extends RuntimeException {

    private final String errorCode;

    public MetaAdsConnectorException(String message) {
        super(message);
        this.errorCode = null;
    }

    public MetaAdsConnectorException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public MetaAdsConnectorException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public MetaAdsConnectorException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
