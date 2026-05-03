package com.ghatana.digitalmarketing.connector.googleads;

/**
 * Unchecked exception for Google Ads HTTP adapter failures.
 *
 * @doc.type class
 * @doc.purpose Provides typed failure classification for all Google Ads connector transport errors
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class GoogleAdsConnectorException extends RuntimeException {

    public GoogleAdsConnectorException(String message) {
        super(message);
    }

    public GoogleAdsConnectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
