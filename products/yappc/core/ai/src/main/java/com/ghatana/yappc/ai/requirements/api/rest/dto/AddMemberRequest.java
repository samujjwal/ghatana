package com.ghatana.yappc.ai.requirements.api.rest.dto;

import com.ghatana.yappc.ai.requirements.domain.workspace.WorkspaceRole;

/**
 * Request to add a member to workspace.
 *
 * @param userId User ID to add (required)
 * @param role Role to assign (required)
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for add member request
 * @doc.layer core
 * @doc.pattern DTO
*/
public record AddMemberRequest(String userId, WorkspaceRole role) {
}

