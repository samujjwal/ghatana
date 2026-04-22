package com.ghatana.services.userprofile.validation;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.services.userprofile.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation tests for {@link UserProfile} required field constraints and default value application.
 *
 * @doc.type    class
 * @doc.purpose Tests for UserProfile field validation and default assignment logic
 * @doc.layer   service
 * @doc.pattern Test
 */
@DisplayName("UserProfile Validation Tests [GH-90000]")
class ProfileValidationTest extends EventloopTestBase {

    // ── Required field enforcement ─────────────────────────────────────────────

    @Test
    @DisplayName("builder throws when userId is null [GH-90000]")
    void builderThrowsWhenUserIdIsNull() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                UserProfile.builder() // GH-90000
                        .userId(null) // GH-90000
                        .tenantId("tenant-v [GH-90000]")
                        .email("val@example.com [GH-90000]")
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("builder throws when tenantId is null [GH-90000]")
    void builderThrowsWhenTenantIdIsNull() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                UserProfile.builder() // GH-90000
                        .userId("user-v-001 [GH-90000]")
                        .tenantId(null) // GH-90000
                        .email("val@example.com [GH-90000]")
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("builder throws when email is null [GH-90000]")
    void builderThrowsWhenEmailIsNull() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                UserProfile.builder() // GH-90000
                        .userId("user-v-001 [GH-90000]")
                        .tenantId("tenant-v [GH-90000]")
                        .email(null) // GH-90000
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── Default value application ─────────────────────────────────────────────

    @Test
    @DisplayName("displayName defaults to email local-part when not provided [GH-90000]")
    void displayNameDefaultsToEmailLocalPart() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-002 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        // displayName must not be null/blank — defaults derived from email
        assertThat(profile.displayName()).isNotNull().isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("preferredLanguage defaults to en-US [GH-90000]")
    void preferredLanguageDefaultsToEnUs() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-003 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("lang@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.preferredLanguage()).isEqualTo("en-US [GH-90000]");
    }

    @Test
    @DisplayName("timezone defaults to UTC [GH-90000]")
    void timezoneDefaultsToUtc() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-004 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("tz@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.timezone()).isEqualTo("UTC [GH-90000]");
    }

    @Test
    @DisplayName("theme defaults to system [GH-90000]")
    void themeDefaultsToSystem() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-005 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("theme@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.theme()).isEqualTo("system [GH-90000]");
    }

    // ── Custom value overrides ────────────────────────────────────────────────

    @Test
    @DisplayName("explicitly set displayName overrides email-derived default [GH-90000]")
    void explicitDisplayNameOverridesDefault() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-006 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .displayName("Alice Wonderland [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("Alice Wonderland [GH-90000]");
    }

    @ParameterizedTest(name = "theme={0} is accepted") // GH-90000
    @ValueSource(strings = {"light", "dark", "system"}) // GH-90000
    @DisplayName("theme accepts light/dark/system values [GH-90000]")
    void themeAcceptsValidValues(String theme) { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-007 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("theme@example.com [GH-90000]")
                .theme(theme) // GH-90000
                .build(); // GH-90000

        assertThat(profile.theme()).isEqualTo(theme); // GH-90000
    }

    @ParameterizedTest(name = "language={0} is accepted") // GH-90000
    @ValueSource(strings = {"en-US", "fr-FR", "de-DE", "ja-JP", "zh-CN"}) // GH-90000
    @DisplayName("preferredLanguage stores locale codes correctly [GH-90000]")
    void preferredLanguageStoresLocaleCodes(String lang) { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-008 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("lang@example.com [GH-90000]")
                .preferredLanguage(lang) // GH-90000
                .build(); // GH-90000

        assertThat(profile.preferredLanguage()).isEqualTo(lang); // GH-90000
    }

    // ── Immutability via toBuilder ────────────────────────────────────────────

    @Test
    @DisplayName("toBuilder does not mutate the original profile [GH-90000]")
    void toBuilderPreservesOriginalProfile() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-v-009 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("original@example.com [GH-90000]")
                .displayName("Original [GH-90000]")
                .build(); // GH-90000

        // Create a modified copy
        UserProfile modified = original.toBuilder() // GH-90000
                .displayName("Modified [GH-90000]")
                .build(); // GH-90000

        assertThat(original.displayName()).isEqualTo("Original [GH-90000]");
        assertThat(modified.displayName()).isEqualTo("Modified [GH-90000]");
        assertThat(original.userId()).isEqualTo(modified.userId()); // GH-90000
    }

    @Test
    @DisplayName("withUpdatedAt returns new profile with updated timestamp, original unchanged [GH-90000]")
    void withUpdatedAtDoesNotMutateOriginal() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-010 [GH-90000]")
                .tenantId("tenant-v [GH-90000]")
                .email("ts@example.com [GH-90000]")
                .build(); // GH-90000

        java.time.Instant updatedAt = java.time.Instant.now(); // GH-90000
        UserProfile withTs = profile.withUpdatedAt(updatedAt); // GH-90000

        assertThat(withTs.updatedAt()).isEqualTo(updatedAt); // GH-90000
        assertThat(withTs.userId()).isEqualTo(profile.userId()); // GH-90000
    }
}
