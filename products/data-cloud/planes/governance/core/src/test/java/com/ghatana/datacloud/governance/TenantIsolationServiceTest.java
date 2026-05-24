/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3: Contract tests for TenantIsolationService.
 *
 * <p>These tests enforce:
 * <ul>
 *   <li>Tenant context validation</li>
 *   <li>Cross-tenant access prevention</li>
 *   <li>Resource ownership validation</li>
 *   <li>Operation permission checks</li>
 * </ul>
 */
@DisplayName("Tenant Isolation Service Tests (Phase 3)")
class TenantIsolationServiceTest {

    private final TenantIsolationService service = new TenantIsolationService();

    // =========================================================================
    //  Tenant Context Validation
    // =========================================================================

    @Nested
    @DisplayName("Tenant Context Validation")
    class ContextValidationTests {

        @Test
        @DisplayName("valid tenant context passes validation")
        void validTenantContextPassesValidation() {
            // Should not throw
            service.validateTenantContext("tenant-123");
        }

        @Test
        @DisplayName("null tenant ID throws exception")
        void nullTenantIdThrowsException() {
            assertThatThrownBy(() -> service.validateTenantContext(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId must not be null");
        }

        @Test
        @DisplayName("blank tenant ID throws exception")
        void blankTenantIdThrowsException() {
            assertThatThrownBy(() -> service.validateTenantContext(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
        }

        @Test
        @DisplayName("whitespace-only tenant ID throws exception")
        void whitespaceOnlyTenantIdThrowsException() {
            assertThatThrownBy(() -> service.validateTenantContext("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
        }
    }

    // =========================================================================
    //  Cross-Tenant Access Prevention
    // =========================================================================

    @Nested
    @DisplayName("Cross-Tenant Access Prevention")
    class CrossTenantAccessTests {

        @Test
        @DisplayName("same tenant access is allowed")
        void sameTenantAccessIsAllowed() {
            // Should not throw
            service.validateCrossTenantAccess("tenant-123", "tenant-123");
        }

        @Test
        @DisplayName("cross-tenant access is denied")
        void crossTenantAccessIsDenied() {
            assertThatThrownBy(() -> service.validateCrossTenantAccess("tenant-123", "tenant-456"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Cross-tenant access denied")
                .hasMessageContaining("tenant-123")
                .hasMessageContaining("tenant-456");
        }

        @Test
        @DisplayName("null source tenant ID throws exception")
        void nullSourceTenantIdThrowsException() {
            assertThatThrownBy(() -> service.validateCrossTenantAccess(null, "tenant-456"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId must not be null");
        }

        @Test
        @DisplayName("null target tenant ID throws exception")
        void nullTargetTenantIdThrowsException() {
            assertThatThrownBy(() -> service.validateCrossTenantAccess("tenant-123", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId must not be null");
        }
    }

    // =========================================================================
    //  Resource Ownership Validation
    // =========================================================================

    @Nested
    @DisplayName("Resource Ownership Validation")
    class ResourceOwnershipTests {

        @Test
        @DisplayName("matching tenant ownership is allowed")
        void matchingTenantOwnershipIsAllowed() {
            // Should not throw
            service.validateResourceOwnership("tenant-123", "tenant-123");
        }

        @Test
        @DisplayName("mismatched tenant ownership is denied")
        void mismatchedTenantOwnershipIsDenied() {
            assertThatThrownBy(() -> service.validateResourceOwnership("tenant-123", "tenant-456"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Resource ownership violation")
                .hasMessageContaining("tenant-123")
                .hasMessageContaining("tenant-456");
        }

        @Test
        @DisplayName("null resource tenant ID throws exception")
        void nullResourceTenantIdThrowsException() {
            assertThatThrownBy(() -> service.validateResourceOwnership(null, "tenant-456"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId must not be null");
        }

        @Test
        @DisplayName("null requesting tenant ID throws exception")
        void nullRequestingTenantIdThrowsException() {
            assertThatThrownBy(() -> service.validateResourceOwnership("tenant-123", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId must not be null");
        }
    }

    // =========================================================================
    //  Operation Permission Checks
    // =========================================================================

    @Nested
    @DisplayName("Operation Permission Checks")
    class OperationPermissionTests {

        @Test
        @DisplayName("valid tenant operation is allowed")
        void validTenantOperationIsAllowed() {
            boolean allowed = service.isOperationAllowed("tenant-123", "entity", "READ");
            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("null tenant ID throws exception")
        void nullTenantIdThrowsException() {
            assertThatThrownBy(() -> service.isOperationAllowed(null, "entity", "READ"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId must not be null");
        }

        @Test
        @DisplayName("blank tenant ID throws exception")
        void blankTenantIdThrowsException() {
            assertThatThrownBy(() -> service.isOperationAllowed("", "entity", "READ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
        }

        @Test
        @DisplayName("operation check is resource-type agnostic")
        void operationCheckIsResourceTypeAgnostic() {
            // Currently all operations are allowed on own data
            assertThat(service.isOperationAllowed("tenant-123", "entity", "READ")).isTrue();
            assertThat(service.isOperationAllowed("tenant-123", "event", "WRITE")).isTrue();
            assertThat(service.isOperationAllowed("tenant-123", "config", "DELETE")).isTrue();
        }
    }
}
