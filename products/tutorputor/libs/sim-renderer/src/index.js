/**
 * TutorPutor Simulation Renderer Library
 *
 * A comprehensive rendering library for educational simulations across
 * multiple domains: algorithms, physics, chemistry, biology, and medicine.
 *
 * @doc.type module
 * @doc.purpose Export simulation rendering primitives, renderers, and hooks
 * @doc.layer product
 * @doc.pattern Barrel
 *
 * @example
 * ```tsx
 * import {
 *   useRendererRegistry,
 *   useRenderContext,
 *   useCanvasRendering,
 *   discreteRenderers,
 * } from '@ghatana/tutorputor-sim-renderer';
 *
 * function SimCanvas({ entities }: { entities: SimEntity[] }) {
 *   const canvasRef = useRef<HTMLCanvasElement>(null);
 *   const registry = useRendererRegistry();
 *   const context = useRenderContext({
 *     canvasRef,
 *     entities,
 *     width: 800,
 *     height: 600,
 *   });
 *
 *   useCanvasRendering({
 *     canvasRef,
 *     registry,
 *     context,
 *     entities,
 *   });
 *
 *   return <canvas ref={canvasRef} width={800} height={600} />;
 * }
 * ```
 */
export { defaultTheme } from "./types";
// Type guards
export { isDiscreteNodeEntity, isDiscreteEdgeEntity, isDiscretePointerEntity, isPhysicsBodyEntity, isPhysicsSpringEntity, isPhysicsVectorEntity, isPhysicsParticleEntity, isChemAtomEntity, isChemBondEntity, isChemMoleculeEntity, isChemReactionArrowEntity, isChemEnergyProfileEntity, isBioCellEntity, isBioOrganelleEntity, isBioCompartmentEntity, isBioEnzymeEntity, isBioSignalEntity, isBioGeneEntity, isMedCompartmentEntity, isMedDoseEntity, isMedInfectionAgentEntity, } from "./types";
// =============================================================================
// Primitives
// =============================================================================
export { 
// Shapes
drawRect, drawRoundedRect, drawCircle, drawEllipse, drawPolygon, drawRegularPolygon, drawDiamond, 
// Lines
drawLine, drawQuadraticCurve, drawBezierCurve, drawArrow, drawBidirectionalArrow, 
// Text
drawText, measureText, 
// Special
drawSpring, drawVector, drawBond, 
// Effects
applyGlow, clearGlow, } from "./primitives";
// =============================================================================
// Renderers
// =============================================================================
// All renderers
export { allRenderers } from "./renderers";
// Discrete (Algorithms)
export { discreteNodeRenderer, discreteEdgeRenderer, discretePointerRenderer, discreteRenderers, } from "./renderers";
// Physics
export { physicsBodyRenderer, physicsSpringRenderer, physicsVectorRenderer, physicsParticleRenderer, physicsRenderers, } from "./renderers";
// Chemistry
export { chemAtomRenderer, chemBondRenderer, chemMoleculeRenderer, chemReactionArrowRenderer, chemEnergyProfileRenderer, chemistryRenderers, } from "./renderers";
// Biology
export { bioCellRenderer, bioOrganelleRenderer, bioCompartmentRenderer, bioEnzymeRenderer, bioSignalRenderer, bioGeneRenderer, biologyRenderers, } from "./renderers";
// Medicine
export { medCompartmentRenderer, medDoseRenderer, medInfectionAgentRenderer, medicineRenderers, } from "./renderers";
// =============================================================================
// Hooks
// =============================================================================
export { useRendererRegistry, useRenderContext, useAnimation, useHitTest, useCanvasRendering, } from "./hooks";
// =============================================================================
// Easing & Animation
// =============================================================================
export { applyEasing, lerp, lerpColor, clamp } from "./easing";
//# sourceMappingURL=index.js.map