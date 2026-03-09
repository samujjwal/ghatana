/**
 * Simulation Renderer Types
 *
 * @doc.type module
 * @doc.purpose Define core types for simulation rendering system
 * @doc.layer product
 * @doc.pattern Schema
 */
import type { SimEntity, SimEntityBase, SimEntityId, SimulationDomain, EasingFunction, DiscreteNodeEntity, DiscreteEdgeEntity, DiscretePointerEntity, PhysicsBodyEntity, PhysicsSpringEntity, PhysicsVectorEntity, PhysicsParticleEntity, ChemAtomEntity, ChemBondEntity, ChemMoleculeEntity, ChemReactionArrowEntity, ChemEnergyProfileEntity, BioCellEntity, BioOrganelleEntity, BioCompartmentEntity, BioEnzymeEntity, BioSignalEntity, BioGeneEntity, MedCompartmentEntity, MedDoseEntity, MedInfectionAgentEntity } from "@ghatana/tutorputor-contracts/v1/simulation";
/**
 * Rendering context passed to all renderers.
 */
export interface RenderContext {
    /** Canvas 2D rendering context */
    ctx: CanvasRenderingContext2D;
    /** Current zoom level */
    zoom: number;
    /** Pan offset */
    panOffset: {
        x: number;
        y: number;
    };
    /** Canvas dimensions */
    width: number;
    height: number;
    /** Current timestamp for animations */
    timestamp: number;
    /** Delta time since last frame */
    deltaTime: number;
    /** Theme colors */
    theme: RenderTheme;
    /** Entity lookup for references */
    entities: Map<SimEntityId, SimEntity>;
    /** Convert world to screen coordinates */
    worldToScreen: (worldX: number, worldY: number) => {
        x: number;
        y: number;
    };
    /** Convert screen to world coordinates */
    screenToWorld: (screenX: number, screenY: number) => {
        x: number;
        y: number;
    };
}
/**
 * Theme configuration for rendering.
 */
export interface RenderTheme {
    /** Primary color */
    primary: string;
    /** Secondary color */
    secondary: string;
    /** Success color */
    success: string;
    /** Warning color */
    warning: string;
    /** Danger/error color */
    danger: string;
    /** Neutral/muted color */
    neutral: string;
    /** Background color */
    background: string;
    /** Foreground/text color */
    foreground: string;
    /** Border color */
    border: string;
    /** Highlight color */
    highlight: string;
    /** Font family */
    fontFamily: string;
}
/**
 * Default theme configuration.
 */
export declare const defaultTheme: RenderTheme;
/**
 * Base renderer interface - all domain renderers implement this.
 */
export interface EntityRenderer<T extends SimEntityBase = SimEntity> {
    /** Entity types this renderer handles */
    readonly entityTypes: readonly string[];
    /** Domain this renderer belongs to */
    readonly domain: SimulationDomain;
    /**
     * Render an entity to the canvas.
     * @param entity - The entity to render
     * @param context - Rendering context
     * @param isHovered - Whether the entity is currently hovered
     * @param isSelected - Whether the entity is currently selected
     */
    render(entity: T, context: RenderContext, isHovered?: boolean, isSelected?: boolean): void;
    /**
     * Check if a point intersects with the entity.
     * @param entity - The entity to check
     * @param worldX - World X coordinate
     * @param worldY - World Y coordinate
     * @param context - Rendering context
     * @returns true if the point intersects
     */
    hitTest(entity: T, worldX: number, worldY: number, context: RenderContext): boolean;
    /**
     * Get the bounding box of an entity.
     * @param entity - The entity
     * @param context - Rendering context
     */
    getBounds(entity: T, context: RenderContext): {
        x: number;
        y: number;
        width: number;
        height: number;
    };
}
/**
 * Registry for entity renderers by type.
 */
