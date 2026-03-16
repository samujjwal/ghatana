package com.ghatana.services.userprofile;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of a user's profile and preferences.
 *
 * <p>Scoped per {@code tenantId} so that the same physical user can hold
 * different preferences across tenants (multi-tenant isolation).</p>
 *
 * @param userId              Unique user identifier (sub claim from JWT).
 * @param tenantId            Tenant the profile belongs to.
 * @param email               Primary email address (read-only, sourced from IdP).
 * @param displayName         User-chosen display name; falls back to email prefix.
 * @param avatarUrl           Optional URL to profile picture.
 * @param preferredLanguage   BCP-47 language tag, e.g. {@code "en-US"}.
 * @param timezone            IANA timezone ID, e.g. {@code "America/New_York"}.
 * @param theme               UI theme preference: {@code "light"}, {@code "dark"}, or {@code "system"}.
 * @param notificationsEnabled Whether push/email notifications are enabled.
 * @param createdAt           Profile creation timestamp.
 * @param updatedAt           Last modification timestamp.
 *
 * @doc.type record
 * @doc.purpose Value object representing a user's profile and preferences
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record UserProfile(
        String userId,
        String tenantId,
        String email,
        String displayName,
        String avatarUrl,
        String preferredLanguage,
        String timezone,
        String theme,
        boolean notificationsEnabled,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Compact canonical constructor — validates required fields and applies
     * sensible defaults.
     */
    public UserProfile {
        Objects.requireNonNull(userId,   "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(email,    "email must not be null");
        if (displayName == null || displayName.isBlank()) {
            int atIdx = email.indexOf('@');
            displayName = atIdx > 0 ? email.substring(0, atIdx) : email;
        }
        if (preferredLanguage == null || preferredLanguage.isBlank()) {
            preferredLanguage = "en-US";
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "UTC";
        }
        if (theme == null || theme.isBlank()) {
            theme = "system";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    /** Creates a new profile with the {@code updatedAt} timestamp refreshed to now. */
    public UserProfile withUpdatedAt(Instant now) {
        return new UserProfile(userId, tenantId, email, displayName, avatarUrl,
                preferredLanguage, timezone, theme, notificationsEnabled, createdAt, now);
    }

    /** Returns a builder pre-populated with this profile's values. */
    public Builder toBuilder() {
        return new Builder()
                .userId(userId).tenantId(tenantId).email(email)
                .displayName(displayName).avatarUrl(avatarUrl)
                .preferredLanguage(preferredLanguage).timezone(timezone)
                .theme(theme).notificationsEnabled(notificationsEnabled)
                .createdAt(createdAt).updatedAt(updatedAt);
    }

    // ─── Builder ─────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String userId, tenantId, email, displayName, avatarUrl;
        private String preferredLanguage, timezone, theme;
        private boolean notificationsEnabled = true;
        private Instant createdAt, updatedAt;

        public Builder userId(String v)              { this.userId = v;               return this; }
        public Builder tenantId(String v)            { this.tenantId = v;             return this; }
        public Builder email(String v)               { this.email = v;                return this; }
        public Builder displayName(String v)         { this.displayName = v;          return this; }
        public Builder avatarUrl(String v)           { this.avatarUrl = v;            return this; }
        public Builder preferredLanguage(String v)   { this.preferredLanguage = v;    return this; }
        public Builder timezone(String v)            { this.timezone = v;             return this; }
        public Builder theme(String v)               { this.theme = v;                return this; }
        public Builder notificationsEnabled(boolean v) { this.notificationsEnabled = v; return this; }
        public Builder createdAt(Instant v)          { this.createdAt = v;            return this; }
        public Builder updatedAt(Instant v)          { this.updatedAt = v;            return this; }

        public UserProfile build() {
            return new UserProfile(userId, tenantId, email, displayName, avatarUrl,
                    preferredLanguage, timezone, theme, notificationsEnabled, createdAt, updatedAt);
        }
    }
}
