package com.ghatana.yappc.ai.requirements.api.rest.dto;

/**
 * Request to create a new workspace.
 *
 * @param name Workspace name (required)
 * @param description Workspace description (optional)
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for create workspace request
 * @doc.layer core
 * @doc.pattern DTO
*/
public record CreateWorkspaceRequest(String name, String description) {
}

