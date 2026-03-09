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
 * Request DTO for creating a new ScanJob.
 *
 * <p>This is the canonical DTO for creating scan jobs. All modules should use this
 * class from {@code libs:yappc-domain} instead of creating local duplicates.</p>
 *
 * @doc.type class
 * @doc.purpose Encapsulates the data required to initiate a new security scan
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CreateScanRequest {

    /**
     * The project ID to scan.
     */
    @NotNull(message = "Project ID is required")
    private UUID projectId;

    /**
     * The type of scan to perform (e.g., SAST, DAST, SCA, FULL).
     */
    @NotBlank(message = "Scan type is required")
    @Size(max = 50, message = "Scan type must not exceed 50 characters")
    private String scanType;

    /**
     * Additional configuration options for the scan as a flexible map.
     * Can include options like exclude patterns, timeout settings, etc.
     */
    private Map<String, Object> config;

    /**
     * Optional description or notes for this scan run.
     */
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    /**
     * Optional priority level for scan execution (1-10, higher = more urgent).
     */
    private Integer priority;

    /**
     * Creates a new CreateScanRequest with the minimum required fields.
     *
     * @param projectId the project ID to scan
     * @param scanType  the type of scan to perform
     * @return a new CreateScanRequest instance
     */
    public static CreateScanRequest of(UUID projectId, String scanType) {
        return CreateScanRequest.builder()
                .projectId(Objects.requireNonNull(projectId, "projectId must not be null"))
                .scanType(Objects.requireNonNull(scanType, "scanType must not be null"))
                .build();
    }

    /**
     * Creates a new CreateScanRequest with all common fields.
     *
     * @param projectId   the project ID to scan
     * @param scanType    the type of scan to perform
     * @param config      optional configuration map
     * @param description optional description
     * @return a new CreateScanRequest instance
     */
    public static CreateScanRequest of(UUID projectId, String scanType, Map<String, Object> config, String description) {
        return CreateScanRequest.builder()
                .projectId(Objects.requireNonNull(projectId, "projectId must not be null"))
                .scanType(Objects.requireNonNull(scanType, "scanType must not be null"))
                .config(config)
                .description(description)
                .build();
    }
}
