/**
 * AEP Configuration & Client Factory
 *
 * Provides unified interface for YAPPC to use AEP in either mode:
 * - Library mode (dev default)
 * - Service mode (production)
 *
 * @module aep-config
 */

export {
  AepMode,
  AepConfig,
  DEFAULT_AEP_CONFIGS,
  getAepConfig,
  validateAepConfig,
  formatAepConfig,
  isLibraryMode,
  isServiceMode,
} from './aep-mode';

export {
  AepClient,
  createAepClient,
  getGlobalAepClient,
  resetGlobalAepClient,
} from './aep-client-factory';
