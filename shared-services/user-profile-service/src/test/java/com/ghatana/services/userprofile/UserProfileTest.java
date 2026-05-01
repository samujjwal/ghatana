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
    void validRequiredFieldsConstructProfile() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 

        assertThat(profile.userId()).isEqualTo("user-123");
        assertThat(profile.tenantId()).isEqualTo("tenant-abc");
        assertThat(profile.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("null userId throws NullPointerException")
    void nullUserIdThrows() { 
        assertThatThrownBy(() -> UserProfile.builder() 
                .userId(null) 
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build()) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("null tenantId throws NullPointerException")
    void nullTenantIdThrows() { 
        assertThatThrownBy(() -> UserProfile.builder() 
                .userId("user-123")
                .tenantId(null) 
                .email("alice@example.com")
                .build()) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("null email throws NullPointerException")
    void nullEmailThrows() { 
        assertThatThrownBy(() -> UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email(null) 
                .build()) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("displayName defaults to email prefix when not provided")
    void displayNameDefaultsToEmailPrefix() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 

        assertThat(profile.displayName()).isEqualTo("alice");
    }

    @Test
    @DisplayName("displayName defaults to full email when no @ sign")
    void displayNameDefaultsToFullEmailWhenNoAtSign() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("localusername")
                .build(); 

        assertThat(profile.displayName()).isEqualTo("localusername");
    }

    @Test
    @DisplayName("explicit displayName is preserved")
    void explicitDisplayNameIsPreserved() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .displayName("Alice Wonderland")
                .build(); 

        assertThat(profile.displayName()).isEqualTo("Alice Wonderland");
    }

    @Test
    @DisplayName("preferredLanguage defaults to en-US when not provided")
    void preferredLanguageDefaultsToEnUS() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 

        assertThat(profile.preferredLanguage()).isEqualTo("en-US");
    }

    @Test
    @DisplayName("timezone defaults to UTC when not provided")
    void timezoneDefaultsToUTC() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 

        assertThat(profile.timezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("theme defaults to system when not provided")
    void themeDefaultsToSystem() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 

        assertThat(profile.theme()).isEqualTo("system");
    }

    @Test
    @DisplayName("createdAt is set to now when not provided")
    void createdAtDefaultsToNow() { 
        Instant before = Instant.now().minusSeconds(1); 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 
        Instant after = Instant.now().plusSeconds(1); 

        assertThat(profile.createdAt()).isAfter(before).isBefore(after); 
    }

    @Test
    @DisplayName("updatedAt defaults to createdAt when not explicitly set")
    void updatedAtDefaultsToCreatedAt() { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 

        assertThat(profile.updatedAt()).isEqualTo(profile.createdAt()); 
    }

    @Test
    @DisplayName("withUpdatedAt creates new profile with updated timestamp")
    void withUpdatedAtCreatesNewProfile() { 
        UserProfile original = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("alice@example.com")
                .build(); 

        Instant newTime = Instant.now().plusSeconds(60); 
        UserProfile updated = original.withUpdatedAt(newTime); 

        assertThat(updated.updatedAt()).isEqualTo(newTime); 
        assertThat(updated.userId()).isEqualTo(original.userId()); 
        assertThat(updated.email()).isEqualTo(original.email()); 
    }

    @Test
    @DisplayName("toBuilder produces builder pre-filled with current values")
    void toBuilderPreserveValues() { 
        UserProfile original = UserProfile.builder() 
                .userId("user-456")
                .tenantId("tenant-xyz")
                .email("bob@example.com")
                .displayName("Bob")
                .timezone("America/New_York")
                .theme("dark")
                .notificationsEnabled(false) 
                .build(); 

        UserProfile copy = original.toBuilder().build(); 

        assertThat(copy.userId()).isEqualTo("user-456");
        assertThat(copy.displayName()).isEqualTo("Bob");
        assertThat(copy.timezone()).isEqualTo("America/New_York");
        assertThat(copy.theme()).isEqualTo("dark");
        assertThat(copy.notificationsEnabled()).isFalse(); 
    }
}
