/**
 * Data Cloud Configuration Module
 *
 * Provides unified access to Data Cloud functionality with two modes:
 * - Library Mode (default for dev): In-process embedded client
 * - Service Mode (for prod): External service client
 *
 * Usage:
 * ```ts
 * import { createDataCloudClient, getDataCloudConfig } from './data-cloud-config';
 *
 * // Auto-detect mode based on environment
 * const client = createDataCloudClient();
 * await client.initialize();
 *
 * // Compute a feature
 * const feature = await client.computeFeature('agent.count', { agents: 5 });
 *
 * // Or get configuration
 * const config = getDataCloudConfig();
 * console.log(config.mode); // 'library' or 'service'
 * ```
 */

// Configuration
export {
  DataCloudMode,
  type DataCloudConfig,
  DEFAULT_DATA_CLOUD_CONFIGS,
  getDataCloudConfig,
  validateDataCloudConfig,
  formatDataCloudConfig,
  isLibraryMode,
  isServiceMode,
} from './data-cloud-mode';

// Client Factory
export {
  type Feature,
  type DataCloudClient,
  createDataCloudClient,
  getGlobalDataCloudClient,
  resetGlobalDataCloudClient,
} from './data-cloud-client-factory';
