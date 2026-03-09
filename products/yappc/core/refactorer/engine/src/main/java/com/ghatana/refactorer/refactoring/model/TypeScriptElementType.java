package com.ghatana.refactorer.refactoring.model;

/**
 * Represents the type of a TypeScript/JavaScript element. 
 * @doc.type enum
 * @doc.purpose Enumerates type script element type values
 * @doc.layer core
 * @doc.pattern Enum
*/
public enum TypeScriptElementType {
    MODULE,
    CLASS,
    INTERFACE,
    ENUM,
    FUNCTION,
    METHOD,
    VARIABLE,
    PARAMETER,
    IMPORT,
    EXPORT,
    PROPERTY,
    GETTER,
    SETTER,
    TYPE_ALIAS,
    NAMESPACE;

    /**
 * Converts a string representation to a TypeScriptElementType. */
    public static TypeScriptElementType fromString(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "module" -> MODULE;
            case "class" -> CLASS;
            case "interface" -> INTERFACE;
            case "enum" -> ENUM;
            case "function" -> FUNCTION;
            case "method" -> METHOD;
            case "variable" -> VARIABLE;
            case "parameter" -> PARAMETER;
            case "import" -> IMPORT;
            case "export" -> EXPORT;
            case "property" -> PROPERTY;
            case "getter" -> GETTER;
            case "setter" -> SETTER;
            case "type_alias", "typealias" -> TYPE_ALIAS;
            case "namespace" -> NAMESPACE;
            default -> null;
        };
    }
}
