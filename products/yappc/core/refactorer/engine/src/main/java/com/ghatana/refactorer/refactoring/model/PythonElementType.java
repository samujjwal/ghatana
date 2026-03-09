package com.ghatana.refactorer.refactoring.model;

/**
 * Represents the type of a Python element. 
 * @doc.type enum
 * @doc.purpose Enumerates python element type values
 * @doc.layer core
 * @doc.pattern Enum
*/
public enum PythonElementType {
    MODULE,
    CLASS,
    FUNCTION,
    METHOD,
    VARIABLE,
    PARAMETER,
    IMPORT,
    ATTRIBUTE;

    /**
 * Converts a string representation to a PythonElementType. */
    public static PythonElementType fromString(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "module" -> MODULE;
            case "class" -> CLASS;
            case "function" -> FUNCTION;
            case "method" -> METHOD;
            case "variable" -> VARIABLE;
            case "parameter" -> PARAMETER;
            case "import" -> IMPORT;
            case "attribute" -> ATTRIBUTE;
            default -> null;
        };
    }
}
