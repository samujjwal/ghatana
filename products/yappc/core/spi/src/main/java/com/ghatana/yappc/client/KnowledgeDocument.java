package com.ghatana.yappc.client;

import java.util.Map;

/**
 * Document to ingest into knowledge graph.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles knowledge document operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class KnowledgeDocument {
    
    private final String id;
    private final String title;
    private final String content;
    private final Map<String, Object> metadata;
    
    public KnowledgeDocument(String id, String title, String content, Map<String, Object> metadata) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.metadata = Map.copyOf(metadata);
    }
    
    public String getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getContent() {
        return content;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
