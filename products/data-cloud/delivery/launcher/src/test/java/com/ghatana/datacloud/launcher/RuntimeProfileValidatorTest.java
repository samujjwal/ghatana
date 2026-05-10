/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RuntimeProfileValidator}.
 *
 * <p>Covers the P0-1 requirement that production profiles fail closed when
 * any required trust-critical dependency is absent.
 *
 * @doc.type class
 * @doc.purpose Verify fail-closed startup validation for all deployment profiles
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RuntimeProfileValidator — fail-closed startup gate")
class RuntimeProfileValidatorTest {

    // =========================================================================
    // LOCAL profile — all optional, must not throw
    // =========================================================================

    @Nested
    @DisplayName("LOCAL profile")
    class LocalProfile {

        @Test
        @DisplayName("passes with no dependencies configured")
        void localProfile_noDependencies_passes() {
            assertThatCode(() ->
                RuntimeProfileValidator.builder()
                    .deploymentProfile("local")
                    .build()
                    .validate())
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("passes even when auth, audit, policy are all absent")
        void localProfile_allAbsent_passes() {
            assertThatCode(() ->
                RuntimeProfileValidator.builder()
                    .deploymentProfile("local")
                    .authConfigured(false)
                    .auditConfigured(false)
                    .policyEngineConfigured(false)
                    .durableEntityStore(false)
                    .durableEventStore(false)
                    .build()
                    .validate())
                .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // SOVEREIGN profile — auth/audit/policy required when strict-tenant
    // =========================================================================

    @Nested
    @DisplayName("SOVEREIGN profile")
    class SovereignProfile {

        @Test
        @DisplayName("passes when auth, audit, policy configured (strict-tenant)")
        void sovereignProfile_fullySatisfied_passes() {
            assertThatCode(() ->
                RuntimeProfileValidator.builder()
                    .deploymentProfile("sovereign")
                    .strictTenantResolution(true)
                    .authConfigured(true)
                    .auditConfigured(true)
                    .policyEngineConfigured(true)
                    .build()
                    .validate())
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("fails without auth in strict-tenant sovereign mode")
        void sovereignProfile_missingAuth_fails() {
            assertThatThrownBy(() ->
                RuntimeProfileValidator.builder()
                    .deploymentProfile("sovereign")
                    .strictTenantResolution(true)
                    .authConfigured(false)
                    .auditConfigured(true)
                    .policyEngineConfigured(true)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC-P0-001")
                .hasMessageContaining("Authentication");
        }
    }

    // =========================================================================
    // PRODUCTION profile — full fail-closed
    // =========================================================================

    @Nested
    @DisplayName("PRODUCTION profile — fail-closed tests")
    class ProductionProfile {

        private RuntimeProfileValidator.Builder fullyConfiguredProduction() {
            return RuntimeProfileValidator.builder()
                .deploymentProfile("production")
                .strictTenantResolution(true)
                .authConfigured(true)
                .auditConfigured(true)
                .policyEngineConfigured(true)
                .durableEntityStore(true)
                .durableEventStore(true)
                .durableIdempotencyStore(true)
                .transactionManagerConfigured(true)
                .metricsConfigured(true)
                .traceExportConfigured(true)
                .completionServiceConfigured(true);
        }

        @Test
        @DisplayName("passes when all required dependencies are present")
        void production_allConfigured_passes() {
            assertThatCode(() -> fullyConfiguredProduction().build().validate())
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("fails without auth — P0-1")
        void production_missingAuth_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .authConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authentication");
        }

        @Test
        @DisplayName("fails without audit — P0-1")
        void production_missingAudit_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .auditConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Audit service");
        }

        @Test
        @DisplayName("fails without policy engine — P0-1")
        void production_missingPolicyEngine_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .policyEngineConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Policy engine");
        }

        @Test
        @DisplayName("fails without durable idempotency store — P0-1")
        void production_missingIdempotencyStore_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .durableIdempotencyStore(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency store");
        }

        @Test
        @DisplayName("fails without transaction manager — P0-1")
        void production_missingTransactionManager_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .transactionManagerConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transaction manager");
        }

        @Test
        @DisplayName("fails without metrics — P0-1")
        void production_missingMetrics_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .metricsConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Metrics collector");
        }

        @Test
        @DisplayName("fails without trace export — P0-1")
        void production_missingTraceExport_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .traceExportConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trace export");
        }

