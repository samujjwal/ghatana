package com.ghatana.pattern.engine.model;

/**
 * Visitor interface for pattern specifications.
 *
 * @param <T> The return type of the visitor
  * @doc.type interface
 * @doc.purpose Provides pattern visitor functionality.
 * @doc.layer product
 * @doc.pattern Interface
*/
public interface PatternVisitor<T> {
    T visit(PrimaryEventPattern pattern);
    T visit(SequencePattern pattern);
    T visit(ConjunctionPattern pattern);
    T visit(DisjunctionPattern pattern);
    T visit(NegationPattern pattern);
    T visit(WithinPattern pattern);
}
