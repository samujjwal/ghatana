package com.ghatana.datacloud.entity.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Represents consent information for media artifacts containing PII or biometric data.
 *
 * <p><b>Purpose</b><br>
 * Tracks consent status, scope, and lifecycle for media artifacts that may contain
 * personally identifiable information (PII) or biometric data. Provides audit trail
 * for compliance with privacy regulations like GDPR, CCPA, and biometric data laws.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Consent consent = Consent.builder()
 *     .mediaArtifact(artifact)
 *     .consentType(ConsentType.EXPLICIT)
 *     .consentStatus(ConsentStatus.GRANTED)
 *     .consentGiver("user-123")
 *     .purpose(List.of("transcription", "analysis"))
 *     .retentionDays(365)
 *     .build();
 * 
 * // Check if consent is valid
 * if (consent.isValid()) {
 *     // Process the media artifact
 * }
 * 
 * // Revoke consent
 * consent.revoke("User requested data deletion");
 * }</pre>
 *
 * @see MediaArtifact
 * @see RetentionPolicy
 * @doc.type class
 * @doc.purpose Consent tracking for PII/biometric data compliance
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "media_consent", indexes = {
    @Index(name = "idx_consent_tenant", columnList = "tenant_id"),
    @Index(name = "idx_consent_artifact", columnList = "media_artifact_id"),
    @Index(name = "idx_consent_giver", columnList = "consent_giver"),
    @Index(name = "idx_consent_status", columnList = "consent_status"),
    @Index(name = "idx_consent_type", columnList = "consent_type"),
    @Index(name = "idx_consent_expires", columnList = "expires_at"),
    @Index(name = "idx_consent_created", columnList = "created_at")
})
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "consent_id", nullable = false, unique = true, length = 255)
    private String consentId;

    @NotNull
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @NotNull
    @Column(name = "media_artifact_id", nullable = false)
    private UUID mediaArtifactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_artifact_id", insertable = false, updatable = false)
    private MediaArtifact mediaArtifact;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "consent_type", nullable = false, length = 50)
    private ConsentType consentType;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "consent_status", nullable = false, length = 50)
    private ConsentStatus consentStatus;

    @Column(name = "consent_giver", nullable = false, length = 255)
    private String consentGiver;

    @Column(name = "consent_giver_type", length = 50)
    private String consentGiverType;

    @Column(name = "legal_basis", length = 100)
    private String legalBasis;

    /**
     * Purposes for which consent is granted.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "purposes", columnDefinition = "jsonb")
    private List<String> purposes = new ArrayList<>();

    /**
     * Scope and limitations of the consent.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", columnDefinition = "jsonb")
    private ConsentScope scope;

    /**
     * Consent metadata and context.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consent_metadata", columnDefinition = "jsonb")
    private Map<String, Object> consentMetadata = new HashMap<>();

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "withdrawal_method", length = 100)
    private String withdrawalMethod;

    @Column(name = "withdrawal_reason", length = 1000)
    private String withdrawalReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "document_reference", length = 255)
    private String documentReference;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "parent_consent_id", length = 255)
    private String parentConsentId;

    /**
     * Audit trail of consent changes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_trail", columnDefinition = "jsonb")
    private List<ConsentAuditEntry> auditTrail = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Types of consent.
     */
    public enum ConsentType {
        EXPLICIT,     // Explicit written or electronic consent
        IMPLICIT,     // Implied consent from actions
        INFERRED,     // Inferred from context
        PARENTAL,     // Parental consent for minors
        GUARDIAN,     // Legal guardian consent
        EMERGENCY,    // Emergency use consent
        DELEGATED     // Delegated consent authority
    }

    /**
     * Consent status states.
     */
    public enum ConsentStatus {
        PENDING,      // Consent requested but not yet granted
        GRANTED,      // Consent has been granted
        DENIED,       // Consent was denied
        EXPIRED,      // Consent has expired
        REVOKED,      // Consent was revoked
        SUSPENDED,    // Consent temporarily suspended
        WITHDRAWN,    // Consent withdrawn by giver
        INVALID       // Consent is invalid
    }

    /**
     * Consent scope and limitations.
     */
    public record ConsentScope(
        List<String> allowedPurposes,
        List<String> restrictedPurposes,
        List<String> allowedRecipients,
        List<String> geographicRestrictions,
        List<String> timeRestrictions,
        Map<String, Object> customRestrictions
    ) {
        public ConsentScope {
            if (allowedPurposes == null) allowedPurposes = List.of();
            if (restrictedPurposes == null) restrictedPurposes = List.of();
            if (allowedRecipients == null) allowedRecipients = List.of();
            if (geographicRestrictions == null) geographicRestrictions = List.of();
            if (timeRestrictions == null) timeRestrictions = List.of();
            if (customRestrictions == null) customRestrictions = Map.of();
        }
    }

    /**
     * Audit trail entry for consent changes.
     */
    public record ConsentAuditEntry(
        Instant timestamp,
        String action,
        String performedBy,
        String reason,
        ConsentStatus previousStatus,
        ConsentStatus newStatus,
        Map<String, Object> metadata
    ) {
        public ConsentAuditEntry {
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

    public String getConsentId() {
        return consentId;
    }

    public void setConsentId(String consentId) {
        this.consentId = consentId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getMediaArtifactId() {
        return mediaArtifactId;
    }

    public void setMediaArtifactId(UUID mediaArtifactId) {
        this.mediaArtifactId = mediaArtifactId;
    }

    public MediaArtifact getMediaArtifact() {
        return mediaArtifact;
    }

    public void setMediaArtifact(MediaArtifact mediaArtifact) {
        this.mediaArtifact = mediaArtifact;
        if (mediaArtifact != null) {
            this.mediaArtifactId = mediaArtifact.getId();
        }
    }

    public ConsentType getConsentType() {
        return consentType;
    }

    public void setConsentType(ConsentType consentType) {
        this.consentType = consentType;
    }

    public ConsentStatus getConsentStatus() {
        return consentStatus;
    }

    public void setConsentStatus(ConsentStatus consentStatus) {
        this.consentStatus = consentStatus;
    }

    public String getConsentGiver() {
        return consentGiver;
    }

    public void setConsentGiver(String consentGiver) {
        this.consentGiver = consentGiver;
    }

    public String getConsentGiverType() {
        return consentGiverType;
    }

    public void setConsentGiverType(String consentGiverType) {
        this.consentGiverType = consentGiverType;
    }

    public String getLegalBasis() {
        return legalBasis;
    }

    public void setLegalBasis(String legalBasis) {
        this.legalBasis = legalBasis;
    }

    public List<String> getPurposes() {
        return purposes;
    }

    public void setPurposes(List<String> purposes) {
        this.purposes = purposes;
    }

    public ConsentScope getScope() {
        return scope;
    }

    public void setScope(ConsentScope scope) {
        this.scope = scope;
    }

    public Map<String, Object> getConsentMetadata() {
        return consentMetadata;
    }

    public void setConsentMetadata(Map<String, Object> consentMetadata) {
        this.consentMetadata = consentMetadata;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Instant grantedAt) {
        this.grantedAt = grantedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getWithdrawalMethod() {
        return withdrawalMethod;
    }

    public void setWithdrawalMethod(String withdrawalMethod) {
        this.withdrawalMethod = withdrawalMethod;
    }

    public String getWithdrawalReason() {
        return withdrawalReason;
    }

    public void setWithdrawalReason(String withdrawalReason) {
        this.withdrawalReason = withdrawalReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public void setDocumentReference(String documentReference) {
        this.documentReference = documentReference;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getParentConsentId() {
        return parentConsentId;
    }

    public void setParentConsentId(String parentConsentId) {
        this.parentConsentId = parentConsentId;
    }

    public List<ConsentAuditEntry> getAuditTrail() {
        return auditTrail;
    }

    public void setAuditTrail(List<ConsentAuditEntry> auditTrail) {
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
     * Grants consent with the specified purposes.
     */
    public void grant(String performedBy, String reason) {
        ConsentStatus previousStatus = this.consentStatus;
        this.consentStatus = ConsentStatus.GRANTED;
        this.grantedAt = Instant.now();
        this.version++;
        
        addAuditEntry("GRANTED", performedBy, reason, previousStatus, ConsentStatus.GRANTED);
    }

    /**
     * Denies consent.
     */
    public void deny(String performedBy, String reason) {
        ConsentStatus previousStatus = this.consentStatus;
        this.consentStatus = ConsentStatus.DENIED;
        this.version++;
        
        addAuditEntry("DENIED", performedBy, reason, previousStatus, ConsentStatus.DENIED);
    }

    /**
     * Revokes consent.
     */
    public void revoke(String performedBy, String reason, String method) {
        ConsentStatus previousStatus = this.consentStatus;
        this.consentStatus = ConsentStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.withdrawalMethod = method;
        this.withdrawalReason = reason;
        this.version++;
        
        addAuditEntry("REVOKED", performedBy, reason, previousStatus, ConsentStatus.REVOKED);
    }

    /**
     * Withdraws consent by the giver.
     */
    public void withdraw(String performedBy, String reason, String method) {
        ConsentStatus previousStatus = this.consentStatus;
        this.consentStatus = ConsentStatus.WITHDRAWN;
        this.revokedAt = Instant.now();
        this.withdrawalMethod = method;
        this.withdrawalReason = reason;
        this.version++;
        
        addAuditEntry("WITHDRAWN", performedBy, reason, previousStatus, ConsentStatus.WITHDRAWN);
    }

    /**
     * Expires consent.
     */
    public void expire(String performedBy) {
        ConsentStatus previousStatus = this.consentStatus;
        this.consentStatus = ConsentStatus.EXPIRED;
        this.version++;
        
        addAuditEntry("EXPIRED", performedBy, "Consent expired", previousStatus, ConsentStatus.EXPIRED);
    }

    /**
     * Suspends consent temporarily.
     */
    public void suspend(String performedBy, String reason) {
        ConsentStatus previousStatus = this.consentStatus;
        this.consentStatus = ConsentStatus.SUSPENDED;
        this.version++;
        
        addAuditEntry("SUSPENDED", performedBy, reason, previousStatus, ConsentStatus.SUSPENDED);
    }

    /**
     * Reactivates suspended consent.
     */
    public void reactivate(String performedBy, String reason) {
        ConsentStatus previousStatus = this.consentStatus;
        this.consentStatus = ConsentStatus.GRANTED;
        this.version++;
        
        addAuditEntry("REACTIVATED", performedBy, reason, previousStatus, ConsentStatus.GRANTED);
    }

    /**
     * Checks if consent is currently valid and active.
     */
    public boolean isValid() {
        if (consentStatus != ConsentStatus.GRANTED) {
            return false;
        }
        
        // Check expiration
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        
        // Check retention period
        if (retentionDays != null && grantedAt != null) {
            Instant retentionExpiry = grantedAt.plus(Duration.ofDays(retentionDays.longValue()));
            if (Instant.now().isAfter(retentionExpiry)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Checks if consent is expired.
     */
    public boolean isExpired() {
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return true;
        }
        
        if (retentionDays != null && grantedAt != null) {
            Instant retentionExpiry = grantedAt.plus(Duration.ofDays(retentionDays.longValue()));
            if (Instant.now().isAfter(retentionExpiry)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if a specific purpose is allowed.
     */
    public boolean isPurposeAllowed(String purpose) {
        if (!isValid()) {
            return false;
        }
        
        if (scope != null && scope.restrictedPurposes().contains(purpose)) {
            return false;
        }
        
        return purposes.contains(purpose) || 
               (scope != null && scope.allowedPurposes().contains(purpose));
    }

    /**
     * Gets the remaining validity period in days.
     */
    public Long getRemainingValidityDays() {
        if (!isValid()) {
            return 0L;
        }
        
        Instant expiryTime = expiresAt;
        if (expiryTime == null && retentionDays != null && grantedAt != null) {
            expiryTime = grantedAt.plus(Duration.ofDays(retentionDays.longValue()));
        }
        
        if (expiryTime == null) {
            return null; // No expiration
        }
        
        return java.time.Duration.between(Instant.now(), expiryTime).toDays();
    }

    /**
     * Gets the age of the consent in days.
     */
    public Long getAgeDays() {
        if (grantedAt == null) return null;
        return java.time.Duration.between(grantedAt, Instant.now()).toDays();
    }

    /**
     * Adds an audit entry for consent changes.
     */
    public void addAuditEntry(String action, String performedBy, String reason, 
                             ConsentStatus previousStatus, ConsentStatus newStatus) {
        ConsentAuditEntry entry = new ConsentAuditEntry(
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
    public ConsentAuditEntry getLastAuditEntry() {
        if (auditTrail.isEmpty()) {
            return null;
        }
        return auditTrail.get(auditTrail.size() - 1);
    }

    /**
     * Checks if consent can be modified.
     */
    public boolean canModify() {
        return consentStatus == ConsentStatus.GRANTED || 
               consentStatus == ConsentStatus.SUSPENDED ||
               consentStatus == ConsentStatus.PENDING;
    }

    /**
     * Checks if consent can be withdrawn.
     */
    public boolean canWithdraw() {
        return consentStatus == ConsentStatus.GRANTED && 
               consentType != ConsentType.EMERGENCY;
    }

    /**
     * Gets a summary of the consent status.
     */
    public String getSummary() {
        return String.format(
            "Consent %s: %s by %s for %s purposes, %s",
            consentId,
            consentStatus,
            consentGiver,
            purposes.size(),
            isValid() ? "valid" : "invalid"
        );
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Generate consent ID if not provided
        if (consentId == null) {
            consentId = "consent-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        // Set default consent giver type
        if (consentGiverType == null) {
            consentGiverType = "user";
        }
        
        // Add initial audit entry
        if (auditTrail.isEmpty()) {
            addAuditEntry("CREATED", "system", "Consent record created", null, consentStatus);
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
        private String consentId;
        private String tenantId;
        private UUID mediaArtifactId;
        private MediaArtifact mediaArtifact;
        private ConsentType consentType;
        private ConsentStatus consentStatus = ConsentStatus.PENDING;
        private String consentGiver;
        private String consentGiverType;
        private String legalBasis;
        private List<String> purposes = new ArrayList<>();
        private ConsentScope scope;
        private Map<String, Object> consentMetadata = new HashMap<>();
        private Instant grantedAt;
        private Instant expiresAt;
        private Instant revokedAt;
        private Integer retentionDays;
        private String withdrawalMethod;
        private String withdrawalReason;
        private String ipAddress;
        private String userAgent;
        private String documentReference;
        private Integer version = 1;
        private String parentConsentId;
        private List<ConsentAuditEntry> auditTrail = new ArrayList<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder consentId(String consentId) {
            this.consentId = consentId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder mediaArtifactId(UUID mediaArtifactId) {
            this.mediaArtifactId = mediaArtifactId;
            return this;
        }

        public Builder mediaArtifact(MediaArtifact mediaArtifact) {
            this.mediaArtifact = mediaArtifact;
            if (mediaArtifact != null) {
                this.mediaArtifactId = mediaArtifact.getId();
            }
            return this;
        }

        public Builder consentType(ConsentType consentType) {
            this.consentType = consentType;
            return this;
        }

        public Builder consentStatus(ConsentStatus consentStatus) {
            this.consentStatus = consentStatus;
            return this;
        }

        public Builder consentGiver(String consentGiver) {
            this.consentGiver = consentGiver;
            return this;
        }

        public Builder consentGiverType(String consentGiverType) {
            this.consentGiverType = consentGiverType;
            return this;
        }

        public Builder legalBasis(String legalBasis) {
            this.legalBasis = legalBasis;
            return this;
        }

        public Builder purposes(List<String> purposes) {
            this.purposes = purposes;
            return this;
        }

        public Builder scope(ConsentScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder consentMetadata(Map<String, Object> consentMetadata) {
            this.consentMetadata = consentMetadata;
            return this;
        }

        public Builder grantedAt(Instant grantedAt) {
            this.grantedAt = grantedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder revokedAt(Instant revokedAt) {
            this.revokedAt = revokedAt;
            return this;
        }

        public Builder retentionDays(Integer retentionDays) {
            this.retentionDays = retentionDays;
            return this;
        }

        public Builder withdrawalMethod(String withdrawalMethod) {
            this.withdrawalMethod = withdrawalMethod;
            return this;
        }

        public Builder withdrawalReason(String withdrawalReason) {
            this.withdrawalReason = withdrawalReason;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder documentReference(String documentReference) {
            this.documentReference = documentReference;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder parentConsentId(String parentConsentId) {
            this.parentConsentId = parentConsentId;
            return this;
        }

        public Builder auditTrail(List<ConsentAuditEntry> auditTrail) {
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

        public Consent build() {
            Consent consent = new Consent();
            consent.id = this.id;
            consent.consentId = this.consentId;
            consent.tenantId = this.tenantId;
            consent.mediaArtifactId = this.mediaArtifactId;
            consent.mediaArtifact = this.mediaArtifact;
            consent.consentType = this.consentType;
            consent.consentStatus = this.consentStatus;
            consent.consentGiver = this.consentGiver;
            consent.consentGiverType = this.consentGiverType;
            consent.legalBasis = this.legalBasis;
            consent.purposes = this.purposes;
            consent.scope = this.scope;
            consent.consentMetadata = this.consentMetadata;
            consent.grantedAt = this.grantedAt;
            consent.expiresAt = this.expiresAt;
            consent.revokedAt = this.revokedAt;
            consent.retentionDays = this.retentionDays;
            consent.withdrawalMethod = this.withdrawalMethod;
            consent.withdrawalReason = this.withdrawalReason;
            consent.ipAddress = this.ipAddress;
            consent.userAgent = this.userAgent;
            consent.documentReference = this.documentReference;
            consent.version = this.version;
            consent.parentConsentId = this.parentConsentId;
            consent.auditTrail = this.auditTrail;
            consent.createdAt = this.createdAt;
            consent.updatedAt = this.updatedAt;
            return consent;
        }
    }

    @Override
    public String toString() {
        return "Consent{" +
                "consentId='" + consentId + '\'' +
                ", consentStatus=" + consentStatus +
                ", consentGiver='" + consentGiver + '\'' +
                ", purposes=" + purposes +
                '}';
    }
}
