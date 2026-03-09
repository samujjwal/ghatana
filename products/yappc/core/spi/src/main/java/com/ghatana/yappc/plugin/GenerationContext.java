package com.ghatana.yappc.plugin;

import java.util.Map;

/**
 * Context for generation operations.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for generation context
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface GenerationContext {
    
    /**
     * Gets the source object to generate from.
     *
     * @return the source object
     */
    Object getSource();
    
    /**
     * Gets the target language.
     *
     * @return the language
     */
    String getLanguage();
    
    /**
     * Gets the target framework.
     *
     * @return the framework
     */
    String getFramework();
    
    /**
     * Gets generation options.
     *
     * @return the options map
     */
    Map<String, Object> getOptions();
}
