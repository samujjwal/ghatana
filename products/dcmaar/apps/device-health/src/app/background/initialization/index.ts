/**
 * @fileoverview Initialization Module - Exports
 *
 * Central export point for extension initialization components:
 * - ConfigLoader: Loads and validates configuration
 * - Orchestrator: Orchestrates complete startup sequence
 */

export { ConfigLoader } from './ConfigLoader';
export type { ConfigLoadingResult } from './ConfigLoader';

export { Orchestrator } from './Orchestrator';
export type { InitializationResult } from './Orchestrator';
