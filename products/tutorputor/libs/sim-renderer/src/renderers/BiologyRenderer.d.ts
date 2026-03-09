/**
 * Biology Renderer - Cells & Molecular Biology
 *
 * @doc.type module
 * @doc.purpose Render biology simulation entities (cells, organelles, enzymes, genes)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import type { BioCellEntity, BioOrganelleEntity, BioCompartmentEntity, BioEnzymeEntity, BioSignalEntity, BioGeneEntity } from "@ghatana/tutorputor-contracts/v1/simulation";
import type { EntityRenderer } from "../types";
/**
 * Renderer for biological cells.
 */
export declare const bioCellRenderer: EntityRenderer<BioCellEntity>;
/**
 * Renderer for cell organelles.
 */
export declare const bioOrganelleRenderer: EntityRenderer<BioOrganelleEntity>;
/**
 * Renderer for biological compartments (reaction chambers).
 */
export declare const bioCompartmentRenderer: EntityRenderer<BioCompartmentEntity>;
/**
 * Renderer for enzymes.
 */
export declare const bioEnzymeRenderer: EntityRenderer<BioEnzymeEntity>;
/**
 * Renderer for signaling molecules.
 */
export declare const bioSignalRenderer: EntityRenderer<BioSignalEntity>;
/**
 * Renderer for genes.
 */
export declare const bioGeneRenderer: EntityRenderer<BioGeneEntity>;
export declare const biologyRenderers: EntityRenderer<BioCellEntity>[];
//# sourceMappingURL=BiologyRenderer.d.ts.map