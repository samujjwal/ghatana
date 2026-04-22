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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RbacPermissionEvaluator}.
 *
 * @doc.type class
 * @doc.purpose RbacPermissionEvaluator unit tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("RbacPermissionEvaluator [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class RbacPermissionEvaluatorTest {

    @Mock
    private PolicyService policyService;

    private RbacPermissionEvaluator evaluator;

    @BeforeEach
    void setUp() { // GH-90000
        evaluator = new RbacPermissionEvaluator(policyService); // GH-90000
    }

    @Nested
    @DisplayName("hasPermission(role, permission) [GH-90000]")
    class SingleRole {

        @Test
        @DisplayName("should return true when policy grants permission [GH-90000]")
        void shouldReturnTrueWhenGranted() { // GH-90000
            when(policyService.hasPermission("ADMIN", "*", "event:read:all")).thenReturn(true); // GH-90000

            assertThat(evaluator.hasPermission("ADMIN", "event:read:all")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false when policy denies permission [GH-90000]")
        void shouldReturnFalseWhenDenied() { // GH-90000
            when(policyService.hasPermission("GUEST", "*", "event:write:all")).thenReturn(false); // GH-90000

            assertThat(evaluator.hasPermission("GUEST", "event:write:all")).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("hasPermission(role, roles, permission) [GH-90000]")
    class MultipleRoles {

        @Test
        @DisplayName("should return true when any role has permission [GH-90000]")
        void shouldReturnTrueWhenAnyRoleMatches() { // GH-90000
            lenient().when(policyService.hasPermission("READER", "*", "event:read:all")).thenReturn(false); // GH-90000
            when(policyService.hasPermission("ADMIN", "*", "event:read:all")).thenReturn(true); // GH-90000

            assertThat(evaluator.hasPermission("ignored", Set.of("READER", "ADMIN"), "event:read:all")) // GH-90000
                    .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false when no role has permission [GH-90000]")
        void shouldReturnFalseWhenNoRoleMatches() { // GH-90000
            when(policyService.hasPermission("GUEST", "*", "event:write:all")).thenReturn(false); // GH-90000
            when(policyService.hasPermission("VIEWER", "*", "event:write:all")).thenReturn(false); // GH-90000

            assertThat(evaluator.hasPermission("ignored", Set.of("GUEST", "VIEWER"), "event:write:all")) // GH-90000
                    .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should return false for empty roles set [GH-90000]")
        void shouldReturnFalseForEmptyRoles() { // GH-90000
            assertThat(evaluator.hasPermission("role", Set.of(), "event:read:all")) // GH-90000
                    .isFalse(); // GH-90000
        }
    }
}
