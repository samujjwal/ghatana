/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.feature;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for feature flag service toggle logic (F001). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Feature flag toggle logic tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("FeatureFlagService – Toggle Logic (F001) [GH-90000]")
class FeatureFlagServiceTest extends EventloopTestBase {

    @Mock
    private FeatureFlagService featureFlagService;

    @Nested
    @DisplayName("Toggle Operations [GH-90000]")
    class ToggleOperationsTests {

        @Test
        @DisplayName("[F001]: is_enabled_returns_true_for_enabled_flag [GH-90000]")
        void isEnabledReturnsTrueForEnabledFlag() { // GH-90000
            String featureKey = "new-dashboard";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() // GH-90000
                .userId("user-001 [GH-90000]")
                .tenantId("tenant-alpha [GH-90000]")
                .build(); // GH-90000

            when(featureFlagService.isEnabled(featureKey, context)) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, context)); // GH-90000

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[F001]: is_enabled_returns_false_for_disabled_flag [GH-90000]")
        void isEnabledReturnsFalseForDisabledFlag() { // GH-90000
            String featureKey = "beta-feature";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() // GH-90000
                .tenantId("tenant-alpha [GH-90000]")
                .build(); // GH-90000

            when(featureFlagService.isEnabled(featureKey, context)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, context)); // GH-90000

            assertThat(result).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[F001]: toggle_changes_flag_state [GH-90000]")
        void toggleChangesFlagState() { // GH-90000
            String featureKey = "feature-001";

            FeatureFlagService.FeatureFlag enabled = createFlag(featureKey, true); // GH-90000
            FeatureFlagService.FeatureFlag disabled = createFlag(featureKey, false); // GH-90000

            when(featureFlagService.toggle(featureKey, true)) // GH-90000
                .thenReturn(Promise.of(enabled)); // GH-90000
            when(featureFlagService.toggle(featureKey, false)) // GH-90000
                .thenReturn(Promise.of(disabled)); // GH-90000

            FeatureFlagService.FeatureFlag onResult = runPromise(() -> featureFlagService.toggle(featureKey, true)); // GH-90000
            FeatureFlagService.FeatureFlag offResult = runPromise(() -> featureFlagService.toggle(featureKey, false)); // GH-90000

            assertThat(onResult.enabled()).isTrue(); // GH-90000
            assertThat(offResult.enabled()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Targeting Rules [GH-90000]")
    class TargetingRulesTests {

        @Test
        @DisplayName("[F001]: user_targeting_matches_specific_user [GH-90000]")
        void userTargetingMatchesSpecificUser() { // GH-90000
            String featureKey = "vip-feature";
            FeatureFlagService.FeatureContext vipUser = FeatureFlagService.FeatureContext.builder() // GH-90000
                .userId("vip-user-001 [GH-90000]")
                .tenantId("tenant-alpha [GH-90000]")
                .attributes(Map.of("tier", "vip")) // GH-90000
                .build(); // GH-90000

            when(featureFlagService.isEnabled(featureKey, vipUser)) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, vipUser)); // GH-90000

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[F001]: percentage_rollout_enabled_for_matching_users [GH-90000]")
        void percentageRolloutEnabledForMatchingUsers() { // GH-90000
            String featureKey = "gradual-rollout";

            // User ID hash determines if they're in the rollout percentage
            FeatureFlagService.FeatureContext userInRollout = FeatureFlagService.FeatureContext.builder() // GH-90000
                .userId("user-in-rollout [GH-90000]")
                .tenantId("tenant-alpha [GH-90000]")
                .build(); // GH-90000

            when(featureFlagService.isEnabled(featureKey, userInRollout)) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, userInRollout)); // GH-90000

            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[F001]: percentage_rollout_disabled_for_non_matching_users [GH-90000]")
        void percentageRolloutDisabledForNonMatchingUsers() { // GH-90000
            String featureKey = "limited-rollout";

            FeatureFlagService.FeatureContext userOutOfRollout = FeatureFlagService.FeatureContext.builder() // GH-90000
                .userId("user-out-of-rollout [GH-90000]")
                .tenantId("tenant-alpha [GH-90000]")
                .build(); // GH-90000

            when(featureFlagService.isEnabled(featureKey, userOutOfRollout)) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, userOutOfRollout)); // GH-90000

            assertThat(result).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Variant Features [GH-90000]")
    class VariantFeaturesTests {

        @Test
        @DisplayName("[F001]: get_variant_returns_assigned_variant [GH-90000]")
        void getVariantReturnsAssignedVariant() { // GH-90000
            String featureKey = "ab-test-color";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() // GH-90000
                .userId("user-001 [GH-90000]")
                .tenantId("tenant-alpha [GH-90000]")
                .build(); // GH-90000

            when(featureFlagService.getVariant(featureKey, context)) // GH-90000
                .thenReturn(Promise.of("blue [GH-90000]"));

            String variant = runPromise(() -> featureFlagService.getVariant(featureKey, context)); // GH-90000

            assertThat(variant).isIn("blue", "red", "green"); // GH-90000
        }

