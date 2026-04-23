package com.ghatana.platform.security.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiKey}.
 *
 * @doc.type class
 * @doc.purpose ApiKey value object tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("ApiKey")
class ApiKeyTest {

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("should not be expired when expiresAt is null")
        void shouldNotBeExpiredWhenNull() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not be expired when expiresAt is in the future")
        void shouldNotBeExpiredWhenFuture() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test")
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should be expired when expiresAt is in the past")
        void shouldBeExpiredWhenPast() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test")
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isExpired()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("should be valid when enabled and not expired")
        void shouldBeValidWhenEnabledAndNotExpired() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should not be valid when disabled")
        void shouldNotBeValidWhenDisabled() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test")
                    .enabled(false) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isValid()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not be valid when expired")
        void shouldNotBeValidWhenExpired() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isValid()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("updateLastUsed")
    class UpdateLastUsed {

        @Test
        @DisplayName("should update lastUsedAt to current time")
        void shouldUpdateLastUsed() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.getLastUsedAt()).isNull(); // GH-90000

            Instant before = Instant.now(); // GH-90000
            key.updateLastUsed(); // GH-90000
            Instant after = Instant.now(); // GH-90000

            assertThat(key.getLastUsedAt()) // GH-90000
                    .isNotNull() // GH-90000
                    .isBetween(before, after); // GH-90000
        }
    }

    @Nested
    @DisplayName("addRole / addPermission")
    class RolesAndPermissions {

        @Test
        @DisplayName("should add role and return self for chaining")
        void shouldAddRole() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            ApiKey result = key.addRole("ADMIN");

            assertThat(result).isSameAs(key); // GH-90000
            assertThat(key.getRoles()).contains("ADMIN");
        }

        @Test
        @DisplayName("should add permission and return self for chaining")
        void shouldAddPermission() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            ApiKey result = key.addPermission("event:read:all");

            assertThat(result).isSameAs(key); // GH-90000
            assertThat(key.getPermissions()).contains("event:read:all");
        }

        @Test
        @DisplayName("should not duplicate roles")
        void shouldNotDuplicateRoles() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            key.addRole("ADMIN").addRole("ADMIN");

            assertThat(key.getRoles()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should support chaining multiple roles")
        void shouldChainRoles() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            key.addRole("ADMIN").addRole("READER").addPermission("event:*:*");

            assertThat(key.getRoles()).containsExactlyInAnyOrder("ADMIN", "READER"); // GH-90000
            assertThat(key.getPermissions()).contains("event:*:*");
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should generate UUID id by default")
        void shouldGenerateId() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.getId()).isNotNull().isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should be enabled by default")
        void shouldBeEnabledByDefault() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should have empty roles by default")
        void shouldHaveEmptyRolesByDefault() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.getRoles()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should have createdAt set by default")
        void shouldHaveCreatedAt() { // GH-90000
            Instant before = Instant.now(); // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test").build();

            assertThat(key.getCreatedAt()).isNotNull().isAfterOrEqualTo(before); // GH-90000
        }
    }
}
