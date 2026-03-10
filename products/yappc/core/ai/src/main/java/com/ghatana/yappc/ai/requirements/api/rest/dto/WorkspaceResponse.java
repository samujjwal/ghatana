package com.ghatana.yappc.ai.requirements.api.rest.dto;

/**
 * Workspace response DTO.
 *
 * @param workspaceId Workspace ID
 * @param name Workspace name
 * @param description Workspace description
 * @param ownerId Owner user ID
 * @param status Workspace status
 * @param createdAt Created timestamp
 * @param updatedAt Updated timestamp
 * @param memberCount Number of members
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for workspace response
 * @doc.layer core
 * @doc.pattern DTO
*/
public record WorkspaceResponse(
    String workspaceId,
    String name,
    String description,
    String ownerId,
    String status,
    String createdAt,
    String updatedAt,
    int memberCount
) {
}

