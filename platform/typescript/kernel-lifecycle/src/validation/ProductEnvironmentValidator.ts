import { ProductEnvironment } from '../domain/ProductLifecyclePhase.js';

/**
 * Product environment validator
 */
export class ProductEnvironmentValidator {
  /**
   * Validate environment configuration
   */
  validate(environment: ProductEnvironment): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!environment.id || environment.id.trim().length === 0) {
      errors.push({ path: 'id', message: 'Environment ID is required' });
    } else {
      this.validateEnvironmentId(environment.id, errors);
    }

    if (!environment.displayName || environment.displayName.trim().length === 0) {
      errors.push({ path: 'displayName', message: 'Display name is required' });
    }

    if (!environment.deploymentTarget || environment.deploymentTarget.trim().length === 0) {
      errors.push({ path: 'deploymentTarget', message: 'Deployment target is required' });
    }

    if (!environment.secretsProvider || environment.secretsProvider.trim().length === 0) {
      errors.push({ path: 'secretsProvider', message: 'Secrets provider is required' });
    }

    if (!environment.configProvider || environment.configProvider.trim().length === 0) {
      errors.push({ path: 'configProvider', message: 'Config provider is required' });
    }

    if (typeof environment.approvalRequired !== 'boolean') {
      errors.push({ path: 'approvalRequired', message: 'Approval required must be a boolean' });
    }

    if (!environment.requiredGates || !Array.isArray(environment.requiredGates)) {
      errors.push({ path: 'requiredGates', message: 'Required gates must be an array' });
    }

    if (!environment.observabilityProfile || environment.observabilityProfile.trim().length === 0) {
      errors.push({ path: 'observabilityProfile', message: 'Observability profile is required' });
    }

    if (!environment.rollbackPolicy || environment.rollbackPolicy.trim().length === 0) {
      errors.push({ path: 'rollbackPolicy', message: 'Rollback policy is required' });
    }

    if (!environment.promotionPolicy || environment.promotionPolicy.trim().length === 0) {
      errors.push({ path: 'promotionPolicy', message: 'Promotion policy is required' });
    }

    return errors;
  }

  /**
   * Validate environment ID
   */
  private validateEnvironmentId(id: string, errors: ValidationError[]): void {
    const validIds = ['local', 'dev', 'staging', 'prod'];

    if (!validIds.includes(id)) {
      errors.push({
        path: 'id',
        message: `Invalid environment ID: ${id}. Must be one of: ${validIds.join(', ')}`,
      });
    }
  }

  /**
   * Validate environment supports required gates
   */
  validateGates(environment: ProductEnvironment, requiredGates: string[]): ValidationError[] {
    const errors: ValidationError[] = [];

    for (const gate of requiredGates) {
      if (!environment.requiredGates.includes(gate)) {
        errors.push({
          path: 'requiredGates',
          message: `Required gate ${gate} is not configured in environment ${environment.id}`,
        });
      }
    }

    return errors;
  }
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
