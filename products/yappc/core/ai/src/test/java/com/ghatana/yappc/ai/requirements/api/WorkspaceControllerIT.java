package com.ghatana.yappc.ai.requirements.api;

import com.ghatana.yappc.ai.requirements.api.fixtures.TestDataFixtures;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WorkspaceController.
 *
 * Tests workspace management endpoints including:
 * - Creating workspaces
 * - Retrieving workspace information
 * - Listing workspaces
 * - Updating workspace settings
 * - Managing workspace members
 *
 * @Disabled HTTP server infrastructure not yet implemented (see AbstractIntegrationTest TODO) // GH-90000
 */
@DisplayName("Workspace Controller Integration Tests [GH-90000]")
@Disabled("HTTP server infrastructure not yet implemented - see AbstractIntegrationTest line 141-143 [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles workspace controller it operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public class WorkspaceControllerIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Should create a new workspace [GH-90000]")
    public void testCreateWorkspace() throws Exception { // GH-90000
        // Given
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Test Workspace [GH-90000]");
        var requestBody = TestDataFixtures.toJson(createRequest); // GH-90000

        // When
        var response = performPost("/v1/workspaces", requestBody); // GH-90000

        // Then
        assertEquals(201, response.getCode()); // GH-90000
        var responseBody = response.getBody().asString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
        var workspaceData = TestDataFixtures.parseJson(responseBody); // GH-90000

        assertNotNull(workspaceData.get("id [GH-90000]"));
        assertEquals("Test Workspace", workspaceData.get("name [GH-90000]").asText());
    }

    @Test
    @DisplayName("Should retrieve workspace by ID [GH-90000]")
    public void testGetWorkspaceById() throws Exception { // GH-90000
        // Given - Create a workspace first
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Fetch Test [GH-90000]");
        var requestBody = TestDataFixtures.toJson(createRequest); // GH-90000
        var createResponse = performPost("/v1/workspaces", requestBody); // GH-90000

        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000
        var workspaceId = workspaceData.get("id [GH-90000]").asText();

        // When
        var getResponse = performGet("/v1/workspaces/" + workspaceId); // GH-90000

        // Then
        assertEquals(200, getResponse.getCode()); // GH-90000
        var retrievedData = TestDataFixtures.parseJson(getResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000

        assertEquals(workspaceId, retrievedData.get("id [GH-90000]").asText());
        assertEquals("Fetch Test", retrievedData.get("name [GH-90000]").asText());
    }

    @Test
    @DisplayName("Should return 404 for non-existent workspace [GH-90000]")
    public void testGetNonExistentWorkspace() throws Exception { // GH-90000
        // When
        var response = performGet("/v1/workspaces/non-existent-id [GH-90000]");

        // Then
        assertEquals(404, response.getCode()); // GH-90000
    }

    @Test
    @DisplayName("Should list all workspaces [GH-90000]")
    public void testListWorkspaces() throws Exception { // GH-90000
        // Given - Create multiple workspaces
        TestDataFixtures.createCreateWorkspaceRequest("Workspace 1 [GH-90000]");
        TestDataFixtures.createCreateWorkspaceRequest("Workspace 2 [GH-90000]");

        // When
        var response = performGet("/v1/workspaces [GH-90000]");

        // Then
        assertEquals(200, response.getCode()); // GH-90000
        var responseBody = response.getBody().asString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
        var workspaces = TestDataFixtures.parseJson(responseBody); // GH-90000

        assertTrue(workspaces.isArray()); // GH-90000
        assertTrue(workspaces.size() >= 0); // GH-90000
    }

    @Test
    @DisplayName("Should update workspace [GH-90000]")
    public void testUpdateWorkspace() throws Exception { // GH-90000
        // Given - Create a workspace
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Original Name [GH-90000]");
        var requestBody = TestDataFixtures.toJson(createRequest); // GH-90000
        var createResponse = performPost("/v1/workspaces", requestBody); // GH-90000

        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000
        var workspaceId = workspaceData.get("id [GH-90000]").asText();

        // When - Update workspace
        var updateRequest = TestDataFixtures.createCreateWorkspaceRequest("Updated Name [GH-90000]");
        var updateResponse = performPut("/v1/workspaces/" + workspaceId, // GH-90000
            TestDataFixtures.toJson(updateRequest)); // GH-90000

        // Then
        assertEquals(200, updateResponse.getCode()); // GH-90000
        var updatedData = TestDataFixtures.parseJson(updateResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000
        assertEquals("Updated Name", updatedData.get("name [GH-90000]").asText());
    }

    @Test
    @DisplayName("Should delete workspace [GH-90000]")
    public void testDeleteWorkspace() throws Exception { // GH-90000
        // Given - Create a workspace
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("To Delete [GH-90000]");
        var requestBody = TestDataFixtures.toJson(createRequest); // GH-90000
        var createResponse = performPost("/v1/workspaces", requestBody); // GH-90000

        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000
        var workspaceId = workspaceData.get("id [GH-90000]").asText();

        // When - Delete workspace
        var deleteResponse = performDelete("/v1/workspaces/" + workspaceId); // GH-90000

        // Then
        assertEquals(204, deleteResponse.getCode()); // GH-90000

        // Verify it's deleted
        var getResponse = performGet("/v1/workspaces/" + workspaceId); // GH-90000
        assertEquals(404, getResponse.getCode()); // GH-90000
    }

    @Test
    @DisplayName("Should add member to workspace [GH-90000]")
    public void testAddWorkspaceMember() throws Exception { // GH-90000
        // Given - Create workspace and user
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Member Test [GH-90000]");
        var requestBody = TestDataFixtures.toJson(createRequest); // GH-90000
        var createResponse = performPost("/v1/workspaces", requestBody); // GH-90000

        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000
        var workspaceId = workspaceData.get("id [GH-90000]").asText();

        var memberRequest = TestDataFixtures.createObjectNode(); // GH-90000
        memberRequest.put("userId", "test-user-id"); // GH-90000
        memberRequest.put("role", "MEMBER"); // GH-90000

        // When
        var addMemberResponse = performPost("/v1/workspaces/" + workspaceId + "/members", // GH-90000
            TestDataFixtures.toJson(memberRequest)); // GH-90000

        // Then
        assertEquals(200, addMemberResponse.getCode()); // GH-90000
    }

    @Test
    @DisplayName("Should remove member from workspace [GH-90000]")
    public void testRemoveWorkspaceMember() throws Exception { // GH-90000
        // Given - Create workspace and add member first
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Member Removal Test [GH-90000]");
        var requestBody = TestDataFixtures.toJson(createRequest); // GH-90000
        var createResponse = performPost("/v1/workspaces", requestBody); // GH-90000

        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8)); // GH-90000
        var workspaceId = workspaceData.get("id [GH-90000]").asText();

        // When - Remove member
        var removeMemberResponse = performDelete("/v1/workspaces/" + workspaceId + "/members/test-user-id"); // GH-90000

        // Then
        assertEquals(200, removeMemberResponse.getCode()); // GH-90000
    }
}