        @Test
        @DisplayName("fails without AI completion service — P0-1")
        void production_missingCompletionService_fails() {
            assertThatThrownBy(() ->
                fullyConfiguredProduction()
                    .completionServiceConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completion service");
        }

        @Test
        @DisplayName("accumulates ALL violations in one exception — operators fix everything in one cycle")
        void production_multipleViolations_accumulatesAll() {
            assertThatThrownBy(() ->
                RuntimeProfileValidator.builder()
                    .deploymentProfile("production")
                    .strictTenantResolution(true)
                    .authConfigured(false)
                    .auditConfigured(false)
                    .policyEngineConfigured(false)
                    .durableEntityStore(false)
                    .durableEventStore(false)
                    .durableIdempotencyStore(false)
                    .transactionManagerConfigured(false)
                    .metricsConfigured(false)
                    .traceExportConfigured(false)
                    .completionServiceConfigured(false)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authentication")
                .hasMessageContaining("Audit service")
                .hasMessageContaining("Policy engine")
                .hasMessageContaining("idempotency store")
                .hasMessageContaining("Transaction manager")
                .hasMessageContaining("Metrics collector")
                .hasMessageContaining("Trace export")
                .hasMessageContaining("completion service");
        }
    }

    // =========================================================================
    // STAGING profile — same as production
    // =========================================================================

    @Nested
    @DisplayName("STAGING profile — fail-closed tests")
    class StagingProfile {

        @Test
        @DisplayName("fails without auth — staging is production-like")
        void staging_missingAuth_fails() {
            assertThatThrownBy(() ->
                RuntimeProfileValidator.builder()
                    .deploymentProfile("staging")
                    .strictTenantResolution(true)
                    .authConfigured(false)
                    .auditConfigured(true)
                    .policyEngineConfigured(true)
                    .durableEntityStore(true)
                    .durableEventStore(true)
                    .durableIdempotencyStore(true)
                    .transactionManagerConfigured(true)
                    .metricsConfigured(true)
                    .traceExportConfigured(true)
                    .completionServiceConfigured(true)
                    .build()
                    .validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authentication");
        }
    }

    // =========================================================================
    // toPostureSnapshot
    // =========================================================================

    @Nested
    @DisplayName("toPostureSnapshot")
    class PostureSnapshot {

        @Test
        @DisplayName("includes all posture fields")
        void postureSnapshot_containsAllFields() {
            Map<String, Object> snapshot = RuntimeProfileValidator.builder()
                .deploymentProfile("production")
                .authConfigured(true)
                .auditConfigured(true)
                .policyEngineConfigured(true)
                .durableEntityStore(true)
                .durableEventStore(true)
                .durableIdempotencyStore(true)
                .transactionManagerConfigured(true)
                .metricsConfigured(true)
                .traceExportConfigured(true)
                .completionServiceConfigured(true)
                .build()
                .toPostureSnapshot();

            assertThat(snapshot).containsKey("profile");
            assertThat(snapshot).containsKey("authenticationConfigured");
            assertThat(snapshot).containsKey("auditConfigured");
            assertThat(snapshot).containsKey("policyConfigured");
            assertThat(snapshot).containsKey("entityStoreDurable");
            assertThat(snapshot).containsKey("coreEventStoreDurable");
            assertThat(snapshot).containsKey("idempotencyStoreDurable");
            assertThat(snapshot).containsKey("transactionManager");
            assertThat(snapshot).containsKey("metricsConfigured");
            assertThat(snapshot).containsKey("traceConfigured");
            assertThat(snapshot).containsKey("aiCompletion");
            assertThat(snapshot.get("profile")).isEqualTo("production");
        }

        @Test
        @DisplayName("local profile does not expose production-only fields as boolean")
        void postureSnapshot_localProfile_nonProductionFieldsAreNA() {
            Map<String, Object> snapshot = RuntimeProfileValidator.builder()
                .deploymentProfile("local")
                .build()
                .toPostureSnapshot();

            assertThat(snapshot.get("idempotencyStoreDurable")).isEqualTo("n/a");
            assertThat(snapshot.get("transactionManager")).isEqualTo("n/a");
            assertThat(snapshot.get("metricsConfigured")).isEqualTo("n/a");
        }
    }

    // =========================================================================
    // Static helper tests
    // =========================================================================

    @Nested
    @DisplayName("Profile classification helpers")
    class ProfileHelpers {

        @Test
        @DisplayName("isLocal recognises 'local' and 'embedded'")
        void isLocal_recognisesLocalAndEmbedded() {
            assertThat(RuntimeProfileValidator.isLocal("local")).isTrue();
            assertThat(RuntimeProfileValidator.isLocal("LOCAL")).isTrue();
            assertThat(RuntimeProfileValidator.isLocal("embedded")).isTrue();
            assertThat(RuntimeProfileValidator.isLocal(null)).isTrue();
            assertThat(RuntimeProfileValidator.isLocal("production")).isFalse();
        }

        @Test
        @DisplayName("isProductionLike recognises 'production' and 'staging'")
        void isProductionLike_recognisesProductionAndStaging() {
            assertThat(RuntimeProfileValidator.isProductionLike("production")).isTrue();
            assertThat(RuntimeProfileValidator.isProductionLike("staging")).isTrue();
            assertThat(RuntimeProfileValidator.isProductionLike("PRODUCTION")).isTrue();
            assertThat(RuntimeProfileValidator.isProductionLike("local")).isFalse();
            assertThat(RuntimeProfileValidator.isProductionLike("sovereign")).isFalse();
            assertThat(RuntimeProfileValidator.isProductionLike(null)).isFalse();
        }

        @Test
        @DisplayName("isSovereign recognises 'sovereign'")
        void isSovereign_recognisesSovereign() {
            assertThat(RuntimeProfileValidator.isSovereign("sovereign")).isTrue();
            assertThat(RuntimeProfileValidator.isSovereign("SOVEREIGN")).isTrue();
            assertThat(RuntimeProfileValidator.isSovereign("production")).isFalse();
            assertThat(RuntimeProfileValidator.isSovereign(null)).isFalse();
        }
    }
}