export interface RendererRegistry {
    /** Register a renderer */
    register<T extends SimEntityBase>(renderer: EntityRenderer<T>): void;
    /** Get renderer for an entity type */
    getRenderer(entityType: string): EntityRenderer | undefined;
    /** Get all renderers for a domain */
    getRenderersByDomain(domain: SimulationDomain): EntityRenderer[];
    /** Check if a renderer exists for an entity type */
    hasRenderer(entityType: string): boolean;
}
/**
 * Animation state for an entity.
 */
export interface EntityAnimation {
    /** Entity being animated */
    entityId: SimEntityId;
    /** Animation start time */
    startTime: number;
    /** Animation duration in ms */
    duration: number;
    /** Easing function */
    easing: EasingFunction;
    /** Property being animated */
    property: string;
    /** Start value */
    fromValue: number;
    /** End value */
    toValue: number;
    /** Delay before animation starts */
    delay: number;
    /** Callback when animation completes */
    onComplete?: () => void;
}
/**
 * Animation manager state.
 */
export interface AnimationState {
    /** Active animations */
    animations: EntityAnimation[];
    /** Animation frame ID */
    frameId: number | null;
    /** Is animation loop running */
    isRunning: boolean;
}
/**
 * Line style configuration.
 */
export interface LineStyle {
    color: string;
    width: number;
    dash?: number[];
    cap?: CanvasLineCap;
    join?: CanvasLineJoin;
}
/**
 * Fill style configuration.
 */
export interface FillStyle {
    color: string;
    opacity?: number;
}
/**
 * Text style configuration.
 */
export interface TextStyle {
    color: string;
    fontSize: number;
    fontFamily?: string;
    fontWeight?: string;
    align?: CanvasTextAlign;
    baseline?: CanvasTextBaseline;
}
/**
 * Arrow head configuration.
 */
export interface ArrowHeadStyle {
    size: number;
    angle: number;
    filled: boolean;
    color?: string;
}
export declare function isDiscreteNodeEntity(entity: SimEntity): entity is DiscreteNodeEntity;
export declare function isDiscreteEdgeEntity(entity: SimEntity): entity is DiscreteEdgeEntity;
export declare function isDiscretePointerEntity(entity: SimEntity): entity is DiscretePointerEntity;
export declare function isPhysicsBodyEntity(entity: SimEntity): entity is PhysicsBodyEntity;
export declare function isPhysicsSpringEntity(entity: SimEntity): entity is PhysicsSpringEntity;
export declare function isPhysicsVectorEntity(entity: SimEntity): entity is PhysicsVectorEntity;
export declare function isPhysicsParticleEntity(entity: SimEntity): entity is PhysicsParticleEntity;
export declare function isChemAtomEntity(entity: SimEntity): entity is ChemAtomEntity;
export declare function isChemBondEntity(entity: SimEntity): entity is ChemBondEntity;
export declare function isChemMoleculeEntity(entity: SimEntity): entity is ChemMoleculeEntity;
export declare function isChemReactionArrowEntity(entity: SimEntity): entity is ChemReactionArrowEntity;
export declare function isChemEnergyProfileEntity(entity: SimEntity): entity is ChemEnergyProfileEntity;
export declare function isBioCellEntity(entity: SimEntity): entity is BioCellEntity;
export declare function isBioOrganelleEntity(entity: SimEntity): entity is BioOrganelleEntity;
export declare function isBioCompartmentEntity(entity: SimEntity): entity is BioCompartmentEntity;
export declare function isBioEnzymeEntity(entity: SimEntity): entity is BioEnzymeEntity;
export declare function isBioSignalEntity(entity: SimEntity): entity is BioSignalEntity;
export declare function isBioGeneEntity(entity: SimEntity): entity is BioGeneEntity;
export declare function isMedCompartmentEntity(entity: SimEntity): entity is MedCompartmentEntity;
export declare function isMedDoseEntity(entity: SimEntity): entity is MedDoseEntity;
export declare function isMedInfectionAgentEntity(entity: SimEntity): entity is MedInfectionAgentEntity;
//# sourceMappingURL=types.d.ts.map