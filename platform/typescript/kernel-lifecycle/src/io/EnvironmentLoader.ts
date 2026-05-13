import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Environment loader
 */
export class EnvironmentLoader {
  private environmentsDir: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.environmentsDir = path.join(configDir, 'environments');
  }

  /**
   * Load environment by ID
   */
  async load(environmentId: string): Promise<Environment> {
    const envPath = path.join(this.environmentsDir, `${environmentId}.json`);
    const content = await fs.readFile(envPath, 'utf-8');
    return JSON.parse(content) as Environment;
  }

  /**
   * Load all environments
   */
  async loadAll(): Promise<Record<string, Environment>> {
    const files = await fs.readdir(this.environmentsDir);
    const environments: Record<string, Environment> = {};

    for (const file of files) {
      if (file.endsWith('.json') && file !== 'environment-schema.json') {
        const environmentId = file.replace('.json', '');
        environments[environmentId] = await this.load(environmentId);
      }
    }

    return environments;
  }

  /**
   * Get environment IDs
   */
  async getEnvironmentIds(): Promise<string[]> {
    const files = await fs.readdir(this.environmentsDir);
    return files
      .filter((f) => f.endsWith('.json') && f !== 'environment-schema.json')
      .map((f) => f.replace('.json', ''));
  }

  /**
   * Validate environment structure
   */
  async validateEnvironment(environmentId: string): Promise<ValidationError[]> {
    const errors: ValidationError[] = [];

    try {
      const env = await this.load(environmentId);

      if (!env.id) {
        errors.push({ path: 'id', message: 'Environment ID is required' });
      }

      if (!env.deploymentTarget) {
        errors.push({ path: 'deploymentTarget', message: 'Deployment target is required' });
      }

      if (!env.secretsProvider) {
        errors.push({ path: 'secretsProvider', message: 'Secrets provider is required' });
      }

      if (!env.configProvider) {
        errors.push({ path: 'configProvider', message: 'Config provider is required' });
      }

      if (!env.requiredGates || env.requiredGates.length === 0) {
        errors.push({ path: 'requiredGates', message: 'Required gates are required' });
      }

      if (!env.observabilityProfile) {
        errors.push({ path: 'observabilityProfile', message: 'Observability profile is required' });
      }

      if (!env.rollbackPolicy) {
        errors.push({ path: 'rollbackPolicy', message: 'Rollback policy is required' });
      }

      if (!env.promotionPolicy) {
        errors.push({ path: 'promotionPolicy', message: 'Promotion policy is required' });
      }
    } catch (error) {
      errors.push({
        path: 'root',
        message: `Failed to load environment: ${error instanceof Error ? error.message : String(error)}`,
      });
    }

    return errors;
  }
}

/**
 * Environment
 */
export interface Environment {
  schemaVersion: string;
  id: string;
  displayName: string;
  deploymentTarget: string;
  secretsProvider: string;
  configProvider: string;
  approvalRequired: boolean;
  requiredGates: string[];
  observabilityProfile: string;
  rollbackPolicy: string;
  promotionPolicy: string;
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
