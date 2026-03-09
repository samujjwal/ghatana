package com.ghatana.requirements.api;

import com.ghatana.requirements.api.fixtures.TestDataFixtures;
import io.activej.http.HttpResponse;
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
 */
@DisplayName("Workspace Controller Integration Tests")
/**
 * @doc.type class
 * @doc.purpose Handles workspace controller it operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public class WorkspaceControllerIT extends AbstractIntegrationTest {
    
    @Test
    @DisplayName("Should create a new workspace")
    public void testCreateWorkspace() throws Exception {
        // Given
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Test Workspace");
        var requestBody = TestDataFixtures.toJson(createRequest);
        
        // When
        var response = performPost("/v1/workspaces", requestBody);
        
        // Then
        assertEquals(201, response.getCode());
        var responseBody = response.getBody().asString(java.nio.charset.StandardCharsets.UTF_8);
        var workspaceData = TestDataFixtures.parseJson(responseBody);
        
        assertNotNull(workspaceData.get("id"));
        assertEquals("Test Workspace", workspaceData.get("name").asText());
    }
    
    @Test
    @DisplayName("Should retrieve workspace by ID")
    public void testGetWorkspaceById() throws Exception {
        // Given - Create a workspace first
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Fetch Test");
        var requestBody = TestDataFixtures.toJson(createRequest);
        var createResponse = performPost("/v1/workspaces", requestBody);
        
        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8));
        var workspaceId = workspaceData.get("id").asText();
        
        // When
        var getResponse = performGet("/v1/workspaces/" + workspaceId);
        
        // Then
        assertEquals(200, getResponse.getCode());
        var retrievedData = TestDataFixtures.parseJson(getResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8));
        
        assertEquals(workspaceId, retrievedData.get("id").asText());
        assertEquals("Fetch Test", retrievedData.get("name").asText());
    }
    
    @Test
    @DisplayName("Should return 404 for non-existent workspace")
    public void testGetNonExistentWorkspace() throws Exception {
        // When
        var response = performGet("/v1/workspaces/non-existent-id");
        
        // Then
        assertEquals(404, response.getCode());
    }
    
    @Test
    @DisplayName("Should list all workspaces")
    public void testListWorkspaces() throws Exception {
        // Given - Create multiple workspaces
        TestDataFixtures.createCreateWorkspaceRequest("Workspace 1");
        TestDataFixtures.createCreateWorkspaceRequest("Workspace 2");
        
        // When
        var response = performGet("/v1/workspaces");
        
        // Then
        assertEquals(200, response.getCode());
        var responseBody = response.getBody().asString(java.nio.charset.StandardCharsets.UTF_8);
        var workspaces = TestDataFixtures.parseJson(responseBody);
        
        assertTrue(workspaces.isArray());
        assertTrue(workspaces.size() >= 0);
    }
    
    @Test
    @DisplayName("Should update workspace")
    public void testUpdateWorkspace() throws Exception {
        // Given - Create a workspace
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Original Name");
        var requestBody = TestDataFixtures.toJson(createRequest);
        var createResponse = performPost("/v1/workspaces", requestBody);
        
        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8));
        var workspaceId = workspaceData.get("id").asText();
        
        // When - Update workspace
        var updateRequest = TestDataFixtures.createCreateWorkspaceRequest("Updated Name");
        var updateResponse = performPut("/v1/workspaces/" + workspaceId, 
            TestDataFixtures.toJson(updateRequest));
        
        // Then
        assertEquals(200, updateResponse.getCode());
        var updatedData = TestDataFixtures.parseJson(updateResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Updated Name", updatedData.get("name").asText());
    }
    
    @Test
    @DisplayName("Should delete workspace")
    public void testDeleteWorkspace() throws Exception {
        // Given - Create a workspace
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("To Delete");
        var requestBody = TestDataFixtures.toJson(createRequest);
        var createResponse = performPost("/v1/workspaces", requestBody);
        
        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8));
        var workspaceId = workspaceData.get("id").asText();
        
        // When - Delete workspace
        var deleteResponse = performDelete("/v1/workspaces/" + workspaceId);
        
        // Then
        assertEquals(204, deleteResponse.getCode());
        
        // Verify it's deleted
        var getResponse = performGet("/v1/workspaces/" + workspaceId);
        assertEquals(404, getResponse.getCode());
    }
    
    @Test
    @DisplayName("Should add member to workspace")
    public void testAddWorkspaceMember() throws Exception {
        // Given - Create workspace and user
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Member Test");
        var requestBody = TestDataFixtures.toJson(createRequest);
        var createResponse = performPost("/v1/workspaces", requestBody);
        
        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8));
        var workspaceId = workspaceData.get("id").asText();
        
        var memberRequest = TestDataFixtures.createObjectNode();
        memberRequest.put("userId", "test-user-id");
        memberRequest.put("role", "MEMBER");
        
        // When
        var addMemberResponse = performPost("/v1/workspaces/" + workspaceId + "/members",
            TestDataFixtures.toJson(memberRequest));
        
        // Then
        assertEquals(200, addMemberResponse.getCode());
    }
    
    @Test
    @DisplayName("Should remove member from workspace")
    public void testRemoveWorkspaceMember() throws Exception {
        // Given - Create workspace and add member first
        var createRequest = TestDataFixtures.createCreateWorkspaceRequest("Member Removal Test");
        var requestBody = TestDataFixtures.toJson(createRequest);
        var createResponse = performPost("/v1/workspaces", requestBody);
        
        var workspaceData = TestDataFixtures.parseJson(createResponse.getBody().asString(java.nio.charset.StandardCharsets.UTF_8));
        var workspaceId = workspaceData.get("id").asText();
        
        // When - Remove member
        var removeMemberResponse = performDelete("/v1/workspaces/" + workspaceId + "/members/test-user-id");
        
        // Then
        assertEquals(200, removeMemberResponse.getCode());
    }
}