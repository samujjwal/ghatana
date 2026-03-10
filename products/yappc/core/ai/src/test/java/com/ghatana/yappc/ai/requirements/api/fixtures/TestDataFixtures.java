package com.ghatana.yappc.ai.requirements.api.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * Test data fixtures for API testing.
 * 
 * Provides factory methods for creating test entities and JSON payloads.
 
 * @doc.type class
 * @doc.purpose Handles test data fixtures operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class TestDataFixtures {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // User fixtures
    public static ObjectNode createTestUser() {
        ObjectNode user = objectMapper.createObjectNode();
        user.put("id", UUID.randomUUID().toString());
        user.put("email", "test-" + System.currentTimeMillis() + "@example.com");
        user.put("name", "Test User");
        user.put("role", "ADMIN");
        return user;
    }
    
    public static ObjectNode createTestUser(String email, String name) {
        ObjectNode user = objectMapper.createObjectNode();
        user.put("id", UUID.randomUUID().toString());
        user.put("email", email);
        user.put("name", name);
        user.put("role", "USER");
        return user;
    }
    
    // Workspace fixtures
    public static ObjectNode createTestWorkspace() {
        ObjectNode workspace = objectMapper.createObjectNode();
        workspace.put("id", UUID.randomUUID().toString());
        workspace.put("name", "Test Workspace");
        workspace.put("description", "A test workspace");
        workspace.put("createdBy", UUID.randomUUID().toString());
        return workspace;
    }
    
    public static ObjectNode createTestWorkspace(String name) {
        ObjectNode workspace = objectMapper.createObjectNode();
        workspace.put("id", UUID.randomUUID().toString());
        workspace.put("name", name);
        workspace.put("description", "Workspace: " + name);
        workspace.put("createdBy", UUID.randomUUID().toString());
        return workspace;
    }
    
    public static ObjectNode createCreateWorkspaceRequest(String name) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("name", name);
        request.put("description", "Workspace: " + name);
        return request;
    }
    
    // Project fixtures
    public static ObjectNode createTestProject(String workspaceId) {
        ObjectNode project = objectMapper.createObjectNode();
        project.put("id", UUID.randomUUID().toString());
        project.put("workspaceId", workspaceId);
        project.put("name", "Test Project");
        project.put("description", "A test project");
        project.put("status", "ACTIVE");
        return project;
    }
    
    public static ObjectNode createTestProject(String workspaceId, String name) {
        ObjectNode project = objectMapper.createObjectNode();
        project.put("id", UUID.randomUUID().toString());
        project.put("workspaceId", workspaceId);
        project.put("name", name);
        project.put("description", "Project: " + name);
        project.put("status", "ACTIVE");
        return project;
    }
    
    public static ObjectNode createCreateProjectRequest(String name) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("name", name);
        request.put("description", "Project: " + name);
        return request;
    }
    
    // Requirement fixtures
    public static ObjectNode createTestRequirement(String projectId) {
        ObjectNode requirement = objectMapper.createObjectNode();
        requirement.put("id", UUID.randomUUID().toString());
        requirement.put("projectId", projectId);
        requirement.put("title", "Test Requirement");
        requirement.put("description", "A test requirement");
        requirement.put("type", "FUNCTIONAL");
        requirement.put("priority", "MUST_HAVE");
        requirement.put("status", "DRAFT");
        return requirement;
    }
    
    public static ObjectNode createTestRequirement(String projectId, String title) {
        ObjectNode requirement = objectMapper.createObjectNode();
        requirement.put("id", UUID.randomUUID().toString());
        requirement.put("projectId", projectId);
        requirement.put("title", title);
        requirement.put("description", "Requirement: " + title);
        requirement.put("type", "FUNCTIONAL");
        requirement.put("priority", "SHOULD_HAVE");
        requirement.put("status", "DRAFT");
        return requirement;
    }
    
    public static ObjectNode createCreateRequirementRequest(String title) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("title", title);
        request.put("description", "Requirement: " + title);
        request.put("type", "FUNCTIONAL");
        request.put("priority", "SHOULD_HAVE");
        return request;
    }
    
    // AI Suggestion fixtures
    public static ObjectNode createTestAISuggestion(String requirementId) {
        ObjectNode suggestion = objectMapper.createObjectNode();
        suggestion.put("id", UUID.randomUUID().toString());
        suggestion.put("requirementId", requirementId);
        suggestion.put("content", "This is a test AI suggestion");
        suggestion.put("confidence", 0.85);
        suggestion.put("source", "GPT-4");
        suggestion.put("type", "ENHANCEMENT");
        return suggestion;
    }
    
    // Export fixtures
    public static ObjectNode createExportRequest(String format, String... requirementIds) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("format", format);
        com.fasterxml.jackson.databind.node.ArrayNode idsArray = request.putArray("requirementIds");
        for (String id : requirementIds) {
            idsArray.add(id);
        }
        return request;
    }
    
    // Authentication fixtures
    public static ObjectNode createLoginRequest(String email, String password) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("email", email);
        request.put("password", password);
        return request;
    }
    
    public static ObjectNode createTestAuthToken() {
        ObjectNode token = objectMapper.createObjectNode();
        token.put("accessToken", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");
        token.put("refreshToken", "refresh-token");
        token.put("expiresIn", 3600);
        return token;
    }
    
    /**
     * Convert object to JSON string
     */
    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
    
    /**
     * Parse JSON string to ObjectNode
     */
    public static ObjectNode parseJson(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
    
    /**
     * Create a new ObjectNode for building JSON objects
     */
    public static ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }
}