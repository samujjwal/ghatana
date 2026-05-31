package com.ghatana.kernel.policy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Product-agnostic policy dispatch plugin.
 *
 * <p>The Kernel owns deterministic policy registration and fail-closed dispatch.
 * Products provide domain-specific decision providers for their own policy IDs
 * and decision/result types.</p>
 *
 * @param <C> product policy evaluation context
 * @param <D> product policy decision type
 * @doc.type class
 * @doc.purpose Kernel-owned policy registry and dispatcher for product-supplied decision providers
 * @doc.layer core
 * @doc.pattern Plugin, Registry
 */
public final class KernelPolicyPlugin<C, D> {

    private final Map<String, Function<C, D>> providers;
    private final BiFunction<String, C, D> unknownPolicyProvider;

    private KernelPolicyPlugin(Builder<C, D> builder) {
        this.providers = Map.copyOf(builder.providers);
        this.unknownPolicyProvider = Objects.requireNonNull(
            builder.unknownPolicyProvider,
            "unknownPolicyProvider cannot be null"
        );
    }

    public static <C, D> Builder<C, D> builder() {
        return new Builder<>();
    }

    public D evaluate(String policyId, C context) {
        if (policyId == null || policyId.isBlank()) {
            return unknownPolicyProvider.apply(policyId, context);
        }

        Function<C, D> provider = providers.get(policyId);
        if (provider == null) {
            return unknownPolicyProvider.apply(policyId, context);
        }

        return provider.apply(context);
    }

    public boolean hasProvider(String policyId) {
        return policyId != null && providers.containsKey(policyId);
    }

    public Map<String, Function<C, D>> providers() {
        return providers;
    }

    /**
     * Builder for policy plugin dispatch tables.
     *
     * @param <C> product policy evaluation context
     * @param <D> product policy decision type
     * @doc.type class
     * @doc.purpose Builds immutable Kernel policy dispatch tables
     * @doc.layer core
     * @doc.pattern Builder
     */
    public static final class Builder<C, D> {
        private final Map<String, Function<C, D>> providers = new LinkedHashMap<>();
        private BiFunction<String, C, D> unknownPolicyProvider;

        private Builder() {
        }

        public Builder<C, D> register(String policyId, Function<C, D> provider) {
            String normalizedPolicyId = Objects.requireNonNull(policyId, "policyId cannot be null").strip();
            if (normalizedPolicyId.isEmpty()) {
                throw new IllegalArgumentException("policyId cannot be blank");
            }
            Objects.requireNonNull(provider, "provider cannot be null");
            if (providers.putIfAbsent(normalizedPolicyId, provider) != null) {
                throw new IllegalArgumentException("Duplicate policy provider: " + normalizedPolicyId);
            }
            return this;
        }

        public Builder<C, D> registerAll(Iterable<String> policyIds, Function<C, D> provider) {
            Objects.requireNonNull(policyIds, "policyIds cannot be null");
            for (String policyId : policyIds) {
                register(policyId, provider);
            }
            return this;
        }

        public Builder<C, D> unknownPolicyProvider(BiFunction<String, C, D> provider) {
            this.unknownPolicyProvider = Objects.requireNonNull(provider, "provider cannot be null");
            return this;
        }

        public KernelPolicyPlugin<C, D> build() {
            if (providers.isEmpty()) {
                throw new IllegalStateException("Kernel policy plugin requires at least one provider");
            }
            return new KernelPolicyPlugin<>(this);
        }
    }
}
