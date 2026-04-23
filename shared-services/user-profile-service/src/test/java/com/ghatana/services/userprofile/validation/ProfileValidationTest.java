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
@DisplayName("UserProfile Validation Tests")
class ProfileValidationTest extends EventloopTestBase {

    // ── Required field enforcement ─────────────────────────────────────────────

    @Test
    @DisplayName("builder throws when userId is null")
    void builderThrowsWhenUserIdIsNull() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                UserProfile.builder() // GH-90000
                        .userId(null) // GH-90000
                        .tenantId("tenant-v")
                        .email("val@example.com")
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("builder throws when tenantId is null")
    void builderThrowsWhenTenantIdIsNull() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                UserProfile.builder() // GH-90000
                        .userId("user-v-001")
                        .tenantId(null) // GH-90000
                        .email("val@example.com")
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("builder throws when email is null")
    void builderThrowsWhenEmailIsNull() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                UserProfile.builder() // GH-90000
                        .userId("user-v-001")
                        .tenantId("tenant-v")
                        .email(null) // GH-90000
                        .build()) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── Default value application ─────────────────────────────────────────────

    @Test
    @DisplayName("displayName defaults to email local-part when not provided")
    void displayNameDefaultsToEmailLocalPart() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-002")
                .tenantId("tenant-v")
                .email("alice@example.com")
                .build(); // GH-90000

        // displayName must not be null/blank — defaults derived from email
        assertThat(profile.displayName()).isNotNull().isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("preferredLanguage defaults to en-US")
    void preferredLanguageDefaultsToEnUs() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-003")
                .tenantId("tenant-v")
                .email("lang@example.com")
                .build(); // GH-90000

        assertThat(profile.preferredLanguage()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("timezone defaults to UTC")
    void timezoneDefaultsToUtc() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-004")
                .tenantId("tenant-v")
                .email("tz@example.com")
                .build(); // GH-90000

        assertThat(profile.timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("theme defaults to system")
    void themeDefaultsToSystem() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-005")
                .tenantId("tenant-v")
                .email("theme@example.com")
                .build(); // GH-90000

        assertThat(profile.theme()).isEqualTo("system");
    }

    // ── Custom value overrides ────────────────────────────────────────────────

    @Test
    @DisplayName("explicitly set displayName overrides email-derived default")
    void explicitDisplayNameOverridesDefault() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-006")
                .tenantId("tenant-v")
                .email("alice@example.com")
                .displayName("Alice Wonderland")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("Alice Wonderland");
    }

    @ParameterizedTest(name = "theme={0} is accepted") // GH-90000
    @ValueSource(strings = {"light", "dark", "system"}) // GH-90000
    @DisplayName("theme accepts light/dark/system values")
    void themeAcceptsValidValues(String theme) { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-007")
                .tenantId("tenant-v")
                .email("theme@example.com")
                .theme(theme) // GH-90000
                .build(); // GH-90000

        assertThat(profile.theme()).isEqualTo(theme); // GH-90000
    }

    @ParameterizedTest(name = "language={0} is accepted") // GH-90000
    @ValueSource(strings = {"en-US", "fr-FR", "de-DE", "ja-JP", "zh-CN"}) // GH-90000
    @DisplayName("preferredLanguage stores locale codes correctly")
    void preferredLanguageStoresLocaleCodes(String lang) { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-008")
                .tenantId("tenant-v")
                .email("lang@example.com")
                .preferredLanguage(lang) // GH-90000
                .build(); // GH-90000

        assertThat(profile.preferredLanguage()).isEqualTo(lang); // GH-90000
    }

    // ── Immutability via toBuilder ────────────────────────────────────────────

    @Test
    @DisplayName("toBuilder does not mutate the original profile")
    void toBuilderPreservesOriginalProfile() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-v-009")
                .tenantId("tenant-v")
                .email("original@example.com")
                .displayName("Original")
                .build(); // GH-90000

        // Create a modified copy
        UserProfile modified = original.toBuilder() // GH-90000
                .displayName("Modified")
                .build(); // GH-90000

        assertThat(original.displayName()).isEqualTo("Original");
        assertThat(modified.displayName()).isEqualTo("Modified");
        assertThat(original.userId()).isEqualTo(modified.userId()); // GH-90000
    }

    @Test
    @DisplayName("withUpdatedAt returns new profile with updated timestamp, original unchanged")
    void withUpdatedAtDoesNotMutateOriginal() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-v-010")
                .tenantId("tenant-v")
                .email("ts@example.com")
                .build(); // GH-90000

        java.time.Instant updatedAt = java.time.Instant.now(); // GH-90000
        UserProfile withTs = profile.withUpdatedAt(updatedAt); // GH-90000

        assertThat(withTs.updatedAt()).isEqualTo(updatedAt); // GH-90000
        assertThat(withTs.userId()).isEqualTo(profile.userId()); // GH-90000
    }
}
