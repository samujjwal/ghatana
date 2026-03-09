package com.ghatana.platform.security.rbac;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RbacPermissionEvaluator}.
 *
 * @doc.type class
 * @doc.purpose RbacPermissionEvaluator unit tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("RbacPermissionEvaluator")
@ExtendWith(MockitoExtension.class)
class RbacPermissionEvaluatorTest {

    @Mock
    private PolicyService policyService;

    private RbacPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RbacPermissionEvaluator(policyService);
    }

    @Nested
    @DisplayName("hasPermission(role, permission)")
    class SingleRole {

        @Test
        @DisplayName("should return true when policy grants permission")
        void shouldReturnTrueWhenGranted() {
            when(policyService.hasPermission("ADMIN", "*", "event:read:all")).thenReturn(true);

            assertThat(evaluator.hasPermission("ADMIN", "event:read:all")).isTrue();
        }

        @Test
        @DisplayName("should return false when policy denies permission")
        void shouldReturnFalseWhenDenied() {
            when(policyService.hasPermission("GUEST", "*", "event:write:all")).thenReturn(false);

            assertThat(evaluator.hasPermission("GUEST", "event:write:all")).isFalse();
        }
    }

    @Nested
    @DisplayName("hasPermission(role, roles, permission)")
    class MultipleRoles {

        @Test
        @DisplayName("should return true when any role has permission")
        void shouldReturnTrueWhenAnyRoleMatches() {
            when(policyService.hasPermission("READER", "*", "event:read:all")).thenReturn(false);
            when(policyService.hasPermission("ADMIN", "*", "event:read:all")).thenReturn(true);

            assertThat(evaluator.hasPermission("ignored", Set.of("READER", "ADMIN"), "event:read:all"))
                    .isTrue();
        }

        @Test
        @DisplayName("should return false when no role has permission")
        void shouldReturnFalseWhenNoRoleMatches() {
            when(policyService.hasPermission("GUEST", "*", "event:write:all")).thenReturn(false);
            when(policyService.hasPermission("VIEWER", "*", "event:write:all")).thenReturn(false);

            assertThat(evaluator.hasPermission("ignored", Set.of("GUEST", "VIEWER"), "event:write:all"))
                    .isFalse();
        }

        @Test
        @DisplayName("should return false for empty roles set")
        void shouldReturnFalseForEmptyRoles() {
            assertThat(evaluator.hasPermission("role", Set.of(), "event:read:all"))
                    .isFalse();
        }
    }
}
