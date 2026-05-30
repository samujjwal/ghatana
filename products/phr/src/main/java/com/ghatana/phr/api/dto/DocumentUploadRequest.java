package com.ghatana.phr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for document upload.
 *
 * @doc.type class
 * @doc.purpose Request DTO for document upload with validation
 * @doc.layer product
 * @doc.pattern DTO
 */
public class DocumentUploadRequest {

    @NotBlank(message = "patientId is required")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]{8,64}$", message = "patientId must be 8-64 alphanumeric characters")
    private String patientId;

    @NotBlank(message = "documentType is required")
    @Pattern(regexp = "^[a-z0-9\\-]+$", message = "documentType must be lowercase alphanumeric")
    private String documentType;

    @NotBlank(message = "title is required")
    @Size(min = 1, max = 200, message = "title must be 1-200 characters")
    private String title;

    @Size(max = 1000, message = "description must be at most 1000 characters")
    private String description;

    @NotBlank(message = "contentType is required")
    @Pattern(regexp = "^(application/pdf|image/jpeg|image/png|image/tiff|application/msword|application/vnd.openxmlformats-officedocument.wordprocessingml.document)$",
             message = "contentType must be PDF, JPEG, PNG, TIFF, DOC, or DOCX")
    private String contentType;

    @NotBlank(message = "content is required")
    @Size(min = 1, message = "content is required")
    private String content; // Base64 encoded

    @NotBlank(message = "contentHash is required")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "contentHash must be SHA-256 hash (64 hex characters)")
    private String contentHash;

    @NotBlank(message = "visibility is required")
    @Pattern(regexp = "^(private|provider|public)$", message = "visibility must be private, provider, or public")
    private String visibility;

    @JsonProperty("patientId")
    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    @JsonProperty("documentType")
    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("contentType")
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @JsonProperty("content")
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @JsonProperty("contentHash")
    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    @JsonProperty("visibility")
    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }
}
