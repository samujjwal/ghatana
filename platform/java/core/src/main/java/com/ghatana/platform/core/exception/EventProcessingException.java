package com.ghatana.platform.core.exception;

/**
 * Exception thrown when there is an error processing an event.
 * 
 * <p>Use this exception for runtime errors during event processing,
 * transformation, routing, or delivery operations.</p>
 * 
 * @see EventCreationException
 * @doc.type exception
 * @doc.purpose Event processing failure exception (transformation, routing, delivery)
 * @doc.layer core
 * @doc.pattern Exception, Event Processing Error
 */
public class EventProcessingException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    public EventProcessingException(String message) {
        super(ErrorCode.EVENT_PROCESSING_ERROR, message);
    }

    public EventProcessingException(String message, Throwable cause) {
        super(ErrorCode.EVENT_PROCESSING_ERROR, message, cause);
    }

    public EventProcessingException(Throwable cause) {
        super(ErrorCode.EVENT_PROCESSING_ERROR, cause);
    }
    
    public EventProcessingException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

