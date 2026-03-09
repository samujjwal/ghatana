package com.ghatana.requirements.api.rest.dto;

import com.ghatana.requirements.domain.project.ProjectStatus;

/**
 * Request to update a project.
 *
 * @param name Project name (optional)
 * @param description Project description (optional)
 * @param status Project status (optional)
 
 * @doc.type record
 * @doc.purpose Immutable data carrier for update project request
 * @doc.layer core
 * @doc.pattern DTO
*/
public record UpdateProjectRequest(
    String name,
    String description,
    ProjectStatus status
) {
}

