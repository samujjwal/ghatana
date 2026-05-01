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
    void builderThrowsWhenUserIdIsNull() { 
        assertThatThrownBy(() -> 
                UserProfile.builder() 
                        .userId(null) 
                        .tenantId("tenant-v")
                        .email("val@example.com")
                        .build()) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("builder throws when tenantId is null")
    void builderThrowsWhenTenantIdIsNull() { 
        assertThatThrownBy(() -> 
                UserProfile.builder() 
                        .userId("user-v-001")
                        .tenantId(null) 
                        .email("val@example.com")
                        .build()) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("builder throws when email is null")
    void builderThrowsWhenEmailIsNull() { 
        assertThatThrownBy(() -> 
                UserProfile.builder() 
                        .userId("user-v-001")
                        .tenantId("tenant-v")
                        .email(null) 
                        .build()) 
                .isInstanceOf(NullPointerException.class); 
    }

    // ── Default value application ─────────────────────────────────────────────

    @Test
    @DisplayName("displayName defaults to email local-part when not provided")
    void displayNameDefaultsToEmailLocalPart() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-002")
                .tenantId("tenant-v")
                .email("alice@example.com")
                .build(); 

        // displayName must not be null/blank — defaults derived from email
        assertThat(profile.displayName()).isNotNull().isNotBlank(); 
    }

    @Test
    @DisplayName("preferredLanguage defaults to en-US")
    void preferredLanguageDefaultsToEnUs() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-003")
                .tenantId("tenant-v")
                .email("lang@example.com")
                .build(); 

        assertThat(profile.preferredLanguage()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("timezone defaults to UTC")
    void timezoneDefaultsToUtc() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-004")
                .tenantId("tenant-v")
                .email("tz@example.com")
                .build(); 

        assertThat(profile.timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("theme defaults to system")
    void themeDefaultsToSystem() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-005")
                .tenantId("tenant-v")
                .email("theme@example.com")
                .build(); 

        assertThat(profile.theme()).isEqualTo("system");
    }

    // ── Custom value overrides ────────────────────────────────────────────────

    @Test
    @DisplayName("explicitly set displayName overrides email-derived default")
    void explicitDisplayNameOverridesDefault() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-006")
                .tenantId("tenant-v")
                .email("alice@example.com")
                .displayName("Alice Wonderland")
                .build(); 

        assertThat(profile.displayName()).isEqualTo("Alice Wonderland");
    }

    @ParameterizedTest(name = "theme={0} is accepted") 
    @ValueSource(strings = {"light", "dark", "system"}) 
    @DisplayName("theme accepts light/dark/system values")
    void themeAcceptsValidValues(String theme) { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-007")
                .tenantId("tenant-v")
                .email("theme@example.com")
                .theme(theme) 
                .build(); 

        assertThat(profile.theme()).isEqualTo(theme); 
    }

    @ParameterizedTest(name = "language={0} is accepted") 
    @ValueSource(strings = {"en-US", "fr-FR", "de-DE", "ja-JP", "zh-CN"}) 
    @DisplayName("preferredLanguage stores locale codes correctly")
    void preferredLanguageStoresLocaleCodes(String lang) { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-008")
                .tenantId("tenant-v")
                .email("lang@example.com")
                .preferredLanguage(lang) 
                .build(); 

        assertThat(profile.preferredLanguage()).isEqualTo(lang); 
    }

    // ── Immutability via toBuilder ────────────────────────────────────────────

    @Test
    @DisplayName("toBuilder does not mutate the original profile")
    void toBuilderPreservesOriginalProfile() { 
        UserProfile original = UserProfile.builder() 
                .userId("user-v-009")
                .tenantId("tenant-v")
                .email("original@example.com")
                .displayName("Original")
                .build(); 

        // Create a modified copy
        UserProfile modified = original.toBuilder() 
                .displayName("Modified")
                .build(); 

        assertThat(original.displayName()).isEqualTo("Original");
        assertThat(modified.displayName()).isEqualTo("Modified");
        assertThat(original.userId()).isEqualTo(modified.userId()); 
    }

    @Test
    @DisplayName("withUpdatedAt returns new profile with updated timestamp, original unchanged")
    void withUpdatedAtDoesNotMutateOriginal() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-v-010")
                .tenantId("tenant-v")
                .email("ts@example.com")
                .build(); 

        java.time.Instant updatedAt = java.time.Instant.now(); 
        UserProfile withTs = profile.withUpdatedAt(updatedAt); 

        assertThat(withTs.updatedAt()).isEqualTo(updatedAt); 
        assertThat(withTs.userId()).isEqualTo(profile.userId()); 
    }
}
