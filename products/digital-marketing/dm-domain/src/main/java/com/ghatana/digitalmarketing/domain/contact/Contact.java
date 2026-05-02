package com.ghatana.digitalmarketing.domain.contact;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a DMOS marketing contact (lead or customer).
 *
 * <p>A {@code Contact} is a data subject whose personal data is processed by DMOS.
 * All contact read and write operations require consent verification via the
 * {@code DM_CONSENT_LIFECYCLE} rule set and are governed by {@code DM-BP-002} and
 * {@code DM-BP-003} boundary policy rules.</p>
 *
 * <p>Contacts are immutable after construction; state changes return new instances.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS contact (lead/customer) domain entity with consent lifecycle
 * @doc.layer product
 * @doc.pattern Entity, AggregateRoot
 */
public final class Contact {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String email;
    private final String displayName;
    private final ConsentStatus consentStatus;
    private final String consentPurpose;
    private final Instant consentRecordedAt;
    private final boolean suppressed;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private Contact(Builder builder) {
        this.id               = Objects.requireNonNull(builder.id,          "id must not be null");
        this.workspaceId      = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.email            = Objects.requireNonNull(builder.email,       "email must not be null");
        this.displayName      = builder.displayName != null ? builder.displayName : "";
        this.consentStatus    = Objects.requireNonNull(builder.consentStatus, "consentStatus must not be null");
        this.consentPurpose   = builder.consentPurpose != null ? builder.consentPurpose : "";
        this.consentRecordedAt = builder.consentRecordedAt;
        this.suppressed       = builder.suppressed;
        this.createdAt        = Objects.requireNonNull(builder.createdAt,   "createdAt must not be null");
        this.updatedAt        = Objects.requireNonNull(builder.updatedAt,   "updatedAt must not be null");
        this.createdBy        = Objects.requireNonNull(builder.createdBy,   "createdBy must not be null");
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    /** Returns the contact identifier. Never {@code null} or blank. */
    public String getId() { return id; }

    /** Returns the owning workspace identifier. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the email address used to identify the contact. Never blank. */
    public String getEmail() { return email; }

    /** Returns the display name. Never {@code null}; may be empty if unknown. */
    public String getDisplayName() { return displayName; }

    /** Returns the current consent status. Never {@code null}. */
    public ConsentStatus getConsentStatus() { return consentStatus; }

    /** Returns the consent purpose string. Never {@code null}; may be empty. */
    public String getConsentPurpose() { return consentPurpose; }

    /** Returns when consent was recorded, or {@code null} if not yet recorded. */
    public Instant getConsentRecordedAt() { return consentRecordedAt; }

    /** Returns whether the contact is on the suppression list and must not be targeted. */
    public boolean isSuppressed() { return suppressed; }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the last-updated timestamp. Never {@code null}. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Returns the actor who created the contact. Never {@code null}. */
    public String getCreatedBy() { return createdBy; }

    /**
     * Returns true when the contact has explicitly granted consent for marketing
     * and is not on the suppression list.
     */
    public boolean isMarketingEligible() {
        return consentStatus == ConsentStatus.GRANTED && !suppressed;
    }

    /**
     * Returns a copy with consent status set to {@link ConsentStatus#GRANTED}.
     *
     * @param purpose          the consent purpose (e.g. {@code "marketing-email"})
     * @param consentTimestamp when consent was recorded
     */
    public Contact grantConsent(String purpose, Instant consentTimestamp) {
        Objects.requireNonNull(purpose,          "purpose must not be null");
        Objects.requireNonNull(consentTimestamp, "consentTimestamp must not be null");
        return toBuilder()
            .consentStatus(ConsentStatus.GRANTED)
            .consentPurpose(purpose)
            .consentRecordedAt(consentTimestamp)
            .updatedAt(Instant.now())
            .build();
    }

    /**
     * Returns a copy with consent status set to {@link ConsentStatus#WITHDRAWN}
     * and the suppression flag set to {@code true}.
     *
     * <p>Withdrawn consent means the contact must not be targeted; suppression ensures
     * this is enforced even if consent state is later reset.</p>
     */
    public Contact withdrawConsent() {
        return toBuilder()
            .consentStatus(ConsentStatus.WITHDRAWN)
            .suppressed(true)
            .updatedAt(Instant.now())
            .build();
    }

    /**
     * Returns a copy with the suppression flag set to {@code true}.
     *
     * <p>Suppression can be set independently of consent — e.g. for a global unsubscribe
     * or regulatory hold.</p>
     */
    public Contact suppress() {
        return toBuilder().suppressed(true).updatedAt(Instant.now()).build();
    }

    /** Returns a builder pre-populated with this contact's values. */
    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .workspaceId(workspaceId)
            .email(email)
            .displayName(displayName)
            .consentStatus(consentStatus)
            .consentPurpose(consentPurpose)
            .consentRecordedAt(consentRecordedAt)
            .suppressed(suppressed)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(createdBy);
    }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contact other)) return false;
        return id.equals(other.id) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() { return Objects.hash(id, workspaceId); }

    @Override
    public String toString() {
        return "Contact{id=" + id + ", workspaceId=" + workspaceId
            + ", consentStatus=" + consentStatus + ", suppressed=" + suppressed + '}';
    }

    /**
     * Fluent builder for {@link Contact}.
     */
    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String email;
        private String displayName;
        private ConsentStatus consentStatus;
        private String consentPurpose;
        private Instant consentRecordedAt;
        private boolean suppressed;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() { }

        public Builder id(String id) { this.id = id; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder consentStatus(ConsentStatus consentStatus) { this.consentStatus = consentStatus; return this; }
        public Builder consentPurpose(String consentPurpose) { this.consentPurpose = consentPurpose; return this; }
        public Builder consentRecordedAt(Instant consentRecordedAt) { this.consentRecordedAt = consentRecordedAt; return this; }
        public Builder suppressed(boolean suppressed) { this.suppressed = suppressed; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        /** Builds a {@link Contact}. Throws if required fields are missing or invalid. */
        public Contact build() { return new Contact(this); }
    }
}
