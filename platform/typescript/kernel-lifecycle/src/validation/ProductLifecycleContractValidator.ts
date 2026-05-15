import { ProductLifecyclePlan, ProductLifecycleResult } from '../domain/ProductLifecyclePhase.js';

/**
 * Product lifecycle contract validator
 */
export class ProductLifecycleContractValidator {
  /**
   * Validate lifecycle plan
   */
  validatePlan(plan: ProductLifecyclePlan): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!plan.productId || plan.productId.trim().length === 0) {
      errors.push({ path: 'productId', message: 'Product ID is required' });
    }

    if (!plan.runId || plan.runId.trim().length === 0) {
      errors.push({ path: 'runId', message: 'Run ID is required' });
    }

    if (!plan.correlationId || plan.correlationId.trim().length === 0) {
      errors.push({ path: 'correlationId', message: 'Correlation ID is required' });
    }

    if (plan.providerMode !== 'bootstrap' && plan.providerMode !== 'platform') {
      errors.push({ path: 'providerMode', message: 'Provider mode must be bootstrap or platform' });
    }

    if (!plan.phase) {
      errors.push({ path: 'phase', message: 'Phase is required' });
    }

    if (!plan.lifecycleProfile || plan.lifecycleProfile.trim().length === 0) {
      errors.push({ path: 'lifecycleProfile', message: 'Lifecycle profile is required' });
    }

    if (!plan.surfaces || plan.surfaces.length === 0) {
      errors.push({ path: 'surfaces', message: 'At least one surface is required' });
    }

    if (!plan.steps || plan.steps.length === 0) {
      errors.push({ path: 'steps', message: 'At least one step is required' });
    }

    if (!plan.outputDirectory || plan.outputDirectory.trim().length === 0) {
      errors.push({ path: 'outputDirectory', message: 'Output directory is required' });
    }

    if (plan.estimatedDurationMs < 0) {
      errors.push({ path: 'estimatedDurationMs', message: 'Estimated duration must be non-negative' });
    }

    if (!Array.isArray(plan.requiredManifests)) {
      errors.push({ path: 'requiredManifests', message: 'Required manifests must be an array' });
    }

    if (!Array.isArray(plan.requiredPlugins)) {
      errors.push({ path: 'requiredPlugins', message: 'Required plugins must be an array' });
    }

    if (!Array.isArray(plan.approvalRequirements)) {
      errors.push({ path: 'approvalRequirements', message: 'Approval requirements must be an array' });
    }

    return errors;
  }

  /**
   * Validate lifecycle result
   */
  validateResult(result: ProductLifecycleResult): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!result.productId || result.productId.trim().length === 0) {
      errors.push({ path: 'productId', message: 'Product ID is required' });
    }

    if (!result.phase) {
      errors.push({ path: 'phase', message: 'Phase is required' });
    }

    if (!result.status) {
      errors.push({ path: 'status', message: 'Status is required' });
    }

    if (!result.startedAt) {
      errors.push({ path: 'startedAt', message: 'Started at timestamp is required' });
    }

    if (!result.completedAt) {
      errors.push({ path: 'completedAt', message: 'Completed at timestamp is required' });
    }

    if (!result.steps || result.steps.length === 0) {
      errors.push({ path: 'steps', message: 'At least one step is required' });
    }

    if (!result.outputDirectory || result.outputDirectory.trim().length === 0) {
      errors.push({ path: 'outputDirectory', message: 'Output directory is required' });
    }

    // Validate that failure details are present when status is failed
    if (result.status === 'failed' && !result.failure) {
      errors.push({ path: 'failure', message: 'Failure details are required when status is failed' });
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
