/**
 * Renderers Index
 *
 * @doc.type module
 * @doc.purpose Export all domain-specific renderers
 * @doc.layer product
 * @doc.pattern Barrel
 */
// Discrete (Algorithms & Data Structures)
export { discreteNodeRenderer, discreteEdgeRenderer, discretePointerRenderer, discreteRenderers, } from "./DiscreteRenderer";
// Physics (Mechanics & Dynamics)
export { physicsBodyRenderer, physicsSpringRenderer, physicsVectorRenderer, physicsParticleRenderer, physicsRenderers, } from "./PhysicsRenderer";
// Chemistry (Molecules & Reactions)
export { chemAtomRenderer, chemBondRenderer, chemMoleculeRenderer, chemReactionArrowRenderer, chemEnergyProfileRenderer, chemistryRenderers, } from "./ChemistryRenderer";
// Biology (Cells & Molecular Biology)
export { bioCellRenderer, bioOrganelleRenderer, bioCompartmentRenderer, bioEnzymeRenderer, bioSignalRenderer, bioGeneRenderer, biologyRenderers, } from "./BiologyRenderer";
// Medicine (Pharmacokinetics & Epidemiology)
export { medCompartmentRenderer, medDoseRenderer, medInfectionAgentRenderer, medicineRenderers, } from "./MedicineRenderer";
// 3D Renderers (React Three Fiber & NGL)
export { Physics3DRenderer, PhysicsRendererWithToggle, usePhysics3DState, } from "./Physics3DRenderer";
export { NGLMoleculeRenderer, Chemistry3DRenderer, useChemistry3DState, } from "./Chemistry3DRenderer";
// All renderers combined
import { discreteRenderers } from "./DiscreteRenderer";
import { physicsRenderers } from "./PhysicsRenderer";
import { chemistryRenderers } from "./ChemistryRenderer";
import { biologyRenderers } from "./BiologyRenderer";
import { medicineRenderers } from "./MedicineRenderer";
/**
 * All available entity renderers.
 */
export const allRenderers = [
    ...discreteRenderers,
    ...physicsRenderers,
    ...chemistryRenderers,
    ...biologyRenderers,
    ...medicineRenderers,
];
//# sourceMappingURL=index.js.map