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
export type { RenderContext, RenderTheme, EntityRenderer, RendererRegistry, EntityAnimation, AnimationState, LineStyle, FillStyle, TextStyle, ArrowHeadStyle, } from "./types";
export { defaultTheme } from "./types";
export { isDiscreteNodeEntity, isDiscreteEdgeEntity, isDiscretePointerEntity, isPhysicsBodyEntity, isPhysicsSpringEntity, isPhysicsVectorEntity, isPhysicsParticleEntity, isChemAtomEntity, isChemBondEntity, isChemMoleculeEntity, isChemReactionArrowEntity, isChemEnergyProfileEntity, isBioCellEntity, isBioOrganelleEntity, isBioCompartmentEntity, isBioEnzymeEntity, isBioSignalEntity, isBioGeneEntity, isMedCompartmentEntity, isMedDoseEntity, isMedInfectionAgentEntity, } from "./types";
export { drawRect, drawRoundedRect, drawCircle, drawEllipse, drawPolygon, drawRegularPolygon, drawDiamond, drawLine, drawQuadraticCurve, drawBezierCurve, drawArrow, drawBidirectionalArrow, drawText, measureText, drawSpring, drawVector, drawBond, applyGlow, clearGlow, } from "./primitives";
export { allRenderers } from "./renderers";
export { discreteNodeRenderer, discreteEdgeRenderer, discretePointerRenderer, discreteRenderers, } from "./renderers";
export { physicsBodyRenderer, physicsSpringRenderer, physicsVectorRenderer, physicsParticleRenderer, physicsRenderers, } from "./renderers";
export { chemAtomRenderer, chemBondRenderer, chemMoleculeRenderer, chemReactionArrowRenderer, chemEnergyProfileRenderer, chemistryRenderers, } from "./renderers";
export { bioCellRenderer, bioOrganelleRenderer, bioCompartmentRenderer, bioEnzymeRenderer, bioSignalRenderer, bioGeneRenderer, biologyRenderers, } from "./renderers";
export { medCompartmentRenderer, medDoseRenderer, medInfectionAgentRenderer, medicineRenderers, } from "./renderers";
export { useRendererRegistry, useRenderContext, useAnimation, useHitTest, useCanvasRendering, } from "./hooks";
export { applyEasing, lerp, lerpColor, clamp } from "./easing";
//# sourceMappingURL=index.d.ts.map