package com.ghatana.services.userprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for UserProfile record construction, defaults, and validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("UserProfile — construction, defaults, and validation")
class UserProfileTest {

    @Test
    @DisplayName("valid required fields constructs profile successfully")
    void validRequiredFieldsConstructProfile() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000

        assertThat(profile.userId()).isEqualTo("user-123");
        assertThat(profile.tenantId()).isEqualTo("tenant-abc");
        assertThat(profile.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("null userId throws NullPointerException")
    void nullUserIdThrows() { // GH-90000
        assertThatThrownBy(() -> UserProfile.builder() // GH-90000
                .userId(null) // GH-90000
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("null tenantId throws NullPointerException")
    void nullTenantIdThrows() { // GH-90000
        assertThatThrownBy(() -> UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId(null) // GH-90000
                .email("alice@example.com")
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("null email throws NullPointerException")
    void nullEmailThrows() { // GH-90000
        assertThatThrownBy(() -> UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email(null) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("displayName defaults to email prefix when not provided")
    void displayNameDefaultsToEmailPrefix() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("alice");
    }

    @Test
    @DisplayName("displayName defaults to full email when no @ sign")
    void displayNameDefaultsToFullEmailWhenNoAtSign() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("localusername")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("localusername");
    }

    @Test
    @DisplayName("explicit displayName is preserved")
    void explicitDisplayNameIsPreserved() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .displayName("Alice Wonderland")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("Alice Wonderland");
    }

    @Test
    @DisplayName("preferredLanguage defaults to en-US when not provided")
    void preferredLanguageDefaultsToEnUS() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000

        assertThat(profile.preferredLanguage()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("timezone defaults to UTC when not provided")
    void timezoneDefaultsToUTC() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000

        assertThat(profile.timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("theme defaults to system when not provided")
    void themeDefaultsToSystem() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000

        assertThat(profile.theme()).isEqualTo("system");
    }

    @Test
    @DisplayName("createdAt is set to now when not provided")
    void createdAtDefaultsToNow() { // GH-90000
        Instant before = Instant.now().minusSeconds(1); // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000
        Instant after = Instant.now().plusSeconds(1); // GH-90000

        assertThat(profile.createdAt()).isAfter(before).isBefore(after); // GH-90000
    }

    @Test
    @DisplayName("updatedAt defaults to createdAt when not explicitly set")
    void updatedAtDefaultsToCreatedAt() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000

        assertThat(profile.updatedAt()).isEqualTo(profile.createdAt()); // GH-90000
    }

    @Test
    @DisplayName("withUpdatedAt creates new profile with updated timestamp")
    void withUpdatedAtCreatesNewProfile() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); // GH-90000

        Instant newTime = Instant.now().plusSeconds(60); // GH-90000
        UserProfile updated = original.withUpdatedAt(newTime); // GH-90000

        assertThat(updated.updatedAt()).isEqualTo(newTime); // GH-90000
        assertThat(updated.userId()).isEqualTo(original.userId()); // GH-90000
        assertThat(updated.email()).isEqualTo(original.email()); // GH-90000
    }

    @Test
    @DisplayName("toBuilder produces builder pre-filled with current values")
    void toBuilderPreserveValues() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-456")
                .tenantId("tenant-xyz")
                .email("bob@example.com")
                .displayName("Bob")
                .timezone("America/New_York")
                .theme("dark")
                .notificationsEnabled(false) // GH-90000
                .build(); // GH-90000

        UserProfile copy = original.toBuilder().build(); // GH-90000

        assertThat(copy.userId()).isEqualTo("user-456");
        assertThat(copy.displayName()).isEqualTo("Bob");
        assertThat(copy.timezone()).isEqualTo("America/New_York");
        assertThat(copy.theme()).isEqualTo("dark");
        assertThat(copy.notificationsEnabled()).isFalse(); // GH-90000
    }
}
