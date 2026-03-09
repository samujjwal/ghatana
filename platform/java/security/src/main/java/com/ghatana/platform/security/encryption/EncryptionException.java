package com.ghatana.platform.security.encryption;

/**
 * Exception thrown when an error occurs during encryption or decryption operations.
 
 *
 * @doc.type class
 * @doc.purpose Encryption exception
 * @doc.layer core
 * @doc.pattern Exception
*/
public class EncryptionException extends RuntimeException {
    
    /**
     * Constructs a new encryption exception with the specified detail message.
     *
     * @param message the detail message
     */
    public EncryptionException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new encryption exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new encryption exception with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public EncryptionException(Throwable cause) {
        super(cause);
    }
}
