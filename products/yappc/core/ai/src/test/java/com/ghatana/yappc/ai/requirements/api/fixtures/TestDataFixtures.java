package com.ghatana.yappc.ai.requirements.api.fixtures;

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

    private static final ObjectMapper objectMapper = new ObjectMapper(); // GH-90000

    // User fixtures
    public static ObjectNode createTestUser() { // GH-90000
        ObjectNode user = objectMapper.createObjectNode(); // GH-90000
        user.put("id", UUID.randomUUID().toString()); // GH-90000
        user.put("email", "test-" + System.currentTimeMillis() + "@example.com"); // GH-90000
        user.put("name", "Test User"); // GH-90000
        user.put("role", "ADMIN"); // GH-90000
        return user;
    }

    public static ObjectNode createTestUser(String email, String name) { // GH-90000
        ObjectNode user = objectMapper.createObjectNode(); // GH-90000
        user.put("id", UUID.randomUUID().toString()); // GH-90000
        user.put("email", email); // GH-90000
        user.put("name", name); // GH-90000
        user.put("role", "USER"); // GH-90000
        return user;
    }

    // Workspace fixtures
    public static ObjectNode createTestWorkspace() { // GH-90000
        ObjectNode workspace = objectMapper.createObjectNode(); // GH-90000
        workspace.put("id", UUID.randomUUID().toString()); // GH-90000
        workspace.put("name", "Test Workspace"); // GH-90000
        workspace.put("description", "A test workspace"); // GH-90000
        workspace.put("createdBy", UUID.randomUUID().toString()); // GH-90000
        return workspace;
    }

    public static ObjectNode createTestWorkspace(String name) { // GH-90000
        ObjectNode workspace = objectMapper.createObjectNode(); // GH-90000
        workspace.put("id", UUID.randomUUID().toString()); // GH-90000
        workspace.put("name", name); // GH-90000
        workspace.put("description", "Workspace: " + name); // GH-90000
        workspace.put("createdBy", UUID.randomUUID().toString()); // GH-90000
        return workspace;
    }

    public static ObjectNode createCreateWorkspaceRequest(String name) { // GH-90000
        ObjectNode request = objectMapper.createObjectNode(); // GH-90000
        request.put("name", name); // GH-90000
        request.put("description", "Workspace: " + name); // GH-90000
        return request;
    }

    // Project fixtures
    public static ObjectNode createTestProject(String workspaceId) { // GH-90000
        ObjectNode project = objectMapper.createObjectNode(); // GH-90000
        project.put("id", UUID.randomUUID().toString()); // GH-90000
        project.put("workspaceId", workspaceId); // GH-90000
        project.put("name", "Test Project"); // GH-90000
        project.put("description", "A test project"); // GH-90000
        project.put("status", "ACTIVE"); // GH-90000
        return project;
    }

    public static ObjectNode createTestProject(String workspaceId, String name) { // GH-90000
        ObjectNode project = objectMapper.createObjectNode(); // GH-90000
        project.put("id", UUID.randomUUID().toString()); // GH-90000
        project.put("workspaceId", workspaceId); // GH-90000
        project.put("name", name); // GH-90000
        project.put("description", "Project: " + name); // GH-90000
        project.put("status", "ACTIVE"); // GH-90000
        return project;
    }

    public static ObjectNode createCreateProjectRequest(String name) { // GH-90000
        ObjectNode request = objectMapper.createObjectNode(); // GH-90000
        request.put("name", name); // GH-90000
        request.put("description", "Project: " + name); // GH-90000
        return request;
    }

    // Requirement fixtures
    public static ObjectNode createTestRequirement(String projectId) { // GH-90000
        ObjectNode requirement = objectMapper.createObjectNode(); // GH-90000
        requirement.put("id", UUID.randomUUID().toString()); // GH-90000
        requirement.put("projectId", projectId); // GH-90000
        requirement.put("title", "Test Requirement"); // GH-90000
        requirement.put("description", "A test requirement"); // GH-90000
        requirement.put("type", "FUNCTIONAL"); // GH-90000
        requirement.put("priority", "MUST_HAVE"); // GH-90000
        requirement.put("status", "DRAFT"); // GH-90000
        return requirement;
    }

    public static ObjectNode createTestRequirement(String projectId, String title) { // GH-90000
        ObjectNode requirement = objectMapper.createObjectNode(); // GH-90000
        requirement.put("id", UUID.randomUUID().toString()); // GH-90000
        requirement.put("projectId", projectId); // GH-90000
        requirement.put("title", title); // GH-90000
        requirement.put("description", "Requirement: " + title); // GH-90000
        requirement.put("type", "FUNCTIONAL"); // GH-90000
        requirement.put("priority", "SHOULD_HAVE"); // GH-90000
        requirement.put("status", "DRAFT"); // GH-90000
        return requirement;
    }

    public static ObjectNode createCreateRequirementRequest(String title) { // GH-90000
        ObjectNode request = objectMapper.createObjectNode(); // GH-90000
        request.put("title", title); // GH-90000
        request.put("description", "Requirement: " + title); // GH-90000
        request.put("type", "FUNCTIONAL"); // GH-90000
        request.put("priority", "SHOULD_HAVE"); // GH-90000
        return request;
    }

    // AI Suggestion fixtures
    public static ObjectNode createTestAISuggestion(String requirementId) { // GH-90000
        ObjectNode suggestion = objectMapper.createObjectNode(); // GH-90000
        suggestion.put("id", UUID.randomUUID().toString()); // GH-90000
        suggestion.put("requirementId", requirementId); // GH-90000
        suggestion.put("content", "This is a test AI suggestion"); // GH-90000
        suggestion.put("confidence", 0.85); // GH-90000
        suggestion.put("source", "GPT-4"); // GH-90000
        suggestion.put("type", "ENHANCEMENT"); // GH-90000
        return suggestion;
    }

    // Export fixtures
    public static ObjectNode createExportRequest(String format, String... requirementIds) { // GH-90000
        ObjectNode request = objectMapper.createObjectNode(); // GH-90000
        request.put("format", format); // GH-90000
        com.fasterxml.jackson.databind.node.ArrayNode idsArray = request.putArray("requirementIds");
        for (String id : requirementIds) { // GH-90000
            idsArray.add(id); // GH-90000
        }
        return request;
    }

    // Authentication fixtures
    public static ObjectNode createLoginRequest(String email, String password) { // GH-90000
        ObjectNode request = objectMapper.createObjectNode(); // GH-90000
        request.put("email", email); // GH-90000
        request.put("password", password); // GH-90000
        return request;
    }

    public static ObjectNode createTestAuthToken() { // GH-90000
        ObjectNode token = objectMapper.createObjectNode(); // GH-90000
        token.put("accessToken", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"); // GH-90000
        token.put("refreshToken", "refresh-token"); // GH-90000
        token.put("expiresIn", 3600); // GH-90000
        return token;
    }

    /**
     * Convert object to JSON string
     */
    public static String toJson(Object obj) { // GH-90000
        try {
            return objectMapper.writeValueAsString(obj); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to serialize to JSON", e); // GH-90000
        }
    }

    /**
     * Parse JSON string to ObjectNode
     */
    public static ObjectNode parseJson(String json) { // GH-90000
        try {
            return (ObjectNode) objectMapper.readTree(json); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to parse JSON", e); // GH-90000
        }
    }

    /**
     * Create a new ObjectNode for building JSON objects
     */
    public static ObjectNode createObjectNode() { // GH-90000
        return objectMapper.createObjectNode(); // GH-90000
    }
}
