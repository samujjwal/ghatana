package com.ghatana.yappc.ai.requirements.api.rest.dto;

/**
 * Request to update a workspace.
 *
 * @param name Workspace name (optional)
 * @param description Workspace description (optional)
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for update workspace request
 * @doc.layer core
 * @doc.pattern DTO
*/
public record UpdateWorkspaceRequest(String name, String description) {
}

