/**
 * @ghatana/product-conformance — Kernel-owned product conformance validation tools
 *
 * Provides schema-based, AST-based, and runtime conformance validation for products.
 * Replaces token-scanning conformance gates with structured validation.
 *
 * @doc.type module
 * @doc.purpose Product conformance validation tools
 * @doc.layer platform
 */

export * from './schema/index.js';
export * from './ast/index.js';
export * from './runtime/index.js';
