import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Lifecycle profile loader
 */
export class LifecycleProfileLoader {
  private profilesPath: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.profilesPath = path.join(configDir, 'product-lifecycle-profiles.json');
  }

  /**
   * Load lifecycle profiles
   */
  async load(): Promise<LifecycleProfiles> {
    const content = await fs.readFile(this.profilesPath, 'utf-8');
    return JSON.parse(content) as LifecycleProfiles;
  }

  /**
   * Get profile by ID
   */
  async getProfile(profileId: string): Promise<LifecycleProfile> {
    const profiles = await this.load();
    const profile = profiles.profiles[profileId];

    if (!profile) {
      throw new Error(`Lifecycle profile ${profileId} not found`);
    }

    return profile;
  }

  /**
   * Get all profile IDs
   */
  async getProfileIds(): Promise<string[]> {
    const profiles = await this.load();
    return Object.keys(profiles.profiles);
  }

  /**
   * Validate profile structure
   */
  async validateProfile(profileId: string): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];

    try {
      const profile = await this.getProfile(profileId);

      if (!profile.description) {
        errors.push({ path: 'description', message: 'Description is required' });
      }

      if (!profile.defaultSurfaces) {
        errors.push({ path: 'defaultSurfaces', message: 'Default surfaces are required' });
      }

      if (!profile.requiredGates) {
        errors.push({ path: 'requiredGates', message: 'Required gates are required' });
      }

      if (!profile.optionalGates) {
        errors.push({ path: 'optionalGates', message: 'Optional gates are required' });
      }

      if (!profile.defaultAdapters) {
        errors.push({ path: 'defaultAdapters', message: 'Default adapters are required' });
      }
    } catch (error) {
      errors.push({
        path: 'root',
        message: `Failed to load profile: ${error instanceof Error ? error.message : String(error)}`,
      });
    }

    return errors;
  }
}

/**
 * Lifecycle profiles
 */
export interface LifecycleProfiles {
  version: string;
  profiles: Record<string, LifecycleProfile>;
}

/**
 * Lifecycle profile
 */
export interface LifecycleProfile {
  description: string;
  defaultSurfaces: Record<string, string[]>;
  requiredGates: Record<string, string[]>;
  optionalGates: Record<string, string[]>;
  defaultAdapters: Record<string, string>;
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
