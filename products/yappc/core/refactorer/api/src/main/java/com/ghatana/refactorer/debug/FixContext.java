package com.ghatana.refactorer.debug;

import java.util.HashMap;
import java.util.Map;

/**
 * Context information for a fix suggestion. 
 * @doc.type class
 * @doc.purpose Handles fix context operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class FixContext {
    private final String language;
    private final String filePath;
    private final Map<String, Object> metadata;

    public FixContext(String language, String filePath) {
        this.language = language;
        this.filePath = filePath;
        this.metadata = new HashMap<>();
    }

    public String getLanguage() {
        return language;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        return (T) metadata.getOrDefault(key, defaultValue);
    }
}
