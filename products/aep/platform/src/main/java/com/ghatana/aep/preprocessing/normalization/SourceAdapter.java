/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.preprocessing.normalization;

import com.ghatana.aep.preprocessing.eventization.SemanticEvent;

/**
 * Adapter interface for source-specific normalization.
 * 
 * <p><b>Purpose</b><br>
 * Defines contract for transforming source-specific events into canonical format.
 * Each source (HTTP, DB, files, IoT) implements this to provide custom mapping logic.
 * 
 * <p><b>Adapter Pattern</b><br>
 * Enables pluggable source support without modifying core normalization logic.
 * New sources can be added by implementing this interface.
 * 
 * <p><b>Example Adapters</b><br>
 * <ul>
 *   <li>HttpSourceAdapter - Normalizes HTTP request/response events</li>
 *   <li>DatabaseSourceAdapter - Normalizes DB change events</li>
 *   <li>FileSystemSourceAdapter - Normalizes file operations</li>
 *   <li>IoTSourceAdapter - Normalizes sensor readings</li>
 * </ul>
 * 
 * @doc.type interface
 * @doc.purpose Source-specific event normalization
 * @doc.layer product
 * @doc.pattern Adapter
 */
public interface SourceAdapter {
    
    /**
     * Returns the source type this adapter handles.
     * 
     * @return Source identifier (e.g., "http", "database", "filesystem")
     */
    String getSourceType();
    
    /**
     * Checks if this adapter can handle the given event.
     * 
     * @param event Semantic event to check
     * @return true if adapter can normalize this event
     */
    boolean canHandle(SemanticEvent event);
    
    /**
     * Transforms semantic event to canonical format.
     * 
     * @param event Semantic event from eventization
     * @return Canonical event with normalized schema
     */
    CanonicalEvent normalize(SemanticEvent event);
    
    /**
     * Validates that the semantic event has required fields for this source.
     * 
     * @param event Event to validate
     * @return true if event is valid for this source
     */
    default boolean validate(SemanticEvent event) {
        return event != null && 
               event.eventType() != null && 
               event.timestamp() != null &&
               event.attributes() != null;
    }
}
