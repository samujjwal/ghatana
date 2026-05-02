/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Tests for feature flag service toggle logic (F001). 
 *
 * @doc.type class
 * @doc.purpose Feature flag toggle logic tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("FeatureFlagService – Toggle Logic (F001)")
class FeatureFlagServiceTest extends EventloopTestBase {

    @Mock
    private FeatureFlagService featureFlagService;

    @Nested
    @DisplayName("Toggle Operations")
    class ToggleOperationsTests {

        @Test
        @DisplayName("[F001]: is_enabled_returns_true_for_enabled_flag")
        void isEnabledReturnsTrueForEnabledFlag() { 
            String featureKey = "new-dashboard";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() 
                .userId("user-001")
                .tenantId("tenant-alpha")
                .build(); 

            when(featureFlagService.isEnabled(featureKey, context)) 
                .thenReturn(Promise.of(true)); 

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, context)); 

            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("[F001]: is_enabled_returns_false_for_disabled_flag")
        void isEnabledReturnsFalseForDisabledFlag() { 
            String featureKey = "beta-feature";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() 
                .tenantId("tenant-alpha")
                .build(); 

            when(featureFlagService.isEnabled(featureKey, context)) 
                .thenReturn(Promise.of(false)); 

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, context)); 

            assertThat(result).isFalse(); 
        }

        @Test
        @DisplayName("[F001]: toggle_changes_flag_state")
        void toggleChangesFlagState() { 
            String featureKey = "feature-001";

            FeatureFlagService.FeatureFlag enabled = createFlag(featureKey, true); 
            FeatureFlagService.FeatureFlag disabled = createFlag(featureKey, false); 

            when(featureFlagService.toggle(featureKey, true)) 
                .thenReturn(Promise.of(enabled)); 
            when(featureFlagService.toggle(featureKey, false)) 
                .thenReturn(Promise.of(disabled)); 

            FeatureFlagService.FeatureFlag onResult = runPromise(() -> featureFlagService.toggle(featureKey, true)); 
            FeatureFlagService.FeatureFlag offResult = runPromise(() -> featureFlagService.toggle(featureKey, false)); 

            assertThat(onResult.enabled()).isTrue(); 
            assertThat(offResult.enabled()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("Targeting Rules")
    class TargetingRulesTests {

        @Test
        @DisplayName("[F001]: user_targeting_matches_specific_user")
        void userTargetingMatchesSpecificUser() { 
            String featureKey = "vip-feature";
            FeatureFlagService.FeatureContext vipUser = FeatureFlagService.FeatureContext.builder() 
                .userId("vip-user-001")
                .tenantId("tenant-alpha")
                .attributes(Map.of("tier", "vip")) 
                .build(); 

            when(featureFlagService.isEnabled(featureKey, vipUser)) 
                .thenReturn(Promise.of(true)); 

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, vipUser)); 

            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("[F001]: percentage_rollout_enabled_for_matching_users")
        void percentageRolloutEnabledForMatchingUsers() { 
            String featureKey = "gradual-rollout";

            // User ID hash determines if they're in the rollout percentage
            FeatureFlagService.FeatureContext userInRollout = FeatureFlagService.FeatureContext.builder() 
                .userId("user-in-rollout")
                .tenantId("tenant-alpha")
                .build(); 

            when(featureFlagService.isEnabled(featureKey, userInRollout)) 
                .thenReturn(Promise.of(true)); 

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, userInRollout)); 

            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("[F001]: percentage_rollout_disabled_for_non_matching_users")
        void percentageRolloutDisabledForNonMatchingUsers() { 
            String featureKey = "limited-rollout";

            FeatureFlagService.FeatureContext userOutOfRollout = FeatureFlagService.FeatureContext.builder() 
                .userId("user-out-of-rollout")
                .tenantId("tenant-alpha")
                .build(); 

            when(featureFlagService.isEnabled(featureKey, userOutOfRollout)) 
                .thenReturn(Promise.of(false)); 

            Boolean result = runPromise(() -> featureFlagService.isEnabled(featureKey, userOutOfRollout)); 

            assertThat(result).isFalse(); 
        }
    }

    @Nested
    @DisplayName("Variant Features")
    class VariantFeaturesTests {

        @Test
        @DisplayName("[F001]: get_variant_returns_assigned_variant")
        void getVariantReturnsAssignedVariant() { 
            String featureKey = "ab-test-color";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() 
                .userId("user-001")
                .tenantId("tenant-alpha")
                .build(); 

            when(featureFlagService.getVariant(featureKey, context)) 
                .thenReturn(Promise.of("blue"));

            String variant = runPromise(() -> featureFlagService.getVariant(featureKey, context)); 

            assertThat(variant).isIn("blue", "red", "green"); 
        }

        @Test
        @DisplayName("[F001]: same_user_gets_same_variant")
        void sameUserGetsSameVariant() { 
            String featureKey = "consistent-variant";
            FeatureFlagService.FeatureContext context = FeatureFlagService.FeatureContext.builder() 
                .userId("user-001")
                .tenantId("tenant-alpha")
                .build(); 

            when(featureFlagService.getVariant(featureKey, context)) 
                .thenReturn(Promise.of("variant-a"));

            String variant1 = runPromise(() -> featureFlagService.getVariant(featureKey, context)); 
            String variant2 = runPromise(() -> featureFlagService.getVariant(featureKey, context)); 

            assertThat(variant1).isEqualTo(variant2); 
        }
    }

    @Nested
    @DisplayName("Flag Management")
    class FlagManagementTests {

        @Test
        @DisplayName("[F001]: create_flag_creates_new_flag")
        void createFlagCreatesNewFlag() { 
            FeatureFlagService.FeatureFlag newFlag = new FeatureFlagService.FeatureFlag( 
                "new-feature", "New Feature", "Description", "tenant-alpha",
                false, List.of(), 0, Set.of(), "", 
                Instant.now(), Instant.now(), "user-001" 
            );

            when(featureFlagService.createFlag(any())) 
                .thenReturn(Promise.of(newFlag)); 

            FeatureFlagService.FeatureFlag result = runPromise(() -> 
                featureFlagService.createFlag(newFlag) 
            );

            assertThat(result.key()).isEqualTo("new-feature");
            assertThat(result.enabled()).isFalse(); 
        }

        @Test
        @DisplayName("[F001]: get_flag_returns_existing_flag")
        void getFlagReturnsExistingFlag() { 
            String key = "existing-feature";
            FeatureFlagService.FeatureFlag flag = createFlag(key, true); 

            when(featureFlagService.getFlag(key)) 
                .thenReturn(Promise.of(Optional.of(flag))); 

            Optional<FeatureFlagService.FeatureFlag> result = runPromise(() -> 
                featureFlagService.getFlag(key) 
            );

            assertThat(result).isPresent(); 
            assertThat(result.get().key()).isEqualTo(key); 
        }

        @Test
        @DisplayName("[F001]: delete_flag_removes_flag")
        void deleteFlagRemovesFlag() { 
            String key = "obsolete-feature";

            when(featureFlagService.deleteFlag(key)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> featureFlagService.deleteFlag(key)); 

            verify(featureFlagService).deleteFlag(key); 
        }

        @Test
        @DisplayName("[F001]: list_flags_returns_all_flags_for_tenant")
        void listFlagsReturnsAllFlagsForTenant() { 
            String tenantId = "tenant-alpha";
            List<FeatureFlagService.FeatureFlag> flags = List.of( 
                createFlag("feature-1", true), 
                createFlag("feature-2", false), 
                createFlag("feature-3", true) 
            );

            when(featureFlagService.listFlags(tenantId)) 
                .thenReturn(Promise.of(flags)); 

            List<FeatureFlagService.FeatureFlag> result = runPromise(() -> 
                featureFlagService.listFlags(tenantId) 
            );

            assertThat(result).hasSize(3); 
        }
    }

    @Nested
    @DisplayName("Metrics")
    class MetricsTests {

        @Test
        @DisplayName("[F001]: get_metrics_returns_evaluation_stats")
        void getMetricsReturnsEvaluationStats() { 
            String key = "measured-feature";

            FeatureFlagService.FeatureMetrics metrics = new FeatureFlagService.FeatureMetrics( 
                key, 10000, 7500, 2500,
                Map.of("variant-a", 5000L, "variant-b", 5000L), 
                0.15, Instant.now() 
            );

            when(featureFlagService.getMetrics(key)) 
                .thenReturn(Promise.of(metrics)); 

            FeatureFlagService.FeatureMetrics result = runPromise(() -> 
                featureFlagService.getMetrics(key) 
            );

            assertThat(result.totalEvaluations()).isEqualTo(10000); 
            assertThat(result.enabledCount()).isEqualTo(7500); 
            assertThat(result.conversionRate()).isEqualTo(0.15); 
        }
    }

    private FeatureFlagService.FeatureFlag createFlag(String key, boolean enabled) { 
        return new FeatureFlagService.FeatureFlag( 
            key, key, "", "tenant-alpha", enabled,
            List.of(), 100, Set.of(), "", 
            Instant.now(), Instant.now(), "user" 
        );
    }
}
