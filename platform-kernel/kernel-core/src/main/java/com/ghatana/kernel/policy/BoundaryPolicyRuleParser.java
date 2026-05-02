package com.ghatana.kernel.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parses and assembles {@link BoundaryPolicyRule} instances from structured configuration.
 *
 * <p>The parser provides a fluent API to compose rule sets programmatically (for tests
 * and product packs) and acts as the entry point for format-specific subclasses or
 * factory methods that parse rules from YAML or JSON policy pack files.</p>
 *
 * <p>All parsed rules are validated via {@link BoundaryPolicyRuleValidator} before
 * being returned. Invalid rule definitions cause startup to fail with an actionable
 * error message.</p>
 *
 * @doc.type class
 * @doc.purpose Parser and assembler for BoundaryPolicyRule lists
 * @doc.layer core
 * @doc.pattern Builder, Factory
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public final class BoundaryPolicyRuleParser {

    private final List<BoundaryPolicyRule> rules = new ArrayList<>();

    private BoundaryPolicyRuleParser() {}

    /** Creates a new, empty parser. */
    public static BoundaryPolicyRuleParser create() {
        return new BoundaryPolicyRuleParser();
    }

    /**
     * Adds a pre-built rule.
     *
     * @param rule the rule to add; must not be null
     * @return this parser for chaining
     */
    public BoundaryPolicyRuleParser add(BoundaryPolicyRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule cannot be null"));
        return this;
    }

    /**
     * Adds all rules from another list.
     *
     * @param additionalRules rules to add; must not be null
     * @return this parser for chaining
     */
    public BoundaryPolicyRuleParser addAll(List<BoundaryPolicyRule> additionalRules) {
        Objects.requireNonNull(additionalRules, "additionalRules cannot be null");
        rules.addAll(additionalRules);
        return this;
    }

    /**
     * Builds and validates the rule list.
     *
     * @return immutable, validated list of rules
     * @throws BoundaryPolicyStore.BoundaryPolicyStoreException if validation fails
     */
    public List<BoundaryPolicyRule> buildAndValidate() {
        List<BoundaryPolicyRule> result = Collections.unmodifiableList(new ArrayList<>(rules));
        BoundaryPolicyRuleValidator.validate(result);
        return result;
    }

    /**
     * Builds the rule list without validation. Use only in tests or when the caller
     * performs validation separately.
     */
    public List<BoundaryPolicyRule> buildRaw() {
        return Collections.unmodifiableList(new ArrayList<>(rules));
    }
}
