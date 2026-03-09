/**
 * NL Service - Module exports.
 *
 * @doc.type module
 * @doc.purpose Barrel exports for sim-nl service
 * @doc.layer product
 * @doc.pattern Module
 */

// Main service
export { NLService, createNLService } from './service';
export type { NLRefinementResponse } from './service';

// Intent parser
export { NLIntentParser, ParsedIntentSchema } from './intent-parser';
export type { ParsedIntent, IntentType, IntentParams, ValidatedIntent } from './intent-parser';

// Refinement engine
export { RefinementEngine } from './refinement-engine';
export type { RefinementResult } from './refinement-engine';
