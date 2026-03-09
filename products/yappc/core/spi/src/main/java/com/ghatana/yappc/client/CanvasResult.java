package com.ghatana.yappc.client;

/**
 * Result of canvas creation.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles canvas result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class CanvasResult {
    
    private final String canvasId;
    private final String name;
    private final boolean success;
    
    public CanvasResult(String canvasId, String name, boolean success) {
        this.canvasId = canvasId;
        this.name = name;
        this.success = success;
    }
    
    public String getCanvasId() {
        return canvasId;
    }
    
    public String getName() {
        return name;
    }
    
    public boolean isSuccess() {
        return success;
    }
}
