package com.ghatana.refactorer.refactoring.model;

import com.github.javaparser.ast.Node;

/**
 * Represents a Java element that can be refactored. 
 * @doc.type class
 * @doc.purpose Handles java element operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class JavaElement {
    private final String name;
    private final JavaElementType type;
    private final Node node;

    /**
     * Creates a new JavaElement.
     *
     * @param name the name of the element
     * @param type the type of the element
     * @param node the AST node representing the element
     */
    public JavaElement(String name, JavaElementType type, Node node) {
        this.name = name;
        this.type = type;
        this.node = node;
    }

    /**
 * Gets the name of the element. */
    public String getName() {
        return name;
    }

    /**
 * Gets the type of the element. */
    public JavaElementType getType() {
        return type;
    }

    /**
 * Gets the AST node representing the element. */
    public Node getNode() {
        return node;
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", name, type.name().toLowerCase());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaElement that = (JavaElement) o;
        return name.equals(that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + type.hashCode();
    }
}
