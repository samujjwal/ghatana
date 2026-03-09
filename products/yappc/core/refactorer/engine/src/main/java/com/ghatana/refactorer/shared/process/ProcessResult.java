package com.ghatana.refactorer.shared.process;

/**
 * Represents the result of a process execution. 
 * @doc.type record
 * @doc.purpose Immutable data carrier for process result
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public record ProcessResult(int exitCode, String output, String error) {
    /**
 * Checks if the process completed successfully. */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
