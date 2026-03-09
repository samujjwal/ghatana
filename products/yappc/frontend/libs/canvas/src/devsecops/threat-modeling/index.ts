/**
 * Threat Modeling Module
 * 
 * STRIDE/LINDDUN threat modeling automation for security analysis.
 * 
 * @module threat-modeling
 * 
 * @example
 * ```typescript
 * import {
 *   createThreatModel,
 *   addElement,
 *   addFlow,
 *   analyzeThreatModel,
 *   exportThreatModel,
 * } from '@ghatana/yappc-canvas/devsecops/threat-modeling';
 * 
 * // Create model
 * const model = createThreatModel({ enableSTRIDE: true });
 * 
 * // Add elements
 * const withElement = addElement(model, {
 *   id: 'web-server',
 *   type: 'process',
 *   name: 'Web Server',
 *   trustZone: 'dmz',
 * });
 * 
 * // Analyze
 * const analysis = analyzeThreatModel(withElement);
 * console.log(`Found ${analysis.newThreats.length} threats`);
 * 
 * // Export
 * const report = exportThreatModel(withElement, 'markdown');
 * ```
 */

// Types
export type {
  STRIDECategory,
  LINDDUNCategory,
  ThreatCategory,
  ThreatSeverity,
  ThreatStatus,
  TrustZone,
  ThreatElementType,
  TrustBoundary,
  ThreatElement,
  ThreatFlow,
  ThreatMitigation,
  Threat,
  ThreatModelConfig,
  ThreatCatalogEntry,
  ThreatModelState,
  ThreatAnalysisResult,
  ThreatExportFormat,
} from './types';

// State Management
export {
  createThreatModelConfig,
  createThreatModel,
  addElement,
  addFlow,
  addBoundary,
  addThreat,
  updateThreatStatus,
  addMitigation,
  updateMitigationStatus,
  getThreatsBySeverity,
  getThreatsByStatus,
  getThreatsByCategory,
  calculateThreatScore,
} from './state';

// Analysis
export {
  analyzeThreatModel,
} from './analyzer';

// Catalogs
export {
  getSTRIDEThreatCatalog,
} from './catalogs/stride';

export {
  getLINDDUNThreatCatalog,
} from './catalogs/linddun';

// Export
export {
  exportThreatModel,
} from './export';
