/**
 * API Services Index
 *
 * Centralized export of all API service modules.
 *
 * @doc.type index
 * @doc.purpose Export all API services
 * @doc.layer frontend
 */

// Brain & Workspace
export * from './brain.service';
export { brainService } from './brain.service';

// Data Quality
export * from './quality.service';
export { qualityService } from './quality.service';

// Cost & Optimization
export * from './cost.service';
export { costService } from './cost.service';

// Lineage & Metadata
export * from './lineage.service';
export { lineageService } from './lineage.service';

// Governance & Policy
export * from './governance.service';
export { governanceService } from './governance.service';

// Workflow (existing)
export * from './workflow-client';

// Schema (existing)
export * from './schema.service';

// Suggestion (existing)
export * from './suggestion.service';

// Events — AEP event fabric explorer
export * from './events.service';
export { eventsService } from './events.service';

// Memory — agent memory plane viewer
export * from './memory.service';
export { memoryService } from './memory.service';

// Analytics — DC-9 SQL query engine
export * from './analytics.service';

