package com.ghatana.requirements.api.rest.dto;

/**
 * Request to create a new project.
 *
 * @param workspaceId Workspace ID (required)
 * @param name Project name (required)
 * @param description Project description (optional)
 * @param template Project template name (optional)
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for create project request
 * @doc.layer core
 * @doc.pattern DTO
*/
public record CreateProjectRequest(
    String workspaceId,
    String name,
    String description,
    String template
) {
}

