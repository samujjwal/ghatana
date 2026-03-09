/**
 * Simulation Renderer Types
 *
 * @doc.type module
 * @doc.purpose Define core types for simulation rendering system
 * @doc.layer product
 * @doc.pattern Schema
 */

import type {
    SimEntity,
    SimEntityBase,
    SimEntityId,
    SimulationDomain,
    EasingFunction,
    DiscreteNodeEntity,
    DiscreteEdgeEntity,
    DiscretePointerEntity,
    PhysicsBodyEntity,
    PhysicsSpringEntity,
    PhysicsVectorEntity,
    PhysicsParticleEntity,
    ChemAtomEntity,
    ChemBondEntity,
    ChemMoleculeEntity,
    ChemReactionArrowEntity,
    ChemEnergyProfileEntity,
    BioCellEntity,
    BioOrganelleEntity,
    BioCompartmentEntity,
    BioEnzymeEntity,
    BioSignalEntity,
    BioGeneEntity,
    MedCompartmentEntity,
    MedDoseEntity,
    MedInfectionAgentEntity,
} from "@ghatana/tutorputor-contracts/v1/simulation";

// =============================================================================
// Render Context
// =============================================================================

/**
 * Rendering context passed to all renderers.
 */
export interface RenderContext {
    /** Canvas 2D rendering context */
    ctx: CanvasRenderingContext2D;
    /** Current zoom level */
    zoom: number;
    /** Pan offset */
    panOffset: { x: number; y: number };
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
    worldToScreen: (worldX: number, worldY: number) => { x: number; y: number };
    /** Convert screen to world coordinates */
    screenToWorld: (screenX: number, screenY: number) => { x: number; y: number };
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
export const defaultTheme: RenderTheme = {
    primary: "#3b82f6",
    secondary: "#6366f1",
    success: "#22c55e",
    warning: "#f59e0b",
    danger: "#ef4444",
    neutral: "#64748b",
    background: "#f8fafc",
    foreground: "#0f172a",
    border: "#e2e8f0",
    highlight: "#fbbf24",
    fontFamily: "Inter, system-ui, sans-serif",
};

// =============================================================================
// Renderer Interfaces
// =============================================================================

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
    render(
        entity: T,
        context: RenderContext,
        isHovered?: boolean,
        isSelected?: boolean
    ): void;
    /**
     * Check if a point intersects with the entity.
     * @param entity - The entity to check
     * @param worldX - World X coordinate
     * @param worldY - World Y coordinate
     * @param context - Rendering context
     * @returns true if the point intersects
     */
    hitTest(
        entity: T,
        worldX: number,
        worldY: number,
        context: RenderContext
    ): boolean;
    /**
     * Get the bounding box of an entity.
     * @param entity - The entity
     * @param context - Rendering context
     */
    getBounds(
        entity: T,
        context: RenderContext
    ): { x: number; y: number; width: number; height: number };
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

// =============================================================================
// Animation Types
// =============================================================================

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

// =============================================================================
// Render Primitives
// =============================================================================

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

// =============================================================================
// Domain-Specific Entity Type Guards
// =============================================================================

export function isDiscreteNodeEntity(
    entity: SimEntity
): entity is DiscreteNodeEntity {
    return entity.type === "node";
}

export function isDiscreteEdgeEntity(
    entity: SimEntity
): entity is DiscreteEdgeEntity {
    return entity.type === "edge";
}

export function isDiscretePointerEntity(
    entity: SimEntity
): entity is DiscretePointerEntity {
    return entity.type === "pointer";
}

export function isPhysicsBodyEntity(
    entity: SimEntity
): entity is PhysicsBodyEntity {
    return entity.type === "rigidBody";
}

export function isPhysicsSpringEntity(
    entity: SimEntity
): entity is PhysicsSpringEntity {
    return entity.type === "spring";
}

export function isPhysicsVectorEntity(
    entity: SimEntity
): entity is PhysicsVectorEntity {
    return entity.type === "vector";
}

export function isPhysicsParticleEntity(
    entity: SimEntity
): entity is PhysicsParticleEntity {
    return entity.type === "particle";
}

export function isChemAtomEntity(entity: SimEntity): entity is ChemAtomEntity {
    return entity.type === "atom";
}

export function isChemBondEntity(entity: SimEntity): entity is ChemBondEntity {
    return entity.type === "bond";
}

export function isChemMoleculeEntity(
    entity: SimEntity
): entity is ChemMoleculeEntity {
    return entity.type === "molecule";
}

export function isChemReactionArrowEntity(
    entity: SimEntity
): entity is ChemReactionArrowEntity {
    return entity.type === "reactionArrow";
}

export function isChemEnergyProfileEntity(
    entity: SimEntity
): entity is ChemEnergyProfileEntity {
    return entity.type === "energyProfile";
}

export function isBioCellEntity(entity: SimEntity): entity is BioCellEntity {
    return entity.type === "cell";
}

export function isBioOrganelleEntity(
    entity: SimEntity
): entity is BioOrganelleEntity {
    return entity.type === "organelle";
}

export function isBioCompartmentEntity(
    entity: SimEntity
): entity is BioCompartmentEntity {
    return entity.type === "compartment";
}

export function isBioEnzymeEntity(
    entity: SimEntity
): entity is BioEnzymeEntity {
    return entity.type === "enzyme";
}

export function isBioSignalEntity(
    entity: SimEntity
): entity is BioSignalEntity {
    return entity.type === "signal";
}

export function isBioGeneEntity(entity: SimEntity): entity is BioGeneEntity {
    return entity.type === "gene";
}

export function isMedCompartmentEntity(
    entity: SimEntity
): entity is MedCompartmentEntity {
    return entity.type === "pkCompartment";
}

export function isMedDoseEntity(entity: SimEntity): entity is MedDoseEntity {
    return entity.type === "dose";
}

export function isMedInfectionAgentEntity(
    entity: SimEntity
): entity is MedInfectionAgentEntity {
    return entity.type === "infectionAgent";
}
