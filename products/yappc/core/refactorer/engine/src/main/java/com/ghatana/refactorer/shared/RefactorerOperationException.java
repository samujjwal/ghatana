package com.ghatana.refactorer.shared;

/**
 * @doc.type class
 * @doc.purpose Represents typed operational failures in refactorer execution flows
 * @doc.layer core
 * @doc.pattern Exception
 */
public final class RefactorerOperationException extends RuntimeException {

    public RefactorerOperationException(String message) {
        super(message);
    }

    public RefactorerOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
