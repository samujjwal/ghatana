/**
 * @fileoverview Projection: LogicalArtifactModel → DS Generator config.
 *
 * Projects design-relevant aspects of a LogicalArtifactModel into a
 * design-system configuration object that can be fed to @ghatana/ds-generator.
 *
 * Currently extracts:
 * - Component names that use the design system (for contract registry)
 * - Inferred prop types that map to design tokens
 * - Token usage patterns from prop names
 *
 * @doc.type module
 * @doc.purpose Project LogicalArtifactModel into DS Generator config shape
 * @doc.layer platform
 * @doc.pattern Adapter
 */

import type { ArtifactNode, LogicalArtifactModel } from "@ghatana/artifact-contracts";
import type { FidelityReport, LossPoint } from "@ghatana/artifact-contracts";
import { computeFidelityReport, createPerfectFidelityReport } from "@ghatana/artifact-contracts";

// ============================================================================
// PROJECTED TYPES
// ============================================================================

/**
 * A projected component contract for the DS registry.
 */
export interface ProjectedDsComponentContract {
  /** Component name (PascalCase). */
  readonly name: string;
  /** Inferred props with design-token-mapped names. */
  readonly tokenProps: ReadonlyArray<{
    readonly propName: string;
    readonly inferredTokenCategory: DsTokenCategory;
  }>;
  /** Source file that defines this component. */
  readonly sourceRef?: {
    readonly relativePath: string;
  };
}

/**
 * Category of a design token inferred from prop names.
 */
export type DsTokenCategory =
  | "color"
  | "spacing"
  | "typography"
  | "shadow"
  | "motion"
  | "zIndex"
  | "unknown";

/**
 * Projected DS configuration extracted from the model.
 */
export interface ProjectedDsConfig {
  /** Components that interact with the design system. */
  readonly components: readonly ProjectedDsComponentContract[];
  /**
   * Aggregated token categories actually used by the projected components.
   * Useful for tree-shaking token outputs.
   */
  readonly usedTokenCategories: ReadonlySet<DsTokenCategory>;
  /** Source model ID. */
  readonly sourceModelId: string;
}

// ============================================================================
// RESULT
// ============================================================================

export interface ProjectDsResult {
  readonly config: ProjectedDsConfig;
  readonly fidelityReport: FidelityReport;
}

// ============================================================================
// TOKEN CATEGORY HEURISTICS
// ============================================================================

/**
 * Infer the DS token category from a prop name using naming conventions.
 */
function inferTokenCategory(propName: string): DsTokenCategory {
  const lower = propName.toLowerCase();
  if (
    lower.startsWith("color") ||
    lower.endsWith("color") ||
    lower.includes("background") ||
    lower.includes("foreground") ||
    lower.startsWith("bg") ||
    lower.startsWith("text")
  ) {
    return "color";
  }
  if (
    lower.startsWith("margin") ||
    lower.startsWith("padding") ||
    lower.startsWith("gap") ||
    lower.includes("spacing") ||
    lower.includes("size") ||
    lower.includes("width") ||
    lower.includes("height")
  ) {
    return "spacing";
  }
  if (
    lower.includes("font") ||
    lower.includes("typography") ||
    lower.includes("lineheight") ||
    lower.includes("letterspacing") ||
    lower.startsWith("text")
  ) {
    return "typography";
  }
  if (lower.includes("shadow") || lower.includes("elevation")) {
    return "shadow";
  }
  if (
    lower.includes("duration") ||
    lower.includes("animation") ||
    lower.includes("transition") ||
    lower.includes("easing")
  ) {
    return "motion";
  }
  if (lower.includes("zindex") || lower.includes("z-index") || lower.includes("layer")) {
    return "zIndex";
  }
  return "unknown";
}

// ============================================================================
// MAIN PROJECTION
// ============================================================================

/**
 * Project the design-system-relevant aspects of a LogicalArtifactModel
 * into a DS Generator configuration.
 *
 * Only nodes that `usesDesignSystem === true` are projected as component
 * contracts. Nodes that use the DS but have no inferred props contribute
 * only their name (no token prop mapping).
 *
 * @doc.type function
 * @doc.purpose Project LogicalArtifactModel → DS Generator config
 * @doc.layer platform
 * @doc.pattern Adapter
 */
export function projectToDs(model: LogicalArtifactModel): ProjectDsResult {
  const lossPoints: LossPoint[] = [];
  const components: ProjectedDsComponentContract[] = [];
  const usedTokenCategories = new Set<DsTokenCategory>();

  const dsNodes = Object.values(model.nodes).filter(
    (node: ArtifactNode) => node.usesDesignSystem,
  );

  if (dsNodes.length === 0) {
    return {
      config: {
        components: [],
        usedTokenCategories: new Set(),
        sourceModelId: model.modelId,
      },
      fidelityReport: computeFidelityReport([], model.modelId, "pipeline"),
    };
  }

  for (const node of dsNodes) {
    const tokenProps = Object.keys(node.inferredProps)
      .map((propName) => {
        const category = inferTokenCategory(propName);
        if (category !== "unknown") {
          usedTokenCategories.add(category);
        }
        return { propName, inferredTokenCategory: category };
      })
      .filter(({ inferredTokenCategory }) => inferredTokenCategory !== "unknown");

    // Flag nodes with no mappable token props
    if (Object.keys(node.inferredProps).length > 0 && tokenProps.length === 0) {
      lossPoints.push({
        code: "no-token-props-mapped",
        description: `Component "${node.displayName}" uses design system but no props could be mapped to token categories.`,
        severity: "info",
        sourceRef: node.sourceRef,
        confidenceImpact: 0.02,
      });
    }

    components.push({
      name: node.displayName,
      tokenProps,
      sourceRef: node.sourceRef
        ? { relativePath: node.sourceRef.file.relativePath }
        : undefined,
    });
  }

  const config: ProjectedDsConfig = {
    components,
    usedTokenCategories,
    sourceModelId: model.modelId,
  };

  const fidelityReport = computeFidelityReport(
    lossPoints,
    model.modelId,
    "pipeline",
  );

  return { config, fidelityReport };
}
