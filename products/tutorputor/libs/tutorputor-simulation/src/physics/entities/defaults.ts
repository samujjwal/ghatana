import { EntityType, type PhysicsEntity, type PhysicsProperties, type ToolboxItem } from '../types';

/**
 * @doc.type constant
 * @doc.purpose Default physics properties for new entities
 * @doc.layer core
 * @doc.pattern Factory
 */
export const DEFAULT_PHYSICS: PhysicsProperties = {
    mass: 1,
    friction: 0.5,
    restitution: 0.3,
    isStatic: false,
};

/**
 * @doc.type constant
 * @doc.purpose Default dimensions and properties per entity type
 * @doc.layer core
 * @doc.pattern Registry
 */
export const ENTITY_DEFAULTS: Record<EntityType, Partial<PhysicsEntity>> = {
    [EntityType.BALL]: {
        radius: 30,
        physics: { ...DEFAULT_PHYSICS, restitution: 0.7 },
    },
    [EntityType.BOX]: {
        width: 60,
        height: 60,
        physics: { ...DEFAULT_PHYSICS },
    },
    [EntityType.PLATFORM]: {
        width: 150,
        height: 20,
        physics: { ...DEFAULT_PHYSICS, isStatic: true, mass: 0 },
    },
    [EntityType.RAMP]: {
        width: 100,
        height: 50,
        rotation: 0,
        physics: { ...DEFAULT_PHYSICS, isStatic: true },
    },
    [EntityType.PULLEY]: {
        radius: 25,
        physics: { ...DEFAULT_PHYSICS, friction: 0.1 },
    },
    [EntityType.SPRING]: {
        width: 60,
        height: 20,
        physics: { ...DEFAULT_PHYSICS, restitution: 0.9 },
    },
    [EntityType.PENDULUM]: {
        radius: 20,
        width: 100, // rope length
        physics: { ...DEFAULT_PHYSICS },
    },
    [EntityType.WALL]: {
        width: 20,
        height: 150,
        physics: { ...DEFAULT_PHYSICS, isStatic: true, mass: 0 },
    },
    [EntityType.LEVER]: {
        width: 120,
        height: 10,
        physics: { ...DEFAULT_PHYSICS },
    },
    [EntityType.WHEEL]: {
        radius: 30,
        physics: { ...DEFAULT_PHYSICS, friction: 0.8 },
    },
};

/**
 * @doc.type constant
 * @doc.purpose Toolbox configuration for all entity types
 * @doc.layer core
 * @doc.pattern Registry
 */
export const TOOLBOX_ITEMS: ToolboxItem[] = [
    {
        type: EntityType.BALL,
        icon: '⚽',
        label: 'Ball',
        defaultColor: '#ef4444',
        description: 'A bouncy sphere affected by gravity',
    },
    {
        type: EntityType.BOX,
        icon: '📦',
        label: 'Box',
        defaultColor: '#f59e0b',
        description: 'A rectangular object that can slide and stack',
    },
    {
        type: EntityType.PLATFORM,
        icon: '▬',
        label: 'Platform',
        defaultColor: '#84cc16',
        description: 'A static horizontal surface',
    },
    {
        type: EntityType.RAMP,
        icon: '◢',
        label: 'Ramp',
        defaultColor: '#10b981',
        description: 'An inclined surface for rolling objects',
    },
    {
        type: EntityType.PULLEY,
        icon: '⚙️',
        label: 'Pulley',
        defaultColor: '#06b6d4',
        description: 'A rotating wheel for rope systems',
    },
    {
        type: EntityType.SPRING,
        icon: '〰️',
        label: 'Spring',
        defaultColor: '#3b82f6',
        description: 'An elastic connector between objects',
    },
    {
        type: EntityType.PENDULUM,
        icon: '⚖️',
        label: 'Pendulum',
        defaultColor: '#8b5cf6',
        description: 'A swinging weight on a fixed point',
    },
    {
        type: EntityType.WALL,
        icon: '🧱',
        label: 'Wall',
        defaultColor: '#6b7280',
        description: 'A static vertical barrier',
    },
    {
        type: EntityType.LEVER,
        icon: '↔️',
        label: 'Lever',
        defaultColor: '#ec4899',
        description: 'A pivoting bar for mechanical advantage',
    },
    {
        type: EntityType.WHEEL,
        icon: '⚙',
        label: 'Wheel',
        defaultColor: '#f97316',
        description: 'A rotating circle with axle marks',
    },
];

/**
 * Creates a new entity with default properties
 * @doc.type function
 * @doc.purpose Factory function for creating physics entities
 * @doc.layer core
 * @doc.pattern Factory
 */
export function createEntity(
    type: EntityType,
    x: number,
    y: number,
    overrides?: Partial<PhysicsEntity>
): PhysicsEntity {
    const defaults = ENTITY_DEFAULTS[type];
    const toolboxItem = TOOLBOX_ITEMS.find((item) => item.type === type);

    return {
        id: `entity-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
        type,
        x,
        y,
        ...defaults,
        appearance: {
            color: toolboxItem?.defaultColor || '#3b82f6',
            ...overrides?.appearance,
        },
        physics: {
            ...DEFAULT_PHYSICS,
            ...defaults?.physics,
            ...overrides?.physics,
        },
        ...overrides,
    } as PhysicsEntity;
}

/**
 * Default physics configuration for new simulations
 * @doc.type constant
 * @doc.purpose Default physics world settings
 * @doc.layer core
 * @doc.pattern Factory
 */
export const DEFAULT_PHYSICS_CONFIG = {
    gravity: 9.81,
    friction: 0.5,
    timeScale: 1,
    collisionEnabled: true,
    airResistance: 0.01,
    debugMode: false,
};
