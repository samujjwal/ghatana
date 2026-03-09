package com.ghatana.refactorer.refactoring.model;

/**
 * Represents the type of a Java element. 
 * @doc.type enum
 * @doc.purpose Enumerates java element type values
 * @doc.layer core
 * @doc.pattern Enum
*/
public enum JavaElementType {
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    METHOD,
    FIELD,
    VARIABLE,
    PARAMETER,
    PACKAGE;

    /**
 * Converts a string representation to a JavaElementType. */
    public static JavaElementType fromString(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "class" -> CLASS;
            case "interface" -> INTERFACE;
            case "enum" -> ENUM;
            case "annotation" -> ANNOTATION;
            case "method" -> METHOD;
            case "field" -> FIELD;
            case "variable" -> VARIABLE;
            case "parameter" -> PARAMETER;
            case "package" -> PACKAGE;
            default -> null;
        };
    }
}
