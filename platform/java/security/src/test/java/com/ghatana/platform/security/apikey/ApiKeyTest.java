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
@DisplayName("ApiKey [GH-90000]")
class ApiKeyTest {

    @Nested
    @DisplayName("isExpired [GH-90000]")
    class IsExpired {

        @Test
        @DisplayName("should not be expired when expiresAt is null [GH-90000]")
        void shouldNotBeExpiredWhenNull() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            assertThat(key.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not be expired when expiresAt is in the future [GH-90000]")
        void shouldNotBeExpiredWhenFuture() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test [GH-90000]")
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should be expired when expiresAt is in the past [GH-90000]")
        void shouldBeExpiredWhenPast() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test [GH-90000]")
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isExpired()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isValid [GH-90000]")
    class IsValid {

        @Test
        @DisplayName("should be valid when enabled and not expired [GH-90000]")
        void shouldBeValidWhenEnabledAndNotExpired() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test [GH-90000]")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isValid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should not be valid when disabled [GH-90000]")
        void shouldNotBeValidWhenDisabled() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test [GH-90000]")
                    .enabled(false) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isValid()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not be valid when expired [GH-90000]")
        void shouldNotBeValidWhenExpired() { // GH-90000
            ApiKey key = ApiKey.builder() // GH-90000
                    .key("ak_test [GH-90000]")
                    .enabled(true) // GH-90000
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS)) // GH-90000
                    .build(); // GH-90000
            assertThat(key.isValid()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("updateLastUsed [GH-90000]")
    class UpdateLastUsed {

        @Test
        @DisplayName("should update lastUsedAt to current time [GH-90000]")
        void shouldUpdateLastUsed() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
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
    @DisplayName("addRole / addPermission [GH-90000]")
    class RolesAndPermissions {

        @Test
        @DisplayName("should add role and return self for chaining [GH-90000]")
        void shouldAddRole() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            ApiKey result = key.addRole("ADMIN [GH-90000]");

            assertThat(result).isSameAs(key); // GH-90000
            assertThat(key.getRoles()).contains("ADMIN [GH-90000]");
        }

        @Test
        @DisplayName("should add permission and return self for chaining [GH-90000]")
        void shouldAddPermission() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            ApiKey result = key.addPermission("event:read:all [GH-90000]");

            assertThat(result).isSameAs(key); // GH-90000
            assertThat(key.getPermissions()).contains("event:read:all [GH-90000]");
        }

        @Test
        @DisplayName("should not duplicate roles [GH-90000]")
        void shouldNotDuplicateRoles() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            key.addRole("ADMIN [GH-90000]").addRole("ADMIN [GH-90000]");

            assertThat(key.getRoles()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should support chaining multiple roles [GH-90000]")
        void shouldChainRoles() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            key.addRole("ADMIN [GH-90000]").addRole("READER [GH-90000]").addPermission("event:*:* [GH-90000]");

            assertThat(key.getRoles()).containsExactlyInAnyOrder("ADMIN", "READER"); // GH-90000
            assertThat(key.getPermissions()).contains("event:*:* [GH-90000]");
        }
    }

    @Nested
    @DisplayName("builder defaults [GH-90000]")
    class BuilderDefaults {

        @Test
        @DisplayName("should generate UUID id by default [GH-90000]")
        void shouldGenerateId() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            assertThat(key.getId()).isNotNull().isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should be enabled by default [GH-90000]")
        void shouldBeEnabledByDefault() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            assertThat(key.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should have empty roles by default [GH-90000]")
        void shouldHaveEmptyRolesByDefault() { // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();
            assertThat(key.getRoles()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should have createdAt set by default [GH-90000]")
        void shouldHaveCreatedAt() { // GH-90000
            Instant before = Instant.now(); // GH-90000
            ApiKey key = ApiKey.builder().key("ak_test [GH-90000]").build();

            assertThat(key.getCreatedAt()).isNotNull().isAfterOrEqualTo(before); // GH-90000
        }
    }
}
