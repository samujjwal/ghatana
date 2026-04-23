/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.services.userprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Jackson JSON serialization/deserialization of UserProfile.
 * Verifies that the Jackson ObjectMapper integration works correctly after
 * replacing manual JSON parsing.
 *
 * @doc.type class
 * @doc.purpose Tests for Jackson JSON serialization of UserProfile
 * @doc.layer shared-service
 * @doc.pattern Test
 */
@Tag("unit")
@DisplayName("UserProfile — Jackson JSON serialization tests")
class UserProfileJacksonSerializationTest {

    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper(); // GH-90000

    @Test
    @DisplayName("UserProfile serializes to valid JSON")
    void userProfile_serializesToJson() throws Exception { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("user@example.com")
                .displayName("John Doe")
                .avatarUrl("https://example.com/avatar.jpg")
                .preferredLanguage("en")
                .timezone("America/Los_Angeles")
                .theme("dark")
                .notificationsEnabled(true) // GH-90000
                .build(); // GH-90000

        String json = objectMapper.writeValueAsString(profile); // GH-90000

        assertThat(json).contains("\"userId\":\"user-123\""); // GH-90000
        assertThat(json).contains("\"tenantId\":\"tenant-abc\""); // GH-90000
        assertThat(json).contains("\"email\":\"user@example.com\""); // GH-90000
        assertThat(json).contains("\"displayName\":\"John Doe\""); // GH-90000
        assertThat(json).contains("\"avatarUrl\":\"https://example.com/avatar.jpg\""); // GH-90000
        assertThat(json).contains("\"preferredLanguage\":\"en\""); // GH-90000
        assertThat(json).contains("\"timezone\":\"America/Los_Angeles\""); // GH-90000
        assertThat(json).contains("\"theme\":\"dark\""); // GH-90000
        assertThat(json).contains("\"notificationsEnabled\":true"); // GH-90000
    }

    @Test
    @DisplayName("UserProfile with null avatarUrl omits the field (Jackson default)")
    void userProfile_nullAvatarUrl_omitsField() throws Exception { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("user@example.com")
                .displayName("John Doe")
                .avatarUrl(null) // GH-90000
                .preferredLanguage("en")
                .timezone("America/Los_Angeles")
                .theme("light")
                .notificationsEnabled(false) // GH-90000
                .build(); // GH-90000

        String json = objectMapper.writeValueAsString(profile); // GH-90000

        // Jackson's default behavior is to omit null fields
        assertThat(json).doesNotContain("avatarUrl");
        assertThat(json).contains("\"userId\":\"user-123\""); // GH-90000
        assertThat(json).contains("\"tenantId\":\"tenant-abc\""); // GH-90000
    }

    @Test
    @DisplayName("UserProfile deserializes from JSON")
    void userProfile_deserializesFromJson() throws Exception { // GH-90000
        String json = """
            {
                "userId": "user-456",
                "tenantId": "tenant-xyz",
                "email": "jane@example.com",
                "displayName": "Jane Doe",
                "avatarUrl": "https://example.com/jane.jpg",
                "preferredLanguage": "es",
                "timezone": "Europe/Madrid",
                "theme": "dark",
                "notificationsEnabled": true
            }
            """;

        UserProfile profile = objectMapper.readValue(json, UserProfile.class); // GH-90000

        assertThat(profile.userId()).isEqualTo("user-456");
        assertThat(profile.tenantId()).isEqualTo("tenant-xyz");
        assertThat(profile.email()).isEqualTo("jane@example.com");
        assertThat(profile.displayName()).isEqualTo("Jane Doe");
        assertThat(profile.avatarUrl()).isEqualTo("https://example.com/jane.jpg");
        assertThat(profile.preferredLanguage()).isEqualTo("es");
        assertThat(profile.timezone()).isEqualTo("Europe/Madrid");
        assertThat(profile.theme()).isEqualTo("dark");
        assertThat(profile.notificationsEnabled()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("UserProfile round-trip serialization preserves data")
    void userProfile_roundTrip_preservesData() throws Exception { // GH-90000
        UserProfile original = UserProfile.builder() // GH-90000
                .userId("user-789")
                .tenantId("tenant-def")
                .email("roundtrip@example.com")
                .displayName("Round Trip Test")
                .avatarUrl("https://example.com/roundtrip.jpg")
                .preferredLanguage("fr")
                .timezone("Asia/Tokyo")
                .theme("system")
                .notificationsEnabled(true) // GH-90000
                .build(); // GH-90000

        String json = objectMapper.writeValueAsString(original); // GH-90000
        UserProfile deserialized = objectMapper.readValue(json, UserProfile.class); // GH-90000

        assertThat(deserialized.userId()).isEqualTo(original.userId()); // GH-90000
        assertThat(deserialized.tenantId()).isEqualTo(original.tenantId()); // GH-90000
        assertThat(deserialized.email()).isEqualTo(original.email()); // GH-90000
        assertThat(deserialized.displayName()).isEqualTo(original.displayName()); // GH-90000
        assertThat(deserialized.avatarUrl()).isEqualTo(original.avatarUrl()); // GH-90000
        assertThat(deserialized.preferredLanguage()).isEqualTo(original.preferredLanguage()); // GH-90000
        assertThat(deserialized.timezone()).isEqualTo(original.timezone()); // GH-90000
        assertThat(deserialized.theme()).isEqualTo(original.theme()); // GH-90000
        assertThat(deserialized.notificationsEnabled()).isEqualTo(original.notificationsEnabled()); // GH-90000
    }

    @Test
    @DisplayName("UserProfile handles special characters in fields")
    void userProfile_handlesSpecialCharacters() throws Exception { // GH-90000
        UserProfile profile = UserProfile.builder() // GH-90000
                .userId("user-special")
                .tenantId("tenant-special")
                .email("user+tag@example.com")
                .displayName("John \"The Rock\" Doe") // GH-90000
                .preferredLanguage("en")
                .timezone("America/Los_Angeles")
                .theme("dark")
                .notificationsEnabled(true) // GH-90000
                .build(); // GH-90000

        String json = objectMapper.writeValueAsString(profile); // GH-90000
        UserProfile deserialized = objectMapper.readValue(json, UserProfile.class); // GH-90000

        assertThat(deserialized.email()).isEqualTo("user+tag@example.com");
        assertThat(deserialized.displayName()).isEqualTo("John \"The Rock\" Doe"); // GH-90000
    }

    @Test
    @DisplayName("UserProfile handles boolean notificationsEnabled field")
    void userProfile_handlesBooleanField() throws Exception { // GH-90000
        UserProfile enabled = UserProfile.builder() // GH-90000
                .userId("user-1")
                .tenantId("tenant-1")
                .email("enabled@example.com")
                .notificationsEnabled(true) // GH-90000
                .build(); // GH-90000

        UserProfile disabled = UserProfile.builder() // GH-90000
                .userId("user-2")
                .tenantId("tenant-1")
                .email("disabled@example.com")
                .notificationsEnabled(false) // GH-90000
                .build(); // GH-90000

        String enabledJson = objectMapper.writeValueAsString(enabled); // GH-90000
        String disabledJson = objectMapper.writeValueAsString(disabled); // GH-90000

        assertThat(enabledJson).contains("\"notificationsEnabled\":true"); // GH-90000
        assertThat(disabledJson).contains("\"notificationsEnabled\":false"); // GH-90000
    }
}
