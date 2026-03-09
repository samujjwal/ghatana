package com.ghatana.requirements.api.rest.dto;

/**
 * Project response DTO.
 *
 * @param projectId Project ID
 * @param workspaceId Workspace ID
 * @param name Project name
 * @param description Project description
 * @param status Project status
 * @param createdAt Created timestamp
 * @param updatedAt Updated timestamp
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for project response
 * @doc.layer core
 * @doc.pattern DTO
*/
public record ProjectResponse(
    String projectId,
    String workspaceId,
    String name,
    String description,
    String status,
    String createdAt,
    String updatedAt
) {
}

