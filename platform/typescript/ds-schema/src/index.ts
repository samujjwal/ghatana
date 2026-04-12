/**
 * @fileoverview @ghatana/ds-schema - DTCG-aligned design system schemas.
 *
 * @doc.type package
 * @doc.purpose Provides DTCG-compliant token schemas, component contracts,
 *   theme definitions, pattern schemas, and validation utilities.
 * @doc.layer platform
 * @doc.dependency zod
 */

// Tokens
export * from './tokens/index';

// Components
export * from './components/index';

// Re-export zod for convenience
export { z } from 'zod';
