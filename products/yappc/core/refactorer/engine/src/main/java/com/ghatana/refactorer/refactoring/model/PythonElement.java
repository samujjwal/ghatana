package com.ghatana.refactorer.refactoring.model;

/**
 * Represents a Python element that can be refactored. 
 * @doc.type class
 * @doc.purpose Handles python element operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class PythonElement {
    private final String name;
    private final PythonElementType type;
    private final Object astNode; // Will hold the AST node from the Python parser

    /**
     * Creates a new PythonElement.
     *
     * @param name the name of the element
     * @param type the type of the element
     * @param astNode the AST node representing the element
     */
    public PythonElement(String name, PythonElementType type, Object astNode) {
        this.name = name;
        this.type = type;
        this.astNode = astNode;
    }

    /**
 * Gets the name of the element. */
    public String getName() {
        return name;
    }

    /**
 * Gets the type of the element. */
    public PythonElementType getType() {
        return type;
    }

    /**
 * Gets the AST node representing the element. */
    public Object getAstNode() {
        return astNode;
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", name, type.name().toLowerCase());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PythonElement that = (PythonElement) o;
        return name.equals(that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + type.hashCode();
    }
}
