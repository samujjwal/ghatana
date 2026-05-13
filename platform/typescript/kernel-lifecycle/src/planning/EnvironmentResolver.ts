import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Environment resolver for lifecycle planning
 */
export class EnvironmentResolver {
  private environmentsDir: string;

  constructor(configDir: string = '/Users/samujjwal/Development/ghatana/config') {
    this.environmentsDir = path.join(configDir, 'environments');
  }

  /**
   * Resolve environment by ID
   */
  async resolve(environmentId: string): Promise<Environment> {
    const envPath = path.join(this.environmentsDir, `${environmentId}.json`);
    const env = JSON.parse(await fs.readFile(envPath, 'utf-8'));
    return env as Environment;
  }

  /**
   * Get required gates for environment
   */
  async getRequiredGates(environmentId: string): Promise<string[]> {
    const env = await this.resolve(environmentId);
    return env.requiredGates;
  }

  /**
   * Check if approval is required for environment
   */
  async isApprovalRequired(environmentId: string): Promise<boolean> {
    const env = await this.resolve(environmentId);
    return env.approvalRequired;
  }

  /**
   * Get deployment target for environment
   */
  async getDeploymentTarget(environmentId: string): Promise<string> {
    const env = await this.resolve(environmentId);
    return env.deploymentTarget;
  }

  /**
   * Get rollback policy for environment
   */
  async getRollbackPolicy(environmentId: string): Promise<string> {
    const env = await this.resolve(environmentId);
    return env.rollbackPolicy;
  }

  /**
   * Validate environment configuration
   */
  validate(environment: Environment): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!environment.id) {
      errors.push({ path: 'id', message: 'Environment ID is required' });
    }

    if (!environment.deploymentTarget) {
      errors.push({
        path: 'deploymentTarget',
        message: 'Deployment target is required',
      });
    }

    if (!environment.secretsProvider) {
      errors.push({
        path: 'secretsProvider',
        message: 'Secrets provider is required',
      });
    }

    if (!environment.configProvider) {
      errors.push({
        path: 'configProvider',
        message: 'Config provider is required',
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
