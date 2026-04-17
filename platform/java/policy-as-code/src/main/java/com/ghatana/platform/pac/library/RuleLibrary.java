package com.ghatana.platform.pac.library;

import com.ghatana.platform.pac.InMemoryPolicyEngine;

/**
 * A named, versioned collection of {@link InMemoryPolicyEngine} policy rules.
 *
 * <p>Rule libraries provide pre-packaged, regulation-specific rule sets that can
 * be loaded into an {@link InMemoryPolicyEngine} for development, testing, or
 * in environments where an external OPA endpoint is not available.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * InMemoryPolicyEngine engine = new InMemoryPolicyEngine();
 * new NepalHealthcareRuleLibrary().registerInto(engine);
 * new FinanceSoxRuleLibrary().registerInto(engine);
 *
 * PolicyEvalResult result = runPromise(
 *     () -> engine.evaluate("tenant-nep", "hipaa_data_access", Map.of("role", "NURSE")));
 * }</pre>
 *
 * <p>Libraries may be layered: call {@link #registerInto} on multiple libraries in order
 * of precedence; later registrations override earlier ones for the same policy name.</p>
 *
 * @doc.type interface
 * @doc.purpose Typed, versioned regulated-rule-set SPI
 * @doc.layer platform
 * @doc.pattern RuleLibrary
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public interface RuleLibrary {

    /**
     * Returns the unique identifier of this rule library (e.g. {@code "nepal-healthcare-2081"}).
     *
     * @return library identifier; never {@code null} or blank
     */
    String id();

    /**
     * Returns the semantic version of the rule set.
     *
     * @return semver string; never {@code null} or blank
     */
    String version();

    /**
     * Returns a human-readable description of the regulation this library implements.
     *
     * @return description; never {@code null}
     */
    String description();

    /**
     * Registers all rules this library owns into the supplied engine.
     *
     * <p>If any policy name was previously registered in {@code engine}, this call
     * replaces it (last-writer semantics, supporting overlay / override).</p>
     *
     * @param engine the target engine to register rules into; must not be {@code null}
     */
    void registerInto(InMemoryPolicyEngine engine);
}
