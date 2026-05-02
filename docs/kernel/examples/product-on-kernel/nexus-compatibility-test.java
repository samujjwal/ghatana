/*
 * Copyright (c) 2026 Ghatana Inc.
 * Example file — fictional Nexus logistics product.
 * This file is NOT production code. It demonstrates the policy compatibility
 * test pattern from kernel-dev.md item 29.
 */
package com.ghatana.nexus.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyResolver;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyRule.Effect;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.policy.DefaultBoundaryPolicyResolver;
import com.ghatana.kernel.policy.SensitivityLevel;
import com.ghatana.kernel.scope.ScopeDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example compatibility test: Nexus logistics policy pack composing with the generic
 * {@link DefaultBoundaryPolicyResolver} without any kernel modification.
 *
 * <p>This is a template that any new product team can copy and adapt. Replace:</p>
 * <ul>
 *   <li>{@code NexusBoundaryPolicyStore} with your own store class.</li>
 *   <li>{@code "nexus.*"} scope patterns with your product's scopes.</li>
 *   <li>{@code "shipments/**"} resource patterns with your product's resources.</li>
 *   <li>Assertions with your product's expected access posture.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Example: product policy compatibility test template
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Nexus Policy Compatibility Test (example / item-29 template)")
class NexusPolicyCompatibilityTest {

    private DefaultBoundaryPolicyResolver resolver;

    private final ScopeDescriptor nexusCarrierOps = ScopeDescriptor.domainPack("nexus.carrier-ops");
    private final ScopeDescriptor nexusCarrierMgmt = ScopeDescriptor.domainPack("nexus.carrier-management");
    private final ScopeDescriptor externalScope = ScopeDescriptor.product("external-party");

    private final ClassificationDescriptor internal = ClassificationDescriptor.of(
            "logistics", SensitivityLevel.INTERNAL);

    @BeforeEach
    void setUp() {
        NexusBoundaryPolicyStore store = new NexusBoundaryPolicyStore();
        BoundaryPolicyLoadContext ctx = BoundaryPolicyLoadContext.of("nexus-tenant", "region-global");
        resolver = new DefaultBoundaryPolicyResolver(store, ctx);
    }

    @Test
    @DisplayName("Nexus policy store loads non-empty, well-formed rules")
    void storeLoadsNonEmptyRules() {
        NexusBoundaryPolicyStore store = new NexusBoundaryPolicyStore();
        List<BoundaryPolicyRule> rules = store.loadRules(BoundaryPolicyLoadContext.of("t", "r"));

        assertThat(rules).isNotEmpty();
        for (BoundaryPolicyRule rule : rules) {
            assertThat(rule.getRuleId()).isNotBlank();
            assertThat(rule.getActions()).isNotEmpty();
            assertThat(rule.getEffect()).isNotNull();
        }
        // Default-deny must be last
        assertThat(rules.get(rules.size() - 1).getEffect()).isEqualTo(Effect.DENY);
    }

    @Test
    @DisplayName("Nexus store wires into DefaultBoundaryPolicyResolver without kernel modification")
    void nexusStoreComposesWithGenericResolver() {
        assertThat(resolver).isNotNull();
    }

    @Test
    @DisplayName("Nexus internal scope reads shipments: allowed")
    void internalShipmentReadAllowed() {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
                nexusCarrierOps,
                nexusCarrierOps,
                "shipments/AWB-001",
                "read",
                internal
        );
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    @DisplayName("External scope reads Nexus shipments: denied by default-deny")
    void externalShipmentReadDenied() {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
                externalScope,
                nexusCarrierOps,
                "shipments/AWB-001",
                "read",
                internal
        );
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    @DisplayName("DefaultBoundaryPolicyResolver is unaware of Nexus as a concept")
    void resolverHasNoNexusReference() {
        assertThat(DefaultBoundaryPolicyResolver.class.getName()).doesNotContain("nexus", "Nexus");
    }
}
