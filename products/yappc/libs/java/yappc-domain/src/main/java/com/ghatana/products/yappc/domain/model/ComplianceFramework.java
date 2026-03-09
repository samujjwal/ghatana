package com.ghatana.products.yappc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing a compliance framework in the YAPPC platform.
 *
 * <p>A ComplianceFramework defines a set of security and compliance controls
 * that organizations must adhere to (e.g., SOC 2, PCI-DSS, HIPAA, GDPR).
 * It serves as a template for compliance assessments.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a compliance standard or framework for security assessments
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "compliance_frameworks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ComplianceFramework {

    /**
     * Unique identifier for the framework.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Short name of the framework (e.g., SOC2, PCI-DSS).
     */
    @NotBlank(message = "Framework name is required")
    @Size(max = 100, message = "Framework name must not exceed 100 characters")
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Full display name of the framework.
     */
    @Size(max = 255, message = "Display name must not exceed 255 characters")
    @Column(name = "display_name")
    private String displayName;

    /**
     * Description of the framework.
     */
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Version of the framework.
     */
    @Size(max = 50, message = "Version must not exceed 50 characters")
    @Column(name = "framework_version")
    private String frameworkVersion;

    /**
     * Category of the framework (e.g., SECURITY, PRIVACY, INDUSTRY).
     */
    @Size(max = 100, message = "Category must not exceed 100 characters")
    @Column(name = "category")
    private String category;

    /**
     * URL to official framework documentation.
     */
    @Size(max = 500, message = "Documentation URL must not exceed 500 characters")
    @Column(name = "documentation_url")
    private String documentationUrl;

    /**
     * Whether this framework is enabled by default for new workspaces.
     */
    @Column(name = "enabled_by_default")
    @Builder.Default
    private boolean enabledByDefault = false;

    /**
     * Whether this is a built-in framework or custom.
     */
    @Column(name = "is_builtin")
    @Builder.Default
    private boolean isBuiltin = false;

    /**
     * Total number of controls in this framework.
     */
    @Column(name = "control_count")
    @Builder.Default
    private int controlCount = 0;

    /**
     * Timestamp when the framework was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the framework was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Version for optimistic locking.
     */
    @Column(name = "version")
    @Builder.Default
    private int version = 0;

    /**
     * Creates a new ComplianceFramework with the minimum required fields.
     *
     * @param name the framework name
     * @return a new ComplianceFramework instance
     */
    public static ComplianceFramework of(String name) {
        Instant now = Instant.now();
        return ComplianceFramework.builder()
                .name(Objects.requireNonNull(name, "name must not be null"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Creates a new built-in ComplianceFramework.
     *
     * @param name        the framework name
     * @param displayName the display name
     * @param description the description
     * @return a new ComplianceFramework instance
     */
    public static ComplianceFramework builtin(String name, String displayName, String description) {
        Instant now = Instant.now();
        return ComplianceFramework.builder()
                .name(Objects.requireNonNull(name, "name must not be null"))
                .displayName(displayName)
                .description(description)
                .isBuiltin(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplianceFramework that = (ComplianceFramework) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
