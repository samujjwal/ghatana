package com.ghatana.datacloud.entity.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents retention policies for media artifacts in Data Cloud.
 *
 * <p><b>Purpose</b><br>
 * Defines retention rules, expiration schedules, and cleanup procedures for
 * media artifacts based on classification, consent status, legal requirements,
 * and business needs. Ensures compliance with data retention regulations and
 * storage optimization.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RetentionPolicy policy = RetentionPolicy.builder()
 *     .tenantId("tenant-123")
 *     .policyName("standard-audio-retention")
 *     .classification(MediaArtifact.Classification.INTERNAL)
 *     .mediaTypes(List.of("audio/*", "video/*"))
 *     .retentionDays(365)
 *     .autoDelete(true)
 *     .build();
 * 
 * // Check if artifact matches policy
 * if (policy.matches(artifact)) {
 *     // Apply retention rules
 *     Instant expiry = policy.calculateExpiryDate(artifact);
 * }
 * 
 * // Create deletion job
 * policy.scheduleDeletion(artifact);
 * }</pre>
 *
 * @see MediaArtifact
 * @see Consent
 * @doc.type class
 * @doc.purpose Data retention policy management for media artifacts
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "media_retention_policies", indexes = {
    @Index(name = "idx_retention_tenant", columnList = "tenant_id"),
    @Index(name = "idx_retention_name", columnList = "policy_name"),
    @Index(name = "idx_retention_classification", columnList = "classification"),
    @Index(name = "idx_retention_status", columnList = "status"),
    @Index(name = "idx_retention_priority", columnList = "priority"),
    @Index(name = "idx_retention_created", columnList = "created_at")
})
public class RetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "policy_id", nullable = false, unique = true, length = 255)
    private String policyId;

    @NotNull
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @NotNull
    @Column(name = "policy_name", nullable = false, length = 255)
    private String policyName;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", length = 50)
    private MediaArtifact.Classification classification;

    /**
     * Media types this policy applies to (supports wildcards).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_types", columnDefinition = "jsonb")
    private List<String> mediaTypes = new ArrayList<>();

    /**
     * Consent status requirements.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consent_requirements", columnDefinition = "jsonb")
    private ConsentRequirements consentRequirements;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 50)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "priority", nullable = false, length = 20)
    private PolicyPriority priority = PolicyPriority.NORMAL;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "retention_months")
    private Integer retentionMonths;

    @Column(name = "retention_years")
    private Integer retentionYears;

    @Column(name = "grace_period_days")
    private Integer gracePeriodDays = 30;

    @Column(name = "auto_delete")
    private Boolean autoDelete = false;

    @Column(name = "notify_before_days")
    private Integer notifyBeforeDays = 7;

    @Column(name = "legal_hold")
    private Boolean legalHold = false;

    @Column(name = "archive_before_delete")
    private Boolean archiveBeforeDelete = false;

    /**
     * Retention conditions and exceptions.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retention_conditions", columnDefinition = "jsonb")
    private RetentionConditions retentionConditions;

    /**
     * Deletion configuration and methods.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deletion_config", columnDefinition = "jsonb")
    private DeletionConfig deletionConfig;

    /**
     * Policy metadata and tags.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_metadata", columnDefinition = "jsonb")
    private Map<String, Object> policyMetadata = new HashMap<>();

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "effective_date")
    private Instant effectiveDate;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "last_applied")
    private Instant lastApplied;

    @Column(name = "application_count")
    private Long applicationCount = 0L;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "parent_policy_id", length = 255)
    private String parentPolicyId;

    /**
     * Audit trail of policy changes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_trail", columnDefinition = "jsonb")
    private List<PolicyAuditEntry> auditTrail = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Policy status states.
     */
    public enum PolicyStatus {
        DRAFT,        // Policy is being drafted
        ACTIVE,       // Policy is active and enforced
        SUSPENDED,    // Policy is temporarily suspended
        EXPIRED,      // Policy has expired
        ARCHIVED,     // Policy is archived
        DELETED       // Policy is deleted
    }

    /**
     * Policy priority levels.
     */
    public enum PolicyPriority {
        LOW,          // Low priority
        NORMAL,       // Normal priority
        HIGH,         // High priority
        CRITICAL      // Critical priority
    }

    /**
     * Consent requirements for the policy.
     */
    public record ConsentRequirements(
        List<Consent.ConsentStatus> allowedStatuses,
        Boolean requireExplicitConsent,
        Boolean requireParentalConsent,
        Integer minConsentAgeDays,
        List<String> restrictedPurposes
    ) {
        public ConsentRequirements {
            if (allowedStatuses == null) allowedStatuses = List.of(Consent.ConsentStatus.GRANTED);
            if (requireExplicitConsent == null) requireExplicitConsent = false;
            if (requireParentalConsent == null) requireParentalConsent = false;
            if (minConsentAgeDays == null) minConsentAgeDays = 0;
            if (restrictedPurposes == null) restrictedPurposes = List.of();
        }
    }

    /**
     * Retention conditions and exceptions.
     */
    public record RetentionConditions(
        List<String> requiredTags,
        List<String> excludedTags,
        Map<String, String> metadataRequirements,
        Long minSizeBytes,
        Long maxSizeBytes,
        Integer minAgeDays,
        List<String> excludedOwners
    ) {
        public RetentionConditions {
            if (requiredTags == null) requiredTags = List.of();
            if (excludedTags == null) excludedTags = List.of();
            if (metadataRequirements == null) metadataRequirements = Map.of();
            if (minSizeBytes == null) minSizeBytes = 0L;
            if (maxSizeBytes == null) maxSizeBytes = Long.MAX_VALUE;
            if (minAgeDays == null) minAgeDays = 0;
            if (excludedOwners == null) excludedOwners = List.of();
        }
    }

    /**
     * Deletion configuration.
     */
    public record DeletionConfig(
        String deletionMethod,
        Boolean secureDelete,
        Integer confirmationRequired,
        List<String> notificationRecipients,
        Map<String, Object> deletionParameters
    ) {
        public DeletionConfig {
            if (deletionMethod == null) deletionMethod = "standard";
            if (secureDelete == null) secureDelete = false;
            if (confirmationRequired == null) confirmationRequired = 0;
            if (notificationRecipients == null) notificationRecipients = List.of();
            if (deletionParameters == null) deletionParameters = Map.of();
        }
    }

    /**
     * Audit trail entry for policy changes.
     */
    public record PolicyAuditEntry(
        Instant timestamp,
        String action,
        String performedBy,
        String reason,
        PolicyStatus previousStatus,
        PolicyStatus newStatus,
        Map<String, Object> metadata
    ) {
        public PolicyAuditEntry {
            if (timestamp == null) timestamp = Instant.now();
            if (action == null) action = "unknown";
            if (performedBy == null) performedBy = "system";
            if (reason == null) reason = "no reason provided";
            if (metadata == null) metadata = Map.of();
        }
    }

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MediaArtifact.Classification getClassification() {
        return classification;
    }

    public void setClassification(MediaArtifact.Classification classification) {
        this.classification = classification;
    }

    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    public void setMediaTypes(List<String> mediaTypes) {
        this.mediaTypes = mediaTypes;
    }

    public ConsentRequirements getConsentRequirements() {
        return consentRequirements;
    }

    public void setConsentRequirements(ConsentRequirements consentRequirements) {
        this.consentRequirements = consentRequirements;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public void setStatus(PolicyStatus status) {
        this.status = status;
    }

    public PolicyPriority getPriority() {
        return priority;
    }

    public void setPriority(PolicyPriority priority) {
        this.priority = priority;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Integer getRetentionMonths() {
        return retentionMonths;
    }

    public void setRetentionMonths(Integer retentionMonths) {
        this.retentionMonths = retentionMonths;
    }

    public Integer getRetentionYears() {
        return retentionYears;
    }

    public void setRetentionYears(Integer retentionYears) {
        this.retentionYears = retentionYears;
    }

    public Integer getGracePeriodDays() {
        return gracePeriodDays;
    }

    public void setGracePeriodDays(Integer gracePeriodDays) {
        this.gracePeriodDays = gracePeriodDays;
    }

    public Boolean getAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(Boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public Integer getNotifyBeforeDays() {
        return notifyBeforeDays;
    }

    public void setNotifyBeforeDays(Integer notifyBeforeDays) {
        this.notifyBeforeDays = notifyBeforeDays;
    }

    public Boolean getLegalHold() {
        return legalHold;
    }

    public void setLegalHold(Boolean legalHold) {
        this.legalHold = legalHold;
    }

    public Boolean getArchiveBeforeDelete() {
        return archiveBeforeDelete;
    }

    public void setArchiveBeforeDelete(Boolean archiveBeforeDelete) {
        this.archiveBeforeDelete = archiveBeforeDelete;
    }

    public RetentionConditions getRetentionConditions() {
        return retentionConditions;
    }

    public void setRetentionConditions(RetentionConditions retentionConditions) {
        this.retentionConditions = retentionConditions;
    }

    public DeletionConfig getDeletionConfig() {
        return deletionConfig;
    }

    public void setDeletionConfig(DeletionConfig deletionConfig) {
        this.deletionConfig = deletionConfig;
    }

    public Map<String, Object> getPolicyMetadata() {
        return policyMetadata;
    }

    public void setPolicyMetadata(Map<String, Object> policyMetadata) {
        this.policyMetadata = policyMetadata;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(Instant effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Instant getLastApplied() {
        return lastApplied;
    }

    public void setLastApplied(Instant lastApplied) {
        this.lastApplied = lastApplied;
    }

    public Long getApplicationCount() {
        return applicationCount;
    }

    public void setApplicationCount(Long applicationCount) {
        this.applicationCount = applicationCount;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getParentPolicyId() {
        return parentPolicyId;
    }

    public void setParentPolicyId(String parentPolicyId) {
        this.parentPolicyId = parentPolicyId;
    }

    public List<PolicyAuditEntry> getAuditTrail() {
        return auditTrail;
    }

    public void setAuditTrail(List<PolicyAuditEntry> auditTrail) {
        this.auditTrail = auditTrail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ============ Business Methods ============

    /**
     * Activates the policy.
     */
    public void activate(String performedBy, String reason) {
        PolicyStatus previousStatus = this.status;
        this.status = PolicyStatus.ACTIVE;
        this.effectiveDate = Instant.now();
        this.version++;
        
        addAuditEntry("ACTIVATED", performedBy, reason, previousStatus, PolicyStatus.ACTIVE);
    }

    /**
     * Suspends the policy.
     */
    public void suspend(String performedBy, String reason) {
        PolicyStatus previousStatus = this.status;
        this.status = PolicyStatus.SUSPENDED;
        this.version++;
        
        addAuditEntry("SUSPENDED", performedBy, reason, previousStatus, PolicyStatus.SUSPENDED);
    }

    /**
     * Expires the policy.
     */
    public void expire(String performedBy, String reason) {
        PolicyStatus previousStatus = this.status;
        this.status = PolicyStatus.EXPIRED;
        this.expiryDate = Instant.now();
        this.version++;
        
        addAuditEntry("EXPIRED", performedBy, reason, previousStatus, PolicyStatus.EXPIRED);
    }

    /**
     * Archives the policy.
     */
    public void archive(String performedBy, String reason) {
        PolicyStatus previousStatus = this.status;
        this.status = PolicyStatus.ARCHIVED;
        this.version++;
        
        addAuditEntry("ARCHIVED", performedBy, reason, previousStatus, PolicyStatus.ARCHIVED);
    }

    /**
     * Checks if the policy is currently active.
     */
    public boolean isActive() {
        if (status != PolicyStatus.ACTIVE) {
            return false;
        }
        
        // Check effective date
        if (effectiveDate != null && Instant.now().isBefore(effectiveDate)) {
            return false;
        }
        
        // Check expiry date
        if (expiryDate != null && Instant.now().isAfter(expiryDate)) {
            return false;
        }
        
        // Check legal hold
        if (Boolean.TRUE.equals(legalHold)) {
            return false;
        }
        
        return true;
    }

    /**
     * Checks if a media artifact matches this policy.
     */
    public boolean matches(MediaArtifact artifact) {
        if (!isActive()) {
            return false;
        }
        
        // Check tenant
        if (!tenantId.equals(artifact.getTenantId())) {
            return false;
        }
        
        // Check classification
        if (classification != null && classification != artifact.getClassification()) {
            return false;
        }
        
        // Check media types
        if (!mediaTypes.isEmpty() && !matchesMediaType(artifact.getMediaType())) {
            return false;
        }
        
        // Check consent requirements
        if (consentRequirements != null && !matchesConsentRequirements(artifact)) {
            return false;
        }
        
        // Check retention conditions
        if (retentionConditions != null && !matchesRetentionConditions(artifact)) {
            return false;
        }
        
        return true;
    }

    /**
     * Checks if media type matches the policy patterns.
     */
    private boolean matchesMediaType(String mediaType) {
        return mediaTypes.stream()
            .anyMatch(pattern -> matchesPattern(mediaType, pattern));
    }

    /**
     * Simple pattern matching for media types.
     */
    private boolean matchesPattern(String text, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return text.equals(pattern);
    }

    /**
     * Checks if artifact meets consent requirements.
     */
    private boolean matchesConsentRequirements(MediaArtifact artifact) {
        return artifact.hasValidConsent();
    }

    /**
     * Checks if artifact meets retention conditions.
     */
    private boolean matchesRetentionConditions(MediaArtifact artifact) {
        // Check size requirements
        if (artifact.getSizeBytes() < retentionConditions.minSizeBytes() ||
            artifact.getSizeBytes() > retentionConditions.maxSizeBytes()) {
            return false;
        }
        
        // Check age requirements
        if (artifact.getAgeDays() < retentionConditions.minAgeDays()) {
            return false;
        }
        
        // Check excluded owners
        if (retentionConditions.excludedOwners().contains(artifact.getOwnerId())) {
            return false;
        }
        
        return true;
    }

    /**
     * Calculates the expiry date for a media artifact.
     */
    public Instant calculateExpiryDate(MediaArtifact artifact, Instant createdAt) {
        Instant baseDate = createdAt != null ? createdAt : artifact.getCreatedAt();
        if (baseDate == null) {
            baseDate = Instant.now();
        }
        
        Instant expiry = baseDate;
        
        // Add years
        if (retentionYears != null && retentionYears > 0) {
            expiry = expiry.plus(java.time.Duration.ofDays(retentionYears * 365L));
        }
        
        // Add months
        if (retentionMonths != null && retentionMonths > 0) {
            expiry = expiry.plus(java.time.Duration.ofDays(retentionMonths * 30L));
        }
        
        // Add days
        if (retentionDays != null && retentionDays > 0) {
            expiry = expiry.plus(java.time.Duration.ofDays(retentionDays));
        }
        
        return expiry;
    }

    /**
     * Calculates the notification date for a media artifact.
     */
    public Instant calculateNotificationDate(MediaArtifact artifact, Instant createdAt) {
        Instant expiry = calculateExpiryDate(artifact, createdAt);
        if (notifyBeforeDays != null && notifyBeforeDays > 0) {
            return expiry.minus(java.time.Duration.ofDays(notifyBeforeDays));
        }
        return expiry;
    }

    /**
     * Gets the total retention period in days.
     */
    public Integer getTotalRetentionDays() {
        int totalDays = 0;
        
        if (retentionYears != null) {
            totalDays += retentionYears * 365;
        }
        if (retentionMonths != null) {
            totalDays += retentionMonths * 30;
        }
        if (retentionDays != null) {
            totalDays += retentionDays;
        }
        
        return totalDays > 0 ? totalDays : null;
    }

    /**
     * Records policy application.
     */
    public void recordApplication() {
        this.lastApplied = Instant.now();
        this.applicationCount++;
    }

    /**
     * Adds an audit entry for policy changes.
     */
    public void addAuditEntry(String action, String performedBy, String reason, 
                             PolicyStatus previousStatus, PolicyStatus newStatus) {
        PolicyAuditEntry entry = new PolicyAuditEntry(
            Instant.now(),
            action,
            performedBy,
            reason,
            previousStatus,
            newStatus,
            Map.of("version", version)
        );
        
        auditTrail.add(entry);
    }

    /**
     * Gets the most recent audit entry.
     */
    public PolicyAuditEntry getLastAuditEntry() {
        if (auditTrail.isEmpty()) {
            return null;
        }
        return auditTrail.get(auditTrail.size() - 1);
    }

    /**
     * Gets a summary of the retention policy.
     */
    public String getSummary() {
        return String.format(
            "RetentionPolicy %s: %s, %s priority, %d days retention, %s",
            policyId,
            status,
            priority,
            getTotalRetentionDays(),
            autoDelete ? "auto-delete" : "manual delete"
        );
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Generate policy ID if not provided
        if (policyId == null) {
            policyId = "policy-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        // Add initial audit entry
        if (auditTrail.isEmpty()) {
            addAuditEntry("CREATED", createdBy != null ? createdBy : "system", 
                         "Policy record created", null, status);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String policyId;
        private String tenantId;
        private String policyName;
        private String description;
        private MediaArtifact.Classification classification;
        private List<String> mediaTypes = new ArrayList<>();
        private ConsentRequirements consentRequirements;
        private PolicyStatus status = PolicyStatus.ACTIVE;
        private PolicyPriority priority = PolicyPriority.NORMAL;
        private Integer retentionDays;
        private Integer retentionMonths;
        private Integer retentionYears;
        private Integer gracePeriodDays = 30;
        private Boolean autoDelete = false;
        private Integer notifyBeforeDays = 7;
        private Boolean legalHold = false;
        private Boolean archiveBeforeDelete = false;
        private RetentionConditions retentionConditions;
        private DeletionConfig deletionConfig;
        private Map<String, Object> policyMetadata = new HashMap<>();
        private String createdBy;
        private String approvedBy;
        private Instant effectiveDate;
        private Instant expiryDate;
        private Instant lastApplied;
        private Long applicationCount = 0L;
        private Integer version = 1;
        private String parentPolicyId;
        private List<PolicyAuditEntry> auditTrail = new ArrayList<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder policyId(String policyId) {
            this.policyId = policyId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder policyName(String policyName) {
            this.policyName = policyName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder classification(MediaArtifact.Classification classification) {
            this.classification = classification;
            return this;
        }

        public Builder mediaTypes(List<String> mediaTypes) {
            this.mediaTypes = mediaTypes;
            return this;
        }

        public Builder consentRequirements(ConsentRequirements consentRequirements) {
            this.consentRequirements = consentRequirements;
            return this;
        }

        public Builder status(PolicyStatus status) {
            this.status = status;
            return this;
        }

        public Builder priority(PolicyPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder retentionDays(Integer retentionDays) {
            this.retentionDays = retentionDays;
            return this;
        }

        public Builder retentionMonths(Integer retentionMonths) {
            this.retentionMonths = retentionMonths;
            return this;
        }

        public Builder retentionYears(Integer retentionYears) {
            this.retentionYears = retentionYears;
            return this;
        }

        public Builder gracePeriodDays(Integer gracePeriodDays) {
            this.gracePeriodDays = gracePeriodDays;
            return this;
        }

        public Builder autoDelete(Boolean autoDelete) {
            this.autoDelete = autoDelete;
            return this;
        }

        public Builder notifyBeforeDays(Integer notifyBeforeDays) {
            this.notifyBeforeDays = notifyBeforeDays;
            return this;
        }

        public Builder legalHold(Boolean legalHold) {
            this.legalHold = legalHold;
            return this;
        }

        public Builder archiveBeforeDelete(Boolean archiveBeforeDelete) {
            this.archiveBeforeDelete = archiveBeforeDelete;
            return this;
        }

        public Builder retentionConditions(RetentionConditions retentionConditions) {
            this.retentionConditions = retentionConditions;
            return this;
        }

        public Builder deletionConfig(DeletionConfig deletionConfig) {
            this.deletionConfig = deletionConfig;
            return this;
        }

        public Builder policyMetadata(Map<String, Object> policyMetadata) {
            this.policyMetadata = policyMetadata;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder approvedBy(String approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        public Builder effectiveDate(Instant effectiveDate) {
            this.effectiveDate = effectiveDate;
            return this;
        }

        public Builder expiryDate(Instant expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder lastApplied(Instant lastApplied) {
            this.lastApplied = lastApplied;
            return this;
        }

        public Builder applicationCount(Long applicationCount) {
            this.applicationCount = applicationCount;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder parentPolicyId(String parentPolicyId) {
            this.parentPolicyId = parentPolicyId;
            return this;
        }

        public Builder auditTrail(List<PolicyAuditEntry> auditTrail) {
            this.auditTrail = auditTrail;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public RetentionPolicy build() {
            RetentionPolicy policy = new RetentionPolicy();
            policy.id = this.id;
            policy.policyId = this.policyId;
            policy.tenantId = this.tenantId;
            policy.policyName = this.policyName;
            policy.description = this.description;
            policy.classification = this.classification;
            policy.mediaTypes = this.mediaTypes;
            policy.consentRequirements = this.consentRequirements;
            policy.status = this.status;
            policy.priority = this.priority;
            policy.retentionDays = this.retentionDays;
            policy.retentionMonths = this.retentionMonths;
            policy.retentionYears = this.retentionYears;
            policy.gracePeriodDays = this.gracePeriodDays;
            policy.autoDelete = this.autoDelete;
            policy.notifyBeforeDays = this.notifyBeforeDays;
            policy.legalHold = this.legalHold;
            policy.archiveBeforeDelete = this.archiveBeforeDelete;
            policy.retentionConditions = this.retentionConditions;
            policy.deletionConfig = this.deletionConfig;
            policy.policyMetadata = this.policyMetadata;
            policy.createdBy = this.createdBy;
            policy.approvedBy = this.approvedBy;
            policy.effectiveDate = this.effectiveDate;
            policy.expiryDate = this.expiryDate;
            policy.lastApplied = this.lastApplied;
            policy.applicationCount = this.applicationCount;
            policy.version = this.version;
            policy.parentPolicyId = this.parentPolicyId;
            policy.auditTrail = this.auditTrail;
            policy.createdAt = this.createdAt;
            policy.updatedAt = this.updatedAt;
            return policy;
        }
    }

    @Override
    public String toString() {
        return "RetentionPolicy{" +
                "policyId='" + policyId + '\'' +
                ", policyName='" + policyName + '\'' +
                ", status=" + status +
                ", priority=" + priority +
                ", retentionDays=" + retentionDays +
                '}';
    }
}
