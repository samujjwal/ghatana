package com.ghatana.digitalmarketing.domain.identity;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable contact identity profile with additional identity attributes.
 *
 * @doc.type class
 * @doc.purpose DMOS identity-depth profile for contact identity foundation
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class ContactIdentityProfile {

    private final String contactId;
    private final DmWorkspaceId workspaceId;
    private final String phoneNumber;
    private final String preferredLocale;
    private final String externalIdentityId;
    private final Map<String, String> attributes;
    private final Instant updatedAt;
    private final String updatedBy;

    private ContactIdentityProfile(Builder builder) {
        this.contactId = Objects.requireNonNull(builder.contactId, "contactId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.phoneNumber = builder.phoneNumber != null ? builder.phoneNumber : "";
        this.preferredLocale = builder.preferredLocale != null ? builder.preferredLocale : "";
        this.externalIdentityId = builder.externalIdentityId != null ? builder.externalIdentityId : "";
        this.attributes = builder.attributes != null ? Map.copyOf(builder.attributes) : Map.of();
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        this.updatedBy = Objects.requireNonNull(builder.updatedBy, "updatedBy must not be null");

        if (this.contactId.isBlank()) {
            throw new IllegalArgumentException("contactId must not be blank");
        }
    }

    public String getContactId() {
        return contactId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public String getExternalIdentityId() {
        return externalIdentityId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String contactId;
        private DmWorkspaceId workspaceId;
        private String phoneNumber;
        private String preferredLocale;
        private String externalIdentityId;
        private Map<String, String> attributes;
        private Instant updatedAt;
        private String updatedBy;

        private Builder() {
        }

        public Builder contactId(String contactId) {
            this.contactId = contactId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder preferredLocale(String preferredLocale) {
            this.preferredLocale = preferredLocale;
            return this;
        }

        public Builder externalIdentityId(String externalIdentityId) {
            this.externalIdentityId = externalIdentityId;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public ContactIdentityProfile build() {
            return new ContactIdentityProfile(this);
        }
    }
}
