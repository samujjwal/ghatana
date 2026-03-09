package com.ghatana.requirements.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**

 * @doc.type class

 * @doc.purpose Handles requirement generation request operations

 * @doc.layer core

 * @doc.pattern DTO

 */

public class RequirementGenerationRequest {
    private String description;
    private String projectContext;
    private String userRole;

    private RequirementGenerationRequest() {}

    public String getDescription() { return description; }
    public String getProjectContext() { return projectContext; }
    public String getUserRole() { return userRole; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RequirementGenerationRequest req = new RequirementGenerationRequest();
        public Builder description(String d) { req.description = d; return this; }
        public Builder projectContext(String p) { req.projectContext = p; return this; }
        public Builder userRole(String r) { req.userRole = r; return this; }
        public RequirementGenerationRequest build() { return req; }
    }
}
