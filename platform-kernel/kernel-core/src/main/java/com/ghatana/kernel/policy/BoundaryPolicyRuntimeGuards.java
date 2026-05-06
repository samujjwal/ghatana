package com.ghatana.kernel.policy;

import com.ghatana.kernel.context.KernelContext;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime guardrails for boundary-policy wiring.
 *
 * <p>Product launchers should invoke this guard after module initialization and
 * before accepting traffic so development-only stores fail closed in production-like
 * environments.</p>
 *
 * @doc.type class
 * @doc.purpose Protect product runtimes from wiring development-only boundary policy stores in production-like environments
 * @doc.layer core
 * @doc.pattern Guard
 */
public final class BoundaryPolicyRuntimeGuards {

    private static final Set<String> NON_PRODUCTION_ENVIRONMENTS = Set.of(
            "local",
            "dev",
            "development",
            "test",
            "ci",
            "sandbox"
    );

    private BoundaryPolicyRuntimeGuards() {
    }

    public static void assertStoreAllowed(KernelContext context, String compositionRoot) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(compositionRoot, "compositionRoot cannot be null");

        context.getOptionalDependency(BoundaryPolicyStore.class)
                .ifPresent(store -> assertStoreAllowed(
                        context.getEnvironment(),
                        store,
                        compositionRoot
                ));
    }

    public static void assertStoreAllowed(
            String environment,
            BoundaryPolicyStore store,
            String compositionRoot
    ) {
        Objects.requireNonNull(environment, "environment cannot be null");
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(compositionRoot, "compositionRoot cannot be null");

        if (!(store instanceof InMemoryBoundaryPolicyStore) || isNonProduction(environment)) {
            return;
        }

        throw new IllegalStateException(
                "InMemoryBoundaryPolicyStore is limited to local/dev/test runtime wiring and must not be used in "
                        + "environment '" + environment + "' from " + compositionRoot
                        + ". Wire a product-owned BoundaryPolicyStore implementation instead."
        );
    }

    static boolean isNonProduction(String environment) {
        return NON_PRODUCTION_ENVIRONMENTS.contains(
                environment.trim().toLowerCase(Locale.ROOT)
        );
    }
}
