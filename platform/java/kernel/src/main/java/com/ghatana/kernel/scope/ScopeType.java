package com.ghatana.kernel.scope;

/**
 * Canonical scope types for the kernel runtime.
 *
 * <p>Replaces the product-first framing with a scope-first model. Rather than assuming
 * "product" is the universal top-level entity, the kernel uses scopes to describe
 * boundaries at any architectural tier. A "product" is one valid scope type among many.</p>
 *
 * <p>Per KERNEL_CANONICALIZATION_DECISIONS.md §4.1, these are the canonical scope types.</p>
 *
 * @doc.type enum
 * @doc.purpose Canonical scope types for scope-first kernel framing
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public enum ScopeType {

    /** The kernel runtime itself. */
    KERNEL,

    /** The operational platform layer (AppPlatform). */
    PLATFORM,

    /** A domain pack providing domain-specific capabilities. */
    DOMAIN_PACK,

    /** A product built on top of domain packs and platform. */
    PRODUCT,

    /** A tenant within a product or platform deployment. */
    TENANT,

    /** An operator or admin context. */
    OPERATOR,

    /** An autonomous agent context. */
    AGENT,

    /** A workflow execution context. */
    WORKFLOW
}
