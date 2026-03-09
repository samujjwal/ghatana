package com.ghatana.refactorer.diagnostics.tsjs.model;

/**
 * Represents a TypeScript compiler diagnostic. 
 * @doc.type class
 * @doc.purpose Handles tsc diagnostic operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class TscDiagnostic {
    public String file;
    public int line;
    public int column;
    public String code;
    public String message;
    public String severity;

    // Default constructor for JSON deserialization
    public TscDiagnostic() {}

    @Override
    public String toString() {
        return String.format(
                "TscDiagnostic{file='%s', line=%d, column=%d, code='%s', message='%s',"
                        + " severity='%s'}",
                file, line, column, code, message, severity);
    }
}