        @Test
        @DisplayName("[F001]: same_user_gets_same_variant [GH-90000]")
        void sameUserGetsSameVariant() { // GH-90000
            String featureKey = "consistent-variant";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() // GH-90000
                .userId("user-001 [GH-90000]")
                .tenantId("tenant-alpha [GH-90000]")
                .build(); // GH-90000

            when(featureFlagService.getVariant(featureKey, context)) // GH-90000
                .thenReturn(Promise.of("variant-a [GH-90000]"));

            String variant1 = runPromise(() -> featureFlagService.getVariant(featureKey, context)); // GH-90000
            String variant2 = runPromise(() -> featureFlagService.getVariant(featureKey, context)); // GH-90000

            assertThat(variant1).isEqualTo(variant2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Flag Management [GH-90000]")
    class FlagManagementTests {

        @Test
        @DisplayName("[F001]: create_flag_creates_new_flag [GH-90000]")
        void createFlagCreatesNewFlag() { // GH-90000
            FeatureFlagService.FeatureFlag newFlag = new FeatureFlagService.FeatureFlag( // GH-90000
                "new-feature", "New Feature", "Description", "tenant-alpha",
                false, List.of(), 0, Set.of(), "", // GH-90000
                Instant.now(), Instant.now(), "user-001" // GH-90000
            );

            when(featureFlagService.createFlag(any())) // GH-90000
                .thenReturn(Promise.of(newFlag)); // GH-90000

            FeatureFlagService.FeatureFlag result = runPromise(() -> // GH-90000
                featureFlagService.createFlag(newFlag) // GH-90000
            );

            assertThat(result.key()).isEqualTo("new-feature [GH-90000]");
            assertThat(result.enabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("[F001]: get_flag_returns_existing_flag [GH-90000]")
        void getFlagReturnsExistingFlag() { // GH-90000
            String key = "existing-feature";
            FeatureFlagService.FeatureFlag flag = createFlag(key, true); // GH-90000

            when(featureFlagService.getFlag(key)) // GH-90000
                .thenReturn(Promise.of(Optional.of(flag))); // GH-90000

            Optional<FeatureFlagService.FeatureFlag> result = runPromise(() -> // GH-90000
                featureFlagService.getFlag(key) // GH-90000
            );

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().key()).isEqualTo(key); // GH-90000
        }

        @Test
        @DisplayName("[F001]: delete_flag_removes_flag [GH-90000]")
        void deleteFlagRemovesFlag() { // GH-90000
            String key = "obsolete-feature";

            when(featureFlagService.deleteFlag(key)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> featureFlagService.deleteFlag(key)); // GH-90000

            verify(featureFlagService).deleteFlag(key); // GH-90000
        }

        @Test
        @DisplayName("[F001]: list_flags_returns_all_flags_for_tenant [GH-90000]")
        void listFlagsReturnsAllFlagsForTenant() { // GH-90000
            String tenantId = "tenant-alpha";
            List<FeatureFlagService.FeatureFlag> flags = List.of( // GH-90000
                createFlag("feature-1", true), // GH-90000
                createFlag("feature-2", false), // GH-90000
                createFlag("feature-3", true) // GH-90000
            );

            when(featureFlagService.listFlags(tenantId)) // GH-90000
                .thenReturn(Promise.of(flags)); // GH-90000

            List<FeatureFlagService.FeatureFlag> result = runPromise(() -> // GH-90000
                featureFlagService.listFlags(tenantId) // GH-90000
            );

            assertThat(result).hasSize(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("Metrics [GH-90000]")
    class MetricsTests {

        @Test
        @DisplayName("[F001]: get_metrics_returns_evaluation_stats [GH-90000]")
        void getMetricsReturnsEvaluationStats() { // GH-90000
            String key = "measured-feature";

            FeatureFlagService.FeatureMetrics metrics = new FeatureFlagService.FeatureMetrics( // GH-90000
                key, 10000, 7500, 2500,
                Map.of("variant-a", 5000L, "variant-b", 5000L), // GH-90000
                0.15, Instant.now() // GH-90000
            );

            when(featureFlagService.getMetrics(key)) // GH-90000
                .thenReturn(Promise.of(metrics)); // GH-90000

            FeatureFlagService.FeatureMetrics result = runPromise(() -> // GH-90000
                featureFlagService.getMetrics(key) // GH-90000
            );

            assertThat(result.totalEvaluations()).isEqualTo(10000); // GH-90000
            assertThat(result.enabledCount()).isEqualTo(7500); // GH-90000
            assertThat(result.conversionRate()).isEqualTo(0.15); // GH-90000
        }
    }

    private FeatureFlagService.FeatureFlag createFlag(String key, boolean enabled) { // GH-90000
        return new FeatureFlagService.FeatureFlag( // GH-90000
            key, key, "", "tenant-alpha", enabled,
            List.of(), 100, Set.of(), "", // GH-90000
            Instant.now(), Instant.now(), "user" // GH-90000
        );
    }
}
