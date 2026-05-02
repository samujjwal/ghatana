package com.ghatana.kernel.policy;

import java.util.List;

/**
 * SPI for loading {@link BoundaryPolicyRule} instances used by {@link BoundaryPolicyResolver}.
 *
 * <p>Implementations may load rules from in-memory configuration, YAML/JSON files,
 * a database, a remote policy service, or product-supplied packs. The store must
 * fail startup validation via {@link BoundaryPolicyRuleValidator} when the returned
 * rule list contains malformed or conflicting entries.</p>
 *
 * <p>Stores must be thread-safe. Rule loading is expected to be infrequent (at startup
 * or on explicit refresh) and may be cached by the resolver.</p>
 *
 * @doc.type interface
 * @doc.purpose SPI for supplying boundary policy rules to the resolver
 * @doc.layer core
 * @doc.pattern Repository
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public interface BoundaryPolicyStore {

    /**
     * Loads the applicable set of {@link BoundaryPolicyRule} instances for the given context.
     *
     * <p>The returned list may be filtered, ordered, or merged based on the load context
     * (e.g., tenant overrides, region-specific rules). The resolver evaluates rules in
     * list order; first matching rule with a non-ALLOW effect terminates evaluation.</p>
     *
     * @param context the load context (tenant, region, attributes)
     * @return ordered, immutable list of rules; never null, may be empty
     * @throws BoundaryPolicyStoreException if rules cannot be loaded or fail validation
     */
    List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context);

    /**
     * Exception thrown when rule loading or validation fails.
     */
    final class BoundaryPolicyStoreException extends RuntimeException {
        public BoundaryPolicyStoreException(String message) {
            super(message);
        }

        public BoundaryPolicyStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
