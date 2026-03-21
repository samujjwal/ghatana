import { z } from "zod";
import { EntityType } from "../types";
/**
 * @doc.type schema
 * @doc.purpose Zod schema for physics properties validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export declare const physicsPropertiesSchema: z.ZodObject<{
    mass: z.ZodDefault<z.ZodNumber>;
    friction: z.ZodDefault<z.ZodNumber>;
    restitution: z.ZodDefault<z.ZodNumber>;
    isStatic: z.ZodDefault<z.ZodBoolean>;
    angularVelocity: z.ZodOptional<z.ZodNumber>;
    velocity: z.ZodOptional<z.ZodObject<{
        x: z.ZodNumber;
        y: z.ZodNumber;
    }, z.core.$strip>>;
}, z.core.$strip>;
/**
 * @doc.type schema
 * @doc.purpose Zod schema for entity appearance validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export declare const entityAppearanceSchema: z.ZodObject<{
    color: z.ZodString;
    strokeColor: z.ZodOptional<z.ZodString>;
    strokeWidth: z.ZodOptional<z.ZodNumber>;
    opacity: z.ZodOptional<z.ZodNumber>;
    shadowBlur: z.ZodOptional<z.ZodNumber>;
    shadowColor: z.ZodOptional<z.ZodString>;
}, z.core.$strip>;
/**
 * @doc.type schema
 * @doc.purpose Zod schema for physics entity validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export declare const physicsEntitySchema: z.ZodObject<{
    id: z.ZodString;
    type: z.ZodEnum<typeof EntityType>;
    x: z.ZodNumber;
    y: z.ZodNumber;
    width: z.ZodOptional<z.ZodNumber>;
    height: z.ZodOptional<z.ZodNumber>;
    radius: z.ZodOptional<z.ZodNumber>;
    rotation: z.ZodOptional<z.ZodNumber>;
    appearance: z.ZodObject<{
        color: z.ZodString;
        strokeColor: z.ZodOptional<z.ZodString>;
        strokeWidth: z.ZodOptional<z.ZodNumber>;
        opacity: z.ZodOptional<z.ZodNumber>;
        shadowBlur: z.ZodOptional<z.ZodNumber>;
        shadowColor: z.ZodOptional<z.ZodString>;
    }, z.core.$strip>;
    physics: z.ZodObject<{
        mass: z.ZodDefault<z.ZodNumber>;
        friction: z.ZodDefault<z.ZodNumber>;
        restitution: z.ZodDefault<z.ZodNumber>;
        isStatic: z.ZodDefault<z.ZodBoolean>;
        angularVelocity: z.ZodOptional<z.ZodNumber>;
        velocity: z.ZodOptional<z.ZodObject<{
            x: z.ZodNumber;
            y: z.ZodNumber;
        }, z.core.$strip>>;
    }, z.core.$strip>;
    metadata: z.ZodOptional<z.ZodRecord<z.ZodString, z.ZodUnknown>>;
}, z.core.$strip>;
/**
 * @doc.type schema
 * @doc.purpose Zod schema for physics config validation
 * @doc.layer core
 * @doc.pattern Validator
 */
export declare const physicsConfigSchema: z.ZodObject<{
    gravity: z.ZodDefault<z.ZodNumber>;
    friction: z.ZodDefault<z.ZodNumber>;
    timeScale: z.ZodDefault<z.ZodNumber>;
    collisionEnabled: z.ZodDefault<z.ZodBoolean>;
    airResistance: z.ZodOptional<z.ZodNumber>;
    debugMode: z.ZodOptional<z.ZodBoolean>;
}, z.core.$strip>;
/**
 * Validates a physics entity
 * @doc.type function
 * @doc.purpose Entity validation utility
 * @doc.layer core
 * @doc.pattern Validator
 */
export declare function validateEntity(entity: unknown): {
    success: boolean;
    data?: z.infer<typeof physicsEntitySchema>;
    errors?: z.ZodError;
};
/**
 * Validates physics configuration
 * @doc.type function
 * @doc.purpose Config validation utility
 * @doc.layer core
 * @doc.pattern Validator
 */
export declare function validatePhysicsConfig(config: unknown): {
    success: boolean;
    data?: z.infer<typeof physicsConfigSchema>;
    errors?: z.ZodError;
};
/**
 * Type guard for EntityType
 * @doc.type function
 * @doc.purpose Type guard utility
 * @doc.layer core
 * @doc.pattern Guard
 */
export declare function isValidEntityType(type: string): type is EntityType;
//# sourceMappingURL=validators.d.ts.map