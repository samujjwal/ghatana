/**
 * @fileoverview @yappc/artifact-compiler - Bidirectional artifact-to-model pipeline.
 *
 * @doc.type package
 * @doc.purpose Provides ArtifactGraph indexing, SemanticProductModel synthesis,
 *   language-specific extractors, residual island management, and round-trip
 *   merge engine for decompiling arbitrary codebases into governed product models.
 * @doc.layer product
 * @doc.subpaths inventory, graph, model, provenance, residual, extractors, synthesis, merge
 */

export * from "./inventory/index";
export * from "./graph/index";
export * from "./model/index";
export * from "./provenance/index";
export * from "./residual/index";
export * from "./extractors/index";
export * from "./synthesis/index";
export * from "./merge/index";
export * from "./builder/index";
