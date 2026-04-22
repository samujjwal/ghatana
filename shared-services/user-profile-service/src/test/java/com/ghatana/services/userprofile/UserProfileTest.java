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
@DisplayName("UserProfile — construction, defaults, and validation [GH-90000]")
class UserProfileTest {

    @Test
    @DisplayName("valid required fields constructs profile successfully [GH-90000]")
    void validRequiredFieldsConstructProfile() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.userId()).isEqualTo("user-123 [GH-90000]");
        assertThat(profile.tenantId()).isEqualTo("tenant-abc [GH-90000]");
        assertThat(profile.email()).isEqualTo("alice@example.com [GH-90000]");
    }

    @Test
    @DisplayName("null userId throws NullPointerException [GH-90000]")
    void nullUserIdThrows() { // GH-90000
        assertThatThrownBy(() -> UserProfile.builder() // GH-90000
                .userId(null) // GH-90000
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("userId [GH-90000]");
    }

    @Test
    @DisplayName("null tenantId throws NullPointerException [GH-90000]")
    void nullTenantIdThrows() { // GH-90000
        assertThatThrownBy(() -> UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId(null) // GH-90000
                .email("alice@example.com [GH-90000]")
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("tenantId [GH-90000]");
    }

    @Test
    @DisplayName("null email throws NullPointerException [GH-90000]")
    void nullEmailThrows() { // GH-90000
        assertThatThrownBy(() -> UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email(null) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("email [GH-90000]");
    }

    @Test
    @DisplayName("displayName defaults to email prefix when not provided [GH-90000]")
    void displayNameDefaultsToEmailPrefix() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("alice [GH-90000]");
    }

    @Test
    @DisplayName("displayName defaults to full email when no @ sign [GH-90000]")
    void displayNameDefaultsToFullEmailWhenNoAtSign() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("localusername [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("localusername [GH-90000]");
    }

    @Test
    @DisplayName("explicit displayName is preserved [GH-90000]")
    void explicitDisplayNameIsPreserved() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .displayName("Alice Wonderland [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.displayName()).isEqualTo("Alice Wonderland [GH-90000]");
    }

    @Test
    @DisplayName("preferredLanguage defaults to en-US when not provided [GH-90000]")
    void preferredLanguageDefaultsToEnUS() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.preferredLanguage()).isEqualTo("en-US [GH-90000]");
    }

    @Test
    @DisplayName("timezone defaults to UTC when not provided [GH-90000]")
    void timezoneDefaultsToUTC() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.timezone()).isEqualTo("UTC [GH-90000]");
    }

    @Test
    @DisplayName("theme defaults to system when not provided [GH-90000]")
    void themeDefaultsToSystem() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.theme()).isEqualTo("system [GH-90000]");
    }

    @Test
    @DisplayName("createdAt is set to now when not provided [GH-90000]")
    void createdAtDefaultsToNow() { // GH-90000
        Instant before = Instant.now().minusSeconds(1); // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000
        Instant after = Instant.now().plusSeconds(1); // GH-90000

        assertThat(profile.createdAt()).isAfter(before).isBefore(after); // GH-90000
    }

    @Test
    @DisplayName("updatedAt defaults to createdAt when not explicitly set [GH-90000]")
    void updatedAtDefaultsToCreatedAt() { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        assertThat(profile.updatedAt()).isEqualTo(profile.createdAt()); // GH-90000
    }

    @Test
    @DisplayName("withUpdatedAt creates new profile with updated timestamp [GH-90000]")
    void withUpdatedAtCreatesNewProfile() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-123 [GH-90000]")
                .tenantId("tenant-abc [GH-90000]")
                .email("alice@example.com [GH-90000]")
                .build(); // GH-90000

        Instant newTime = Instant.now().plusSeconds(60); // GH-90000
        UserProfile updated = original.withUpdatedAt(newTime); // GH-90000

        assertThat(updated.updatedAt()).isEqualTo(newTime); // GH-90000
        assertThat(updated.userId()).isEqualTo(original.userId()); // GH-90000
        assertThat(updated.email()).isEqualTo(original.email()); // GH-90000
    }

    @Test
    @DisplayName("toBuilder produces builder pre-filled with current values [GH-90000]")
    void toBuilderPreserveValues() { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-456 [GH-90000]")
                .tenantId("tenant-xyz [GH-90000]")
                .email("bob@example.com [GH-90000]")
                .displayName("Bob [GH-90000]")
                .timezone("America/New_York [GH-90000]")
                .theme("dark [GH-90000]")
                .notificationsEnabled(false) // GH-90000
                .build(); // GH-90000

        UserProfile copy = original.toBuilder().build(); // GH-90000

        assertThat(copy.userId()).isEqualTo("user-456 [GH-90000]");
        assertThat(copy.displayName()).isEqualTo("Bob [GH-90000]");
        assertThat(copy.timezone()).isEqualTo("America/New_York [GH-90000]");
        assertThat(copy.theme()).isEqualTo("dark [GH-90000]");
        assertThat(copy.notificationsEnabled()).isFalse(); // GH-90000
    }
}
