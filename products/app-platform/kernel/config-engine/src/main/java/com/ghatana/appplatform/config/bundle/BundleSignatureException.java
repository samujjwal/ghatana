package com.ghatana.appplatform.config.bundle;

/**
 * Thrown when a config bundle's signature is missing, invalid, or signed
 * with an untrusted key.
 *
 * @doc.type class
 * @doc.purpose Signals a bundle signature verification failure (K02-013)
 * @doc.layer product
 * @doc.pattern Exception
 */
public class BundleSignatureException extends Exception {

    public BundleSignatureException(String message) {
        super(message);
    }

    public BundleSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
