package com.ghatana.yappc.client;

import java.util.Map;

/**
 * Options for code generation.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles generation options operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class GenerationOptions {
    
    private final String language;
    private final String framework;
    private final Map<String, Object> options;
    
    public GenerationOptions(String language, String framework, Map<String, Object> options) {
        this.language = language;
        this.framework = framework;
        this.options = Map.copyOf(options);
    }
    
    public String getLanguage() {
        return language;
    }
    
    public String getFramework() {
        return framework;
    }
    
    public Map<String, Object> getOptions() {
        return options;
    }
}
