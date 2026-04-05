/**
 * Shared Persona Types
 * 
 * Single source of truth for persona type definitions.
 * These are dynamically loaded from backend, not hardcoded.
 * 
 * @doc.type type
 * @doc.purpose Shared persona type definitions
 * @doc.layer product
 */

/**
 * Dynamic persona type - loaded from backend
 * No longer a hardcoded union type
 */
export type PersonaType = string;

/**
 * Persona category for grouping
 */
export type PersonaCategory =
    | 'TECHNICAL'
    | 'MANAGEMENT'
    | 'GOVERNANCE'
    | 'ANALYSIS';
