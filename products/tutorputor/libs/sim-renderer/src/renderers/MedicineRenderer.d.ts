/**
 * Medicine Renderer - Pharmacokinetics & Epidemiology
 *
 * @doc.type module
 * @doc.purpose Render medicine simulation entities (compartments, doses, infection agents)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import type { MedCompartmentEntity, MedDoseEntity, MedInfectionAgentEntity } from "@ghatana/tutorputor-contracts/v1/simulation";
import type { EntityRenderer } from "../types";
/**
 * Renderer for pharmacokinetic compartments.
 */
export declare const medCompartmentRenderer: EntityRenderer<MedCompartmentEntity>;
/**
 * Renderer for drug doses.
 */
export declare const medDoseRenderer: EntityRenderer<MedDoseEntity>;
/**
 * Renderer for infection agents (epidemiology).
 */
export declare const medInfectionAgentRenderer: EntityRenderer<MedInfectionAgentEntity>;
export declare const medicineRenderers: EntityRenderer<MedCompartmentEntity>[];
//# sourceMappingURL=MedicineRenderer.d.ts.map