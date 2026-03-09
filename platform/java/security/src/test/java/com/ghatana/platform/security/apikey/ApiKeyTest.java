package com.ghatana.platform.security.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

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
        void shouldNotBeExpiredWhenNull() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should not be expired when expiresAt is in the future")
        void shouldNotBeExpiredWhenFuture() {
            ApiKey key = ApiKey.builder()
                    .key("ak_test")
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();
            assertThat(key.isExpired()).isFalse();
        }

        @Test
        @DisplayName("should be expired when expiresAt is in the past")
        void shouldBeExpiredWhenPast() {
            ApiKey key = ApiKey.builder()
                    .key("ak_test")
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();
            assertThat(key.isExpired()).isTrue();
        }
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("should be valid when enabled and not expired")
        void shouldBeValidWhenEnabledAndNotExpired() {
            ApiKey key = ApiKey.builder()
                    .key("ak_test")
                    .enabled(true)
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();
            assertThat(key.isValid()).isTrue();
        }

        @Test
        @DisplayName("should not be valid when disabled")
        void shouldNotBeValidWhenDisabled() {
            ApiKey key = ApiKey.builder()
                    .key("ak_test")
                    .enabled(false)
                    .build();
            assertThat(key.isValid()).isFalse();
        }

        @Test
        @DisplayName("should not be valid when expired")
        void shouldNotBeValidWhenExpired() {
            ApiKey key = ApiKey.builder()
                    .key("ak_test")
                    .enabled(true)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();
            assertThat(key.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateLastUsed")
    class UpdateLastUsed {

        @Test
        @DisplayName("should update lastUsedAt to current time")
        void shouldUpdateLastUsed() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.getLastUsedAt()).isNull();

            Instant before = Instant.now();
            key.updateLastUsed();
            Instant after = Instant.now();

            assertThat(key.getLastUsedAt())
                    .isNotNull()
                    .isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("addRole / addPermission")
    class RolesAndPermissions {

        @Test
        @DisplayName("should add role and return self for chaining")
        void shouldAddRole() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            ApiKey result = key.addRole("ADMIN");

            assertThat(result).isSameAs(key);
            assertThat(key.getRoles()).contains("ADMIN");
        }

        @Test
        @DisplayName("should add permission and return self for chaining")
        void shouldAddPermission() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            ApiKey result = key.addPermission("event:read:all");

            assertThat(result).isSameAs(key);
            assertThat(key.getPermissions()).contains("event:read:all");
        }

        @Test
        @DisplayName("should not duplicate roles")
        void shouldNotDuplicateRoles() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            key.addRole("ADMIN").addRole("ADMIN");

            assertThat(key.getRoles()).hasSize(1);
        }

        @Test
        @DisplayName("should support chaining multiple roles")
        void shouldChainRoles() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            key.addRole("ADMIN").addRole("READER").addPermission("event:*:*");

            assertThat(key.getRoles()).containsExactlyInAnyOrder("ADMIN", "READER");
            assertThat(key.getPermissions()).contains("event:*:*");
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should generate UUID id by default")
        void shouldGenerateId() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.getId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should be enabled by default")
        void shouldBeEnabledByDefault() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should have empty roles by default")
        void shouldHaveEmptyRolesByDefault() {
            ApiKey key = ApiKey.builder().key("ak_test").build();
            assertThat(key.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("should have createdAt set by default")
        void shouldHaveCreatedAt() {
            Instant before = Instant.now();
            ApiKey key = ApiKey.builder().key("ak_test").build();

            assertThat(key.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
        }
    }
}
