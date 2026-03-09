/**
 * Chemistry Renderer - Molecules & Reactions
 *
 * @doc.type module
 * @doc.purpose Render chemistry simulation entities (atoms, bonds, molecules, reactions)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import type { ChemAtomEntity, ChemBondEntity, ChemMoleculeEntity, ChemReactionArrowEntity, ChemEnergyProfileEntity } from "@ghatana/tutorputor-contracts/v1/simulation";
import type { EntityRenderer } from "../types";
/**
 * Renderer for chemistry atoms.
 */
export declare const chemAtomRenderer: EntityRenderer<ChemAtomEntity>;
/**
 * Renderer for chemistry bonds.
 */
export declare const chemBondRenderer: EntityRenderer<ChemBondEntity>;
/**
 * Renderer for molecule containers (draws bounding box/highlight).
 */
export declare const chemMoleculeRenderer: EntityRenderer<ChemMoleculeEntity>;
/**
 * Renderer for reaction arrows.
 */
export declare const chemReactionArrowRenderer: EntityRenderer<ChemReactionArrowEntity>;
/**
 * Renderer for reaction energy profiles.
 */
export declare const chemEnergyProfileRenderer: EntityRenderer<ChemEnergyProfileEntity>;
export declare const chemistryRenderers: EntityRenderer<ChemAtomEntity>[];
//# sourceMappingURL=ChemistryRenderer.d.ts.map