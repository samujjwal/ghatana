package com.ghatana.platform.core.exception;

/**
 * Exception thrown when there is an error during event creation.
 * 
 * <p>Use this exception in event-related modules when event instantiation,
 * validation, or persistence fails during the creation process.</p>
 * 
 * @see EventProcessingException
 * @doc.type exception
 * @doc.purpose Event creation failure exception (instantiation, validation, persistence)
 * @doc.layer core
 * @doc.pattern Exception, Event Creation Error
 */
public class EventCreationException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new event creation exception with the specified detail message.
     *
     * @param message the detail message
     */
    public EventCreationException(String message) {
        super(ErrorCode.EVENT_VALIDATION_ERROR, message);
    }
    
    /**
     * Constructs a new event creation exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public EventCreationException(String message, Throwable cause) {
        super(ErrorCode.EVENT_VALIDATION_ERROR, message, cause);
    }
    
    /**
     * Constructs a new event creation exception with the specified cause.
     *
     * @param cause the cause
     */
    public EventCreationException(Throwable cause) {
        super(ErrorCode.EVENT_VALIDATION_ERROR, cause);
    }
    
    /**
     * Constructs a new event creation exception with a specific error code and message.
     *
     * @param errorCode the error code
     * @param message the detail message
     */
    public EventCreationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

