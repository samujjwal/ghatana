/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultScalingPolicyManager}.
 *
 * @doc.type class
 * @doc.purpose Verify policy CRUD, filtering by cluster and enabled state
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DefaultScalingPolicyManager")
class DefaultScalingPolicyManagerTest {

    private DefaultScalingPolicyManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultScalingPolicyManager();
    }

    @Test
    @DisplayName("addPolicy stores policy; getPolicy returns it by ID")
    void addPolicy_storesAndRetrievesById() {
        ScalingPolicy policy = policy("p1", true, null);
        manager.addPolicy(policy);

        assertThat(manager.getPolicy("p1")).isEqualTo(policy);
    }

    @Test
    @DisplayName("addPolicy with null throws IllegalArgumentException")
    void addPolicy_nullThrows() {
        assertThatThrownBy(() -> manager.addPolicy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addPolicy with null policyId throws IllegalArgumentException")
    void addPolicy_nullPolicyIdThrows() {
        ScalingPolicy bad = ScalingPolicy.builder().build(); // policyId is null
        assertThatThrownBy(() -> manager.addPolicy(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("removePolicy removes the policy; getPolicy returns null afterwards")
    void removePolicy_removesExistingPolicy() {
        manager.addPolicy(policy("p2", true, null));
        manager.removePolicy("p2");

        assertThat(manager.getPolicy("p2")).isNull();
    }

    @Test
    @DisplayName("removePolicy on nonexistent ID is a no-op (does not throw)")
    void removePolicy_nonexistentIsNoOp() {
        manager.removePolicy("does-not-exist"); // must not throw
        assertThat(manager.getAllPolicies()).isEmpty();
    }

    @Test
    @DisplayName("getApplicablePolicies returns only enabled policies")
    void getApplicablePolicies_filtersDisabledPolicies() {
        manager.addPolicy(policy("enabled", true, null));
        manager.addPolicy(policy("disabled", false, null));

        List<ScalingPolicy> applicable = manager.getApplicablePolicies("cluster-1");

        assertThat(applicable).extracting(ScalingPolicy::getPolicyId).containsExactly("enabled");
    }

    @Test
    @DisplayName("getApplicablePolicies filters by cluster ID when applicableClusters is set")
    void getApplicablePolicies_filtersByClusterId() {
        manager.addPolicy(policy("p-for-cluster-a", true, List.of("cluster-a")));
        manager.addPolicy(policy("p-global", true, null)); // null = applies to all

        List<ScalingPolicy> forB = manager.getApplicablePolicies("cluster-b");

        assertThat(forB).extracting(ScalingPolicy::getPolicyId).containsExactly("p-global");
    }

    @Test
    @DisplayName("getAllPolicies returns all added policies regardless of enabled state")
    void getAllPolicies_returnsAll() {
        manager.addPolicy(policy("p1", true, null));
        manager.addPolicy(policy("p2", false, null));

        assertThat(manager.getAllPolicies()).hasSize(2);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static ScalingPolicy policy(String id, boolean enabled, List<String> clusters) {
        return ScalingPolicy.builder()
                .policyId(id)
                .name(id + "-name")
                .enabled(enabled)
                .scaleUpThreshold(0.80)
                .scaleDownThreshold(0.20)
                .scaleUpStep(2)
                .scaleDownStep(1)
                .minNodes(1)
                .applicableClusters(clusters)
                .build();
    }
}
