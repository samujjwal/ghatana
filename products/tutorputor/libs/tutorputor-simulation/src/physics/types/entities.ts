/**
 * @doc.type enum
 * @doc.purpose Defines all available physics entity types
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export enum EntityType {
    BALL = 'BALL',
    BOX = 'BOX',
    PLATFORM = 'PLATFORM',
    RAMP = 'RAMP',
    PULLEY = 'PULLEY',
    SPRING = 'SPRING',
    PENDULUM = 'PENDULUM',
    WALL = 'WALL',
    LEVER = 'LEVER',
    WHEEL = 'WHEEL',
}

/**
 * @doc.type interface
 * @doc.purpose Physics properties for simulation entities
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export interface PhysicsProperties {
    /** Mass in kilograms */
    mass: number;
    /** Friction coefficient (0-1) */
    friction: number;
    /** Restitution/bounciness (0-1) */
    restitution: number;
    /** Whether entity is static (immovable) */
    isStatic: boolean;
    /** Angular velocity in radians/second */
    angularVelocity?: number;
    /** Linear velocity vector */
    velocity?: { x: number; y: number };
    /** Additional custom properties */
    [key: string]: unknown;
}

/**
 * @doc.type interface
 * @doc.purpose Visual appearance configuration for entities
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export interface EntityAppearance {
    /** Fill color (hex or named) */
    color: string;
    /** Stroke color for outline */
    strokeColor?: string;
    /** Stroke width in pixels */
    strokeWidth?: number;
    /** Opacity (0-1) */
    opacity?: number;
    /** Shadow blur radius */
    shadowBlur?: number;
    /** Shadow color */
    shadowColor?: string;
}

/**
 * @doc.type interface
 * @doc.purpose Core entity representation for physics simulations
 * @doc.layer core
 * @doc.pattern Entity
 */
export interface PhysicsEntity {
    /** Unique identifier */
    id: string;
    /** Entity type from EntityType enum */
    type: EntityType;
    /** X position in canvas coordinates */
    x: number;
    /** Y position in canvas coordinates */
    y: number;
    /** Width for rectangular entities */
    width?: number;
    /** Height for rectangular entities */
    height?: number;
    /** Radius for circular entities */
    radius?: number;
    /** Rotation in degrees */
    rotation?: number;
    /** Visual appearance */
    appearance: EntityAppearance;
    /** Physics simulation properties */
    physics: PhysicsProperties;
    /** Optional custom metadata */
    metadata?: Record<string, unknown>;
}

/**
 * @doc.type interface
 * @doc.purpose Global physics world configuration
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export interface PhysicsConfig {
    /** Gravity in m/s² (default: 9.81) */
    gravity: number;
    /** Global friction multiplier */
    friction: number;
    /** Time scale for simulation speed */
    timeScale: number;
    /** Whether collision detection is enabled */
    collisionEnabled: boolean;
    /** Air resistance coefficient */
    airResistance?: number;
    /** Whether to show debug info */
    debugMode?: boolean;
}

/**
 * @doc.type interface
 * @doc.purpose Toolbox item configuration for entity creation
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export interface ToolboxItem {
    /** Entity type this creates */
    type: EntityType;
    /** Display icon (emoji or icon class) */
    icon: string;
    /** Display label */
    label: string;
    /** Default color for new entities */
    defaultColor: string;
    /** Tooltip/description */
    description?: string;
    /** Whether this is a premium/advanced entity */
    isPremium?: boolean;
}

/**
 * @doc.type type
 * @doc.purpose Selection state for canvas entities
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export type EntitySelection = {
    /** Currently selected entity ID */
    selectedId: string | null;
    /** Multi-selection IDs */
    multiSelect: string[];
    /** Whether selection is locked */
    isLocked: boolean;
};
