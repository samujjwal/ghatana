package com.ghatana.security.tls;

/**
 * Exception thrown when there is an error configuring TLS.
 
 *
 * @doc.type class
 * @doc.purpose Tls configuration exception
 * @doc.layer core
 * @doc.pattern Exception
*/
public class TlsConfigurationException extends Exception {
    
    /**
     * Creates a new TlsConfigurationException with the specified detail message.
     * 
     * @param message The detail message
     */
    public TlsConfigurationException(String message) {
        super(message);
    }
    
    /**
     * Creates a new TlsConfigurationException with the specified detail message and cause.
     * 
     * @param message The detail message
     * @param cause The cause
     */
    public TlsConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new TlsConfigurationException with the specified cause.
     * 
     * @param cause The cause
     */
    public TlsConfigurationException(Throwable cause) {
        super(cause);
    }
}
