package com.ghatana.digitalmarketing.domain.lead;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a CRM-lite lead capture record.
 *
 * <p>A {@code Lead} is a prospective customer captured via a campaign landing page,
 * form, or inbound channel. Leads are workspace-scoped and enter the DMOS funnel
 * at the NEW status, transitioning through qualification to CONVERTED or DISQUALIFIED.</p>
 *
 * <p>Lead instances are immutable after construction; state transitions return new instances.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS CRM-lite lead capture domain entity
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class Lead {

    private final String id;
    private final DmWorkspaceId workspaceId;
    private final String campaignId;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final String source;
    private final LeadStatus status;
    private final Instant capturedAt;
    private final Instant updatedAt;

    private Lead(Builder builder) {
        this.id          = Objects.requireNonNull(builder.id,          "id must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.campaignId  = Objects.requireNonNull(builder.campaignId,  "campaignId must not be null");
        this.email       = Objects.requireNonNull(builder.email,       "email must not be null");
        this.firstName   = builder.firstName != null ? builder.firstName : "";
        this.lastName    = builder.lastName  != null ? builder.lastName  : "";
        this.phone       = builder.phone     != null ? builder.phone     : "";
        this.source      = builder.source    != null ? builder.source    : "unknown";
        this.status      = Objects.requireNonNull(builder.status,      "status must not be null");
        this.capturedAt  = Objects.requireNonNull(builder.capturedAt,  "capturedAt must not be null");
        this.updatedAt   = Objects.requireNonNull(builder.updatedAt,   "updatedAt must not be null");
        if (this.id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (this.email.isBlank()) throw new IllegalArgumentException("email must not be blank");
    }

    /** Returns the lead identifier. Never {@code null} or blank. */
    public String getId() { return id; }

    /** Returns the owning workspace identifier. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the campaign ID that generated this lead. Never {@code null}. */
    public String getCampaignId() { return campaignId; }

    /** Returns the lead's email address. Never blank. */
    public String getEmail() { return email; }

    /** Returns the first name, may be empty. */
    public String getFirstName() { return firstName; }

    /** Returns the last name, may be empty. */
    public String getLastName() { return lastName; }

    /** Returns the phone number, may be empty. */
    public String getPhone() { return phone; }

    /** Returns the capture source (e.g. "landing-page", "form-embed"). */
    public String getSource() { return source; }

    /** Returns the current qualification status. Never {@code null}. */
    public LeadStatus getStatus() { return status; }

    /** Returns when the lead was captured. Never {@code null}. */
    public Instant getCapturedAt() { return capturedAt; }

    /** Returns when the lead was last updated. Never {@code null}. */
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Returns a copy of this lead with status set to {@link LeadStatus#QUALIFIED}.
     * Only valid from {@link LeadStatus#NEW}.
     *
     * @throws IllegalStateException if not in NEW status
     */
    public Lead qualify() {
        if (status != LeadStatus.NEW) {
            throw new IllegalStateException("Cannot qualify lead in status " + status + "; must be NEW");
        }
        return toBuilder().status(LeadStatus.QUALIFIED).updatedAt(Instant.now()).build();
    }

    /**
     * Returns a copy with status set to {@link LeadStatus#CONVERTED}.
     * Only valid from {@link LeadStatus#QUALIFIED}.
     *
     * @throws IllegalStateException if not in QUALIFIED status
     */
    public Lead convert() {
        if (status != LeadStatus.QUALIFIED) {
            throw new IllegalStateException("Cannot convert lead in status " + status + "; must be QUALIFIED");
        }
        return toBuilder().status(LeadStatus.CONVERTED).updatedAt(Instant.now()).build();
    }

    /**
     * Returns a copy with status set to {@link LeadStatus#DISQUALIFIED}.
     * Valid from NEW or QUALIFIED.
     *
     * @throws IllegalStateException if already CONVERTED or DISQUALIFIED
     */
    public Lead disqualify() {
        if (status == LeadStatus.CONVERTED || status == LeadStatus.DISQUALIFIED) {
            throw new IllegalStateException("Cannot disqualify lead in status " + status);
        }
        return toBuilder().status(LeadStatus.DISQUALIFIED).updatedAt(Instant.now()).build();
    }

    /** Returns a builder pre-populated with this lead's values. */
    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .workspaceId(workspaceId)
            .campaignId(campaignId)
            .email(email)
            .firstName(firstName)
            .lastName(lastName)
            .phone(phone)
            .source(source)
            .status(status)
            .capturedAt(capturedAt)
            .updatedAt(updatedAt);
    }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lead other)) return false;
        return id.equals(other.id) && workspaceId.equals(other.workspaceId);
    }

    @Override
    public int hashCode() { return Objects.hash(id, workspaceId); }

    @Override
    public String toString() {
        return "Lead{id=" + id + ", email=" + email + ", status=" + status + '}';
    }

    /**
     * Fluent builder for {@link Lead}.
     */
    public static final class Builder {
        private String id;
        private DmWorkspaceId workspaceId;
        private String campaignId;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String source;
        private LeadStatus status;
        private Instant capturedAt;
        private Instant updatedAt;

        private Builder() { }

        public Builder id(String id) { this.id = id; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder campaignId(String campaignId) { this.campaignId = campaignId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder firstName(String firstName) { this.firstName = firstName; return this; }
        public Builder lastName(String lastName) { this.lastName = lastName; return this; }
        public Builder phone(String phone) { this.phone = phone; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder status(LeadStatus status) { this.status = status; return this; }
        public Builder capturedAt(Instant capturedAt) { this.capturedAt = capturedAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        /** Builds a {@link Lead}. Throws if required fields are missing or invalid. */
        public Lead build() { return new Lead(this); }
    }
}
