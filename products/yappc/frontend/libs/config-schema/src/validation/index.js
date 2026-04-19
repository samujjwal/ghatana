/**
 * Validation utilities
 *
 * Reuses SchemaRegistry pattern from existing YAPPC implementation.
 * Provides validation functions for PageConfig, IntentConfig, and RequirementConfig.
 *
 * @packageDocumentation
 */
import { z } from 'zod';
import { PageConfigSchema, IntentConfigSchema, RequirementConfigSchema, } from '../schemas';
/**
 * Validate PageConfig
 */
export function validatePageConfig(data) {
    try {
        PageConfigSchema.parse(data);
        return { valid: true, errors: [] };
    }
    catch (error) {
        if (error instanceof z.ZodError) {
            return {
                valid: false,
                errors: error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`),
            };
        }
        return {
            valid: false,
            errors: ['Unknown validation error'],
        };
    }
}
/**
 * Validate IntentConfig
 */
export function validateIntentConfig(data) {
    try {
        IntentConfigSchema.parse(data);
        return { valid: true, errors: [] };
    }
    catch (error) {
        if (error instanceof z.ZodError) {
            return {
                valid: false,
                errors: error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`),
            };
        }
        return {
            valid: false,
            errors: ['Unknown validation error'],
        };
    }
}
/**
 * Validate RequirementConfig
 */
export function validateRequirementConfig(data) {
    try {
        RequirementConfigSchema.parse(data);
        return { valid: true, errors: [] };
    }
    catch (error) {
        if (error instanceof z.ZodError) {
            return {
                valid: false,
                errors: error.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`),
            };
        }
        return {
            valid: false,
            errors: ['Unknown validation error'],
        };
    }
}
