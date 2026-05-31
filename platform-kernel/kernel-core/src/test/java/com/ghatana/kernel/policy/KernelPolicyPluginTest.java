package com.ghatana.kernel.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KernelPolicyPlugin")
@Tag("purity-validation")
class KernelPolicyPluginTest {

    @Test
    @DisplayName("dispatches registered policy providers")
    void dispatchesRegisteredPolicyProviders() {
        KernelPolicyPlugin<String, String> plugin = KernelPolicyPlugin.<String, String>builder()
            .register("product.policy.read", context -> "allow:" + context)
            .unknownPolicyProvider((policyId, context) -> "deny:" + policyId)
            .build();

        assertThat(plugin.evaluate("product.policy.read", "ctx")).isEqualTo("allow:ctx");
        assertThat(plugin.hasProvider("product.policy.read")).isTrue();
    }

    @Test
    @DisplayName("fails closed for missing or blank policy ids")
    void failsClosedForMissingPolicyIds() {
        KernelPolicyPlugin<String, String> plugin = KernelPolicyPlugin.<String, String>builder()
            .register("product.policy.read", context -> "allow")
            .unknownPolicyProvider((policyId, context) -> "deny")
            .build();

        assertThat(plugin.evaluate("product.policy.missing", "ctx")).isEqualTo("deny");
        assertThat(plugin.evaluate(" ", "ctx")).isEqualTo("deny");
        assertThat(plugin.evaluate(null, "ctx")).isEqualTo("deny");
    }

    @Test
    @DisplayName("rejects duplicate providers at registration time")
    void rejectsDuplicateProviders() {
        KernelPolicyPlugin.Builder<String, String> builder = KernelPolicyPlugin.<String, String>builder()
            .register("product.policy.read", context -> "allow")
            .unknownPolicyProvider((policyId, context) -> "deny");

        assertThatThrownBy(() -> builder.register("product.policy.read", context -> "allow-again"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate policy provider");
    }
}
