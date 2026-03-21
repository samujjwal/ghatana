import { EntityType, type PhysicsEntity, type PhysicsProperties, type ToolboxItem } from '../types';
/**
 * @doc.type constant
 * @doc.purpose Default physics properties for new entities
 * @doc.layer core
 * @doc.pattern Factory
 */
export declare const DEFAULT_PHYSICS: PhysicsProperties;
/**
 * @doc.type constant
 * @doc.purpose Default dimensions and properties per entity type
 * @doc.layer core
 * @doc.pattern Registry
 */
export declare const ENTITY_DEFAULTS: Record<EntityType, Partial<PhysicsEntity>>;
/**
 * @doc.type constant
 * @doc.purpose Toolbox configuration for all entity types
 * @doc.layer core
 * @doc.pattern Registry
 */
export declare const TOOLBOX_ITEMS: ToolboxItem[];
/**
 * Creates a new entity with default properties
 * @doc.type function
 * @doc.purpose Factory function for creating physics entities
 * @doc.layer core
 * @doc.pattern Factory
 */
export declare function createEntity(type: EntityType, x: number, y: number, overrides?: Partial<PhysicsEntity>): PhysicsEntity;
/**
 * Default physics configuration for new simulations
 * @doc.type constant
 * @doc.purpose Default physics world settings
 * @doc.layer core
 * @doc.pattern Factory
 */
export declare const DEFAULT_PHYSICS_CONFIG: {
    gravity: number;
    friction: number;
    timeScale: number;
    collisionEnabled: boolean;
    airResistance: number;
    debugMode: boolean;
};
//# sourceMappingURL=defaults.d.ts.map