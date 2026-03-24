package com.ghatana.yappc.client;

/**
 * Request to create a canvas.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles create canvas request operations
 * @doc.layer core
 * @doc.pattern DTO
*/
public final class CreateCanvasRequest {
    
    private final String name;
    private final String description;
    private final String templateId;
    
    public CreateCanvasRequest(String name, String description, String templateId) {
        this.name = name;
        this.description = description;
        this.templateId = templateId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getTemplateId() {
        return templateId;
    }
}
