/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper(); 

    @Test
    @DisplayName("UserProfile serializes to valid JSON")
    void userProfile_serializesToJson() throws Exception { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("user@example.com")
                .displayName("John Doe")
                .avatarUrl("https://example.com/avatar.jpg")
                .preferredLanguage("en")
                .timezone("America/Los_Angeles")
                .theme("dark")
                .notificationsEnabled(true) 
                .build(); 

        String json = objectMapper.writeValueAsString(profile); 

        assertThat(json).contains("\"userId\":\"user-123\""); 
        assertThat(json).contains("\"tenantId\":\"tenant-abc\""); 
        assertThat(json).contains("\"email\":\"user@example.com\""); 
        assertThat(json).contains("\"displayName\":\"John Doe\""); 
        assertThat(json).contains("\"avatarUrl\":\"https://example.com/avatar.jpg\""); 
        assertThat(json).contains("\"preferredLanguage\":\"en\""); 
        assertThat(json).contains("\"timezone\":\"America/Los_Angeles\""); 
        assertThat(json).contains("\"theme\":\"dark\""); 
        assertThat(json).contains("\"notificationsEnabled\":true"); 
    }

    @Test
    @DisplayName("UserProfile with null avatarUrl omits the field (Jackson default)")
    void userProfile_nullAvatarUrl_omitsField() throws Exception { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-123")
                .tenantId("tenant-abc")
                .email("user@example.com")
                .displayName("John Doe")
                .avatarUrl(null) 
                .preferredLanguage("en")
                .timezone("America/Los_Angeles")
                .theme("light")
                .notificationsEnabled(false) 
                .build(); 

        String json = objectMapper.writeValueAsString(profile); 

        // Jackson's default behavior is to omit null fields
        assertThat(json).doesNotContain("avatarUrl");
        assertThat(json).contains("\"userId\":\"user-123\""); 
        assertThat(json).contains("\"tenantId\":\"tenant-abc\""); 
    }

    @Test
    @DisplayName("UserProfile deserializes from JSON")
    void userProfile_deserializesFromJson() throws Exception { 
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

        UserProfile profile = objectMapper.readValue(json, UserProfile.class); 

        assertThat(profile.userId()).isEqualTo("user-456");
        assertThat(profile.tenantId()).isEqualTo("tenant-xyz");
        assertThat(profile.email()).isEqualTo("jane@example.com");
        assertThat(profile.displayName()).isEqualTo("Jane Doe");
        assertThat(profile.avatarUrl()).isEqualTo("https://example.com/jane.jpg");
        assertThat(profile.preferredLanguage()).isEqualTo("es");
        assertThat(profile.timezone()).isEqualTo("Europe/Madrid");
        assertThat(profile.theme()).isEqualTo("dark");
        assertThat(profile.notificationsEnabled()).isTrue(); 
    }

    @Test
    @DisplayName("UserProfile round-trip serialization preserves data")
    void userProfile_roundTrip_preservesData() throws Exception { 
        UserProfile original = UserProfile.builder() 
                .userId("user-789")
                .tenantId("tenant-def")
                .email("roundtrip@example.com")
                .displayName("Round Trip Test")
                .avatarUrl("https://example.com/roundtrip.jpg")
                .preferredLanguage("fr")
                .timezone("Asia/Tokyo")
                .theme("system")
                .notificationsEnabled(true) 
                .build(); 

        String json = objectMapper.writeValueAsString(original); 
        UserProfile deserialized = objectMapper.readValue(json, UserProfile.class); 

        assertThat(deserialized.userId()).isEqualTo(original.userId()); 
        assertThat(deserialized.tenantId()).isEqualTo(original.tenantId()); 
        assertThat(deserialized.email()).isEqualTo(original.email()); 
        assertThat(deserialized.displayName()).isEqualTo(original.displayName()); 
        assertThat(deserialized.avatarUrl()).isEqualTo(original.avatarUrl()); 
        assertThat(deserialized.preferredLanguage()).isEqualTo(original.preferredLanguage()); 
        assertThat(deserialized.timezone()).isEqualTo(original.timezone()); 
        assertThat(deserialized.theme()).isEqualTo(original.theme()); 
        assertThat(deserialized.notificationsEnabled()).isEqualTo(original.notificationsEnabled()); 
    }

    @Test
    @DisplayName("UserProfile handles special characters in fields")
    void userProfile_handlesSpecialCharacters() throws Exception { 
        UserProfile profile = UserProfile.builder() 
                .userId("user-special")
                .tenantId("tenant-special")
                .email("user+tag@example.com")
                .displayName("John \"The Rock\" Doe") 
                .preferredLanguage("en")
                .timezone("America/Los_Angeles")
                .theme("dark")
                .notificationsEnabled(true) 
                .build(); 

        String json = objectMapper.writeValueAsString(profile); 
        UserProfile deserialized = objectMapper.readValue(json, UserProfile.class); 

        assertThat(deserialized.email()).isEqualTo("user+tag@example.com");
        assertThat(deserialized.displayName()).isEqualTo("John \"The Rock\" Doe"); 
    }

    @Test
    @DisplayName("UserProfile handles boolean notificationsEnabled field")
    void userProfile_handlesBooleanField() throws Exception { 
        UserProfile enabled = UserProfile.builder() 
                .userId("user-1")
                .tenantId("tenant-1")
                .email("enabled@example.com")
                .notificationsEnabled(true) 
                .build(); 

        UserProfile disabled = UserProfile.builder() 
                .userId("user-2")
                .tenantId("tenant-1")
                .email("disabled@example.com")
                .notificationsEnabled(false) 
                .build(); 

        String enabledJson = objectMapper.writeValueAsString(enabled); 
        String disabledJson = objectMapper.writeValueAsString(disabled); 

        assertThat(enabledJson).contains("\"notificationsEnabled\":true"); 
        assertThat(disabledJson).contains("\"notificationsEnabled\":false"); 
    }
}
