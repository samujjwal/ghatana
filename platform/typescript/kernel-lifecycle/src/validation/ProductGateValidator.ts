import { ProductGate, ProductGatePlan, ProductGateResult } from '../domain/ProductLifecyclePhase.js';

/**
 * Product gate validator
 */
export class ProductGateValidator {
  /**
   * Validate gate
   */
  validate(gate: ProductGate): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!gate.id || gate.id.trim().length === 0) {
      errors.push({ path: 'id', message: 'Gate ID is required' });
    }

    if (!gate.name || gate.name.trim().length === 0) {
      errors.push({ path: 'name', message: 'Gate name is required' });
    }

    if (!gate.description || gate.description.trim().length === 0) {
      errors.push({ path: 'description', message: 'Gate description is required' });
    }

    if (typeof gate.required !== 'boolean') {
      errors.push({ path: 'required', message: 'Required must be a boolean' });
    }

    if (!gate.phase) {
      errors.push({ path: 'phase', message: 'Gate phase is required' });
    }

    if (!gate.implementation || gate.implementation.trim().length === 0) {
      errors.push({ path: 'implementation', message: 'Gate implementation is required' });
    }

    return errors;
  }

  /**
   * Validate gate plan
   */
  validateGatePlan(plan: ProductGatePlan): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!plan.gateId || plan.gateId.trim().length === 0) {
      errors.push({ path: 'gateId', message: 'Gate ID is required' });
    }

    if (!plan.gateName || plan.gateName.trim().length === 0) {
      errors.push({ path: 'gateName', message: 'Gate name is required' });
    }

    if (typeof plan.required !== 'boolean') {
      errors.push({ path: 'required', message: 'Required must be a boolean' });
    }

    if (!plan.phase) {
      errors.push({ path: 'phase', message: 'Phase is required' });
    }

    if (!plan.status) {
      errors.push({ path: 'status', message: 'Status is required' });
    } else {
      this.validateGateStatus(plan.status, errors);
    }

    return errors;
  }

  /**
   * Validate gate result
   */
  validateGateResult(result: ProductGateResult): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!result.gateId || result.gateId.trim().length === 0) {
      errors.push({ path: 'gateId', message: 'Gate ID is required' });
    }

    if (!result.gateName || result.gateName.trim().length === 0) {
      errors.push({ path: 'gateName', message: 'Gate name is required' });
    }

    if (!result.status) {
      errors.push({ path: 'status', message: 'Status is required' });
    } else {
      this.validateGateStatus(result.status, errors);
    }

    if (!result.checkedAt) {
      errors.push({ path: 'checkedAt', message: 'Checked at timestamp is required' });
    }

    return errors;
  }

  /**
   * Validate gate status
   */
  private validateGateStatus(status: string, errors: ValidationError[]): void {
    const validStatuses = ['pending', 'passed', 'failed', 'skipped'];

    if (!validStatuses.includes(status)) {
      errors.push({
        path: 'status',
        message: `Invalid gate status: ${status}. Must be one of: ${validStatuses.join(', ')}`,
      });
    }
  }

  /**
   * Validate gate is configured for phase
   */
  validateGateForPhase(
    gateId: string,
    phase: string,
    configuredGates: string[],
  ): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!configuredGates.includes(gateId)) {
      errors.push({
        path: 'gateId',
        message: `Gate ${gateId} is not configured for phase ${phase}`,
      });
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
