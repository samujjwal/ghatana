import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Lifecycle profile resolver
 */
export class LifecycleProfileResolver {
  private profilesPath: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.profilesPath = path.join(configDir, 'product-lifecycle-profiles.json');
  }

  /**
   * Resolve lifecycle profile by ID
   */
  async resolve(profileId: string): Promise<LifecycleProfile> {
    const profiles = JSON.parse(await fs.readFile(this.profilesPath, 'utf-8'));
    const profile = profiles.profiles[profileId];

    if (!profile) {
      throw new Error(`Lifecycle profile ${profileId} not found`);
    }

    return profile as LifecycleProfile;
  }

  /**
   * Get phase configuration from profile
   */
  async getPhaseConfig(
    profileId: string,
    phase: string,
  ): Promise<PhaseConfiguration> {
    const profile = await this.resolve(profileId);
    const phaseConfig = profile.defaultSurfaces[phase];

    if (!phaseConfig) {
      throw new Error(`Phase ${phase} not defined in profile ${profileId}`);
    }

    return {
      defaultSurfaces: phaseConfig,
      mode: 'sequential',
    };
  }

  /**
   * Get required gates for a phase
   */
  async getRequiredGates(profileId: string, phase: string): Promise<string[]> {
    const profile = await this.resolve(profileId);
    return profile.requiredGates[phase] || [];
  }

  /**
   * Get optional gates for a phase
   */
  async getOptionalGates(profileId: string, phase: string): Promise<string[]> {
    const profile = await this.resolve(profileId);
    return profile.optionalGates[phase] || [];
  }

  /**
   * Get default adapter for a surface and phase
   */
  async getDefaultAdapter(
    profileId: string,
    surface: string,
    phase: string,
  ): Promise<string | undefined> {
    const profile = await this.resolve(profileId);
    const adapterKey = `${phase}.${surface}`;
    return profile.defaultAdapters[adapterKey];
  }
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
 * Phase configuration
 */
export interface PhaseConfiguration {
  defaultSurfaces: string[];
  mode: 'parallel' | 'sequential';
}
