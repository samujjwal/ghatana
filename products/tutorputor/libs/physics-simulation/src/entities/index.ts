/**
 * @doc.type module
 * @doc.purpose Entity management exports
 * @doc.layer core
 * @doc.pattern Barrel
 */

export {
    DEFAULT_PHYSICS,
    ENTITY_DEFAULTS,
    TOOLBOX_ITEMS,
    DEFAULT_PHYSICS_CONFIG,
    createEntity,
} from './defaults';

export {
    physicsPropertiesSchema,
    entityAppearanceSchema,
    physicsEntitySchema,
    physicsConfigSchema,
    validateEntity,
    validatePhysicsConfig,
    isValidEntityType,
} from './validators';
