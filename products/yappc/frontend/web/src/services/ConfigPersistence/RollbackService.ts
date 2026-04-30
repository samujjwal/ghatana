/**
 * Rollback Service
 *
 * Rollback functionality for config versions.
 *
 * @packageDocumentation
 */

import type { PageConfig } from 'yappc-config-schema';

import { ConfigStorage } from './ConfigStorage';

/**
 * @doc.type service
 * @doc.purpose Rollback functionality for config versions
 * @doc.layer product
 * @doc.pattern Service
 */
export class RollbackService {
  private readonly storage: ConfigStorage;

  constructor() {
    this.storage = new ConfigStorage();
  }

  /**
   * Rollback a config to a specific version.
   *
   * @param configId - Config ID
   * @param version - Target version
   * @returns Rolled back config
   */
  async rollback(configId: string, version: string): Promise<PageConfig | null> {
    const config = await this.storage.load(configId, version);

    if (!config) {
      return null;
    }

    // Save as new version after rollback
    await this.storage.save(config, `${version}-rollback`);

    return config;
  }

  /**
   * Get available rollback versions for a config.
   *
   * @param configId - Config ID
   * @returns Array of available versions
   */
  async getAvailableVersions(configId: string): Promise<Array<{ version: string; timestamp: string }>> {
    return this.storage.getVersions(configId);
  }

  /**
   * Rollback to the previous version.
   *
   * @param configId - Config ID
   * @returns Rolled back config
   */
  async rollbackToPrevious(configId: string): Promise<PageConfig | null> {
    const versions = await this.storage.getVersions(configId);

    if (versions.length < 2) {
      return null;
    }

    const previousVersion = versions[versions.length - 2];
    return this.rollback(configId, previousVersion.version);
  }
}
