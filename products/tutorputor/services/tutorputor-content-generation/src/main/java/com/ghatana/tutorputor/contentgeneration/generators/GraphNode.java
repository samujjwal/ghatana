package com.ghatana.tutorputor.contentgeneration.generators;

import java.util.Map;

/**
 * Represents a node in the knowledge graph.
 *
 * @doc.type interface
 * @doc.purpose Abstraction for knowledge graph nodes used in content generation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public interface GraphNode {

    /**
     * Returns the node type (e.g. "CONCEPT", "PREREQUISITE", "SKILL").
     *
     * @return node type label
     */
    String getType();

    /**
     * Returns all properties associated with this node.
     *
     * @return an immutable map of property keys to string values
     */
    Map<String, String> getProperties();
}
