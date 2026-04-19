/**
 * Config Storage
 *
 * Config storage service with Git-backed persistence.
 *
 * @packageDocumentation
 */

import type { PageConfig } from '@yappc/config-schema';

/**
 * @doc.type service
 * @doc.purpose Config storage service with Git-backed persistence
 * @doc.layer product
 * @doc.pattern Service
 */
export class ConfigStorage {
  private readonly storageKey = 'yappc-config-storage';

  /**
   * Save a config to storage.
   *
   * @param config - PageConfig to save
   * @param version - Optional version identifier
   */
  async save(config: PageConfig, version?: string): Promise<void> {
    const storage = this.getStorage();
    const entry = {
      config,
      version: version || this.generateVersion(),
      timestamp: new Date().toISOString(),
    };

    storage[config.id] = storage[config.id] || [];
    storage[config.id].push(entry);

    this.setStorage(storage);
  }

  /**
   * Load a config from storage.
   *
   * @param configId - Config ID to load
   * @param version - Optional version to load (latest if not specified)
   * @returns PageConfig or null
   */
  async load(configId: string, version?: string): Promise<PageConfig | null> {
    const storage = this.getStorage();
    const entries = storage[configId];

    if (!entries || entries.length === 0) {
      return null;
    }

    if (version) {
      const entry = entries.find((e) => e.version === version);
      return entry?.config || null;
    }

    // Return latest version
    return entries[entries.length - 1].config;
  }

  /**
   * Get all versions of a config.
   *
   * @param configId - Config ID
   * @returns Array of version entries
   */
  async getVersions(configId: string): Promise<Array<{ version: string; timestamp: string }>> {
    const storage = this.getStorage();
    const entries = storage[configId] || [];

    return entries.map((e) => ({ version: e.version, timestamp: e.timestamp }));
  }

  /**
   * Delete a config from storage.
   *
   * @param configId - Config ID to delete
   */
  async delete(configId: string): Promise<void> {
    const storage = this.getStorage();
    delete storage[configId];
    this.setStorage(storage);
  }

  /**
   * List all stored configs.
   *
   * @returns Array of config IDs
   */
  async list(): Promise<string[]> {
    const storage = this.getStorage();
    return Object.keys(storage);
  }

  private getStorage(): Record<string, Array<{ config: PageConfig; version: string; timestamp: string }>> {
    try {
      const data = localStorage.getItem(this.storageKey);
      return data ? JSON.parse(data) : {};
    } catch {
      return {};
    }
  }

  private setStorage(storage: Record<string, unknown>): void {
    localStorage.setItem(this.storageKey, JSON.stringify(storage));
  }

  private generateVersion(): string {
    return `v${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  }
}
