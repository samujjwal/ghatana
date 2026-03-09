package com.ghatana.products.yappc.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Request DTO for creating a new ScanFinding.
 *
 * <p>This is the canonical DTO for creating findings. All modules should use this
 * class from {@code libs:yappc-domain} instead of creating local duplicates.</p>
 *
 * @doc.type class
 * @doc.purpose Encapsulates the data required to create a new scan finding
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CreateFindingRequest {

    /**
     * The ID of the scan job this finding belongs to.
     */
    @NotNull(message = "Scan job ID is required")
    private UUID scanJobId;

    /**
     * The type of finding (e.g., VULNERABILITY, CODE_SMELL, BUG).
     */
    @NotBlank(message = "Finding type is required")
    @Size(max = 100, message = "Finding type must not exceed 100 characters")
    private String findingType;

    /**
     * The severity level (e.g., CRITICAL, HIGH, MEDIUM, LOW, INFO).
     */
    @NotBlank(message = "Severity is required")
    @Size(max = 50, message = "Severity must not exceed 50 characters")
    private String severity;

    /**
     * A short title describing the finding.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    /**
     * Detailed description of the finding.
     */
    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    private String description;

    /**
     * Location information (file path, line numbers, etc.) as a flexible map.
     */
    private Map<String, Object> location;

    /**
     * Suggested remediation steps.
     */
    @Size(max = 4000, message = "Remediation must not exceed 4000 characters")
    private String remediation;

    /**
     * Current status of the finding (e.g., OPEN, RESOLVED, IGNORED).
     */
    @Size(max = 50, message = "Status must not exceed 50 characters")
    private String status;

    /**
     * Flag indicating if this finding has been marked as a false positive.
     */
    private boolean falsePositive;

    /**
     * Creates a new CreateFindingRequest with the minimum required fields.
     *
     * @param scanJobId   the scan job ID
     * @param findingType the type of finding
     * @param severity    the severity level
     * @param title       the finding title
     * @return a new CreateFindingRequest instance
     */
    public static CreateFindingRequest of(UUID scanJobId, String findingType, String severity, String title) {
        return CreateFindingRequest.builder()
                .scanJobId(Objects.requireNonNull(scanJobId, "scanJobId must not be null"))
                .findingType(Objects.requireNonNull(findingType, "findingType must not be null"))
                .severity(Objects.requireNonNull(severity, "severity must not be null"))
                .title(Objects.requireNonNull(title, "title must not be null"))
                .build();
    }
}
