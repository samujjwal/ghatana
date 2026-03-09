package com.ghatana.platform.core.exception;

/**
 * Exception thrown when there is an error accessing data.
 * 
 * <p>Use this for database, file system, cache, or other
 * data access layer failures.</p>
 *
 * @doc.type exception
 * @doc.purpose Data access layer failure exception (DB, filesystem, cache)
 * @doc.layer core
 * @doc.pattern Exception, Data Access Error
 */
public class DataAccessException extends BaseException {
    
    private static final long serialVersionUID = 1L;
    
    public DataAccessException(String message) {
        super(ErrorCode.DATABASE_ERROR, message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(ErrorCode.DATABASE_ERROR, message, cause);
    }

    public DataAccessException(Throwable cause) {
        super(ErrorCode.DATABASE_ERROR, cause);
    }
    
    public DataAccessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

