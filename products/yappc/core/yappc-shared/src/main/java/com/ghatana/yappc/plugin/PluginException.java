package com.ghatana.yappc.plugin;

/**
 * Exception thrown by plugins.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles plugin exception operations
 * @doc.layer core
 * @doc.pattern Exception
*/
public class PluginException extends Exception {
    
    public PluginException(String message) {
        super(message);
    }
    
    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PluginException(Throwable cause) {
        super(cause);
    }
}
