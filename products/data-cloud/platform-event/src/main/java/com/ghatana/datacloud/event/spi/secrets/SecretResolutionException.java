package com.ghatana.datacloud.event.spi.secrets;
/**
 * Secret resolution exception.
 *
 * @doc.type class
 * @doc.purpose Secret resolution exception
 * @doc.layer core
 * @doc.pattern Exception
 */

public class SecretResolutionException extends RuntimeException {
    public SecretResolutionException(String message) {
        super(message);
    }

    public SecretResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
