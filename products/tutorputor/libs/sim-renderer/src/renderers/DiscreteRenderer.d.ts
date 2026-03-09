/**
 * Discrete Renderer - Algorithms & Data Structures
 *
 * @doc.type module
 * @doc.purpose Render discrete simulation entities (nodes, edges, pointers)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import type { DiscreteNodeEntity, DiscreteEdgeEntity, DiscretePointerEntity } from "@ghatana/tutorputor-contracts/v1/simulation";
import type { EntityRenderer } from "../types";
/**
 * Renderer for discrete algorithm nodes.
 */
export declare const discreteNodeRenderer: EntityRenderer<DiscreteNodeEntity>;
/**
 * Renderer for discrete algorithm edges (connections between nodes).
 */
export declare const discreteEdgeRenderer: EntityRenderer<DiscreteEdgeEntity>;
/**
 * Renderer for pointers (i, j, left, right markers).
 */
export declare const discretePointerRenderer: EntityRenderer<DiscretePointerEntity>;
export declare const discreteRenderers: EntityRenderer<DiscreteNodeEntity>[];
//# sourceMappingURL=DiscreteRenderer.d.ts.map