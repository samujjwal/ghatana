package com.ghatana.pipeline.registry.model;

import com.ghatana.platform.domain.auth.TenantId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a pattern definition metadata in the system.
 *
 * <p>
 * <b>Purpose</b><br>
 * Domain model for pattern specifications metadata. Stores only metadata about
 * pattern registration, version history, and lifecycle state. Runtime execution
 * artifacts (compiled detection plans) are stored separately by the execution engine.
 *
 * <p>
 * <b>Properties</b><br>
 * - id: Unique pattern identifier (UUID) - tenantId: Tenant owner (multi-tenant
 * isolation) - name: Human-readable pattern name - specification: Pattern
 * specification string (e.g., "SEQ(A,B,C)") - version: Incremented on each
 * update - status: Pattern lifecycle state (DRAFT, COMPILED, ACTIVE, INACTIVE)
 * - detectionPlanId: Reference to separately stored compiled detection plan
 * (null for DRAFT, set after compilation) - confidence: Pattern match confidence
 * score (0-100) - tags: Searchable tags for categorization - agentHints:
 * Metadata hints for agent-driven discovery (optional) - createdAt/updatedAt:
 * Audit timestamps - createdBy/updatedBy: User identifiers for audit trail
 *
 * <p>
 * <b>Lifecycle</b><br>
 * DRAFT → COMPILED → ACTIVE ↔ INACTIVE → DELETED
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * Pattern pattern = Pattern.builder()
 *     .tenantId(tenantId)
 *     .name("fraud_pattern")
 *     .specification("SEQ(login_failed, transaction)")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Domain model for pattern definition metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pattern {

    private String id;
    private TenantId tenantId;

    @Size(min = 1, max = 255, message = "Pattern name must be between 1 and 255 characters")
    @NotBlank(message = "Pattern name is required")
    private String name;

    @NotBlank(message = "Pattern specification is required")
    private String specification;

    private int version;

    @Builder.Default
    private String status = "DRAFT";

    /**
     * Reference to separately stored compiled detection plan.
     * Null for DRAFT patterns, set after compilation by the execution engine.
     * This separates metadata from runtime execution artifacts.
     */
    private String detectionPlanId;

    @Builder.Default
    private int confidence = 0;

    @Size(max = 2000, message = "Pattern description cannot exceed 2000 characters")
    private String description;

    @Builder.Default
    private List<@NotBlank(message = "Tag cannot be blank") String> tags = new ArrayList<>();

    @Size(max = 1000, message = "Agent hints cannot exceed 1000 characters")
    private String agentHints;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    private long versionControl;

    /**
     * Creates a new instance with the specified ID.
     *
     * @param id the ID to set
     * @return a new instance with the specified ID
     */
    public static Pattern withId(String id) {
        Pattern pattern = new Pattern();
        pattern.setId(id);
        return pattern;
    }

    /**
     * Creates a new instance with specified name, tenant, and specification.
     *
     * @param name the pattern name
     * @param tenantId the tenant owner
     * @param specification the pattern specification (e.g., "SEQ(A,B)")
     * @return a new instance
     */
    public static Pattern create(String name, TenantId tenantId, String specification) {
        Pattern pattern = new Pattern();
        pattern.setName(name);
        pattern.setTenantId(tenantId);
        pattern.setSpecification(specification);
        pattern.setVersion(1);
        pattern.setStatus("DRAFT");
        pattern.setCreatedAt(Instant.now());
        pattern.setUpdatedAt(Instant.now());
        return pattern;
    }

    /**
     * Creates a new version of this pattern.
     *
     * @return a new version with incremented version number
     */
    public Pattern newVersion() {
        Pattern newPattern = new Pattern();
        newPattern.setTenantId(this.tenantId);
        newPattern.setName(this.name);
        newPattern.setSpecification(this.specification);
        newPattern.setVersion(this.version + 1);
        newPattern.setStatus("DRAFT");
        newPattern.setDescription(this.description);
        newPattern.setConfidence(this.confidence);
        newPattern.setAgentHints(this.agentHints);
        newPattern.setCreatedAt(Instant.now());
        newPattern.setUpdatedAt(Instant.now());
        newPattern.setCreatedBy(this.createdBy);
        newPattern.setUpdatedBy(this.updatedBy);
        newPattern.setTags(new ArrayList<>(this.tags));
        return newPattern;
    }

    /**
     * Marks pattern as compiled with detection plan reference.
     *
     * @param planId the ID of the separately stored compiled detection plan
     * @param confidence the match confidence (0-100)
     * @return this pattern with status updated to COMPILED
     */
    public Pattern withCompiledPlan(String planId, int confidence) {
        this.detectionPlanId = planId;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.status = "COMPILED";
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Marks pattern as active for execution.
     *
     * @return this pattern with status updated to ACTIVE
     */
    public Pattern activate() {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Marks pattern as inactive.
     *
     * @return this pattern with status updated to INACTIVE
     */
    public Pattern deactivate() {
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
        return this;
    }
}
