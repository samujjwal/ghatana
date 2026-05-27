package com.ghatana.phr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;
import java.util.Set;

/**
 * Request DTO for creating a consent grant.
 *
 * @doc.type class
 * @doc.purpose Request DTO for consent grant creation with validation
 * @doc.layer product
 * @doc.pattern DTO
 */
public class CreateConsentGrantRequest {

    @NotBlank(message = "patientId is required")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]{8,64}$", message = "patientId must be 8-64 alphanumeric characters")
    private String patientId;

    @NotBlank(message = "recipientId is required")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]{8,64}$", message = "recipientId must be 8-64 alphanumeric characters")
    private String recipientId;

    @NotNull(message = "scope is required")
    @Valid
    private ConsentScopeDto scope;

    @NotBlank(message = "expiresAt is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$", message = "expiresAt must be ISO-8601 format")
    private String expiresAt;

    @Pattern(regexp = "^(ACTIVE|REVOKED|EXPIRED)$", message = "status must be ACTIVE, REVOKED, or EXPIRED")
    private String status;

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public ConsentScopeDto getScope() {
        return scope;
    }

    public void setScope(ConsentScopeDto scope) {
        this.scope = scope;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * DTO for consent scope with resource, action, and field-level access.
     */
    public static class ConsentScopeDto {

        private Set<@Pattern(regexp = "^[a-z0-9\\-]+$", message = "resourceType must be lowercase alphanumeric") String> resourceTypes;

        private boolean allDocuments = false;

        private Set<@Pattern(regexp = "^[a-zA-Z0-9\\-]{8,64}$", message = "documentId must be 8-64 alphanumeric characters") String> specificDocumentIds;

        private Set<@Pattern(regexp = "^(?i)(READ|WRITE)$", message = "action must be READ or WRITE") String> actions;

        private Map<String, Set<@Pattern(regexp = "^[a-z0-9_]+$", message = "field name must be lowercase alphanumeric") String>> fieldLevelAccess;

        @JsonProperty("resourceTypes")
        public Set<String> getResourceTypes() {
            return resourceTypes;
        }

        public void setResourceTypes(Set<String> resourceTypes) {
            this.resourceTypes = resourceTypes;
        }

        @JsonProperty("allDocuments")
        public boolean isAllDocuments() {
            return allDocuments;
        }

        public void setAllDocuments(boolean allDocuments) {
            this.allDocuments = allDocuments;
        }

        @JsonProperty("specificDocumentIds")
        public Set<String> getSpecificDocumentIds() {
            return specificDocumentIds;
        }

        public void setSpecificDocumentIds(Set<String> specificDocumentIds) {
            this.specificDocumentIds = specificDocumentIds;
        }

        @JsonProperty("actions")
        public Set<String> getActions() {
            return actions;
        }

        public void setActions(Set<String> actions) {
            this.actions = actions;
        }

        @JsonProperty("fieldLevelAccess")
        public Map<String, Set<String>> getFieldLevelAccess() {
            return fieldLevelAccess;
        }

        public void setFieldLevelAccess(Map<String, Set<String>> fieldLevelAccess) {
            this.fieldLevelAccess = fieldLevelAccess;
        }
    }
}
