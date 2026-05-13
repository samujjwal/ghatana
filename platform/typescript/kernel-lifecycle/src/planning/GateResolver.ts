import { ProductLifecyclePhase } from '../domain/ProductLifecyclePhase.js';

/**
 * Gate resolver for lifecycle planning
 */
export class GateResolver {
  /**
   * Resolve gates for a phase
   */
  resolve(
    phase: ProductLifecyclePhase,
    requiredGates: string[],
    optionalGates: string[],
  ): GatePlan[] {
    const gates: GatePlan[] = [];

    for (const gateId of requiredGates) {
      gates.push({
        gateId,
        gateName: gateId,
        required: true,
        phase,
        status: 'pending',
      });
    }

    for (const gateId of optionalGates) {
      gates.push({
        gateId,
        gateName: gateId,
        required: false,
        phase,
        status: 'pending',
      });
    }

    return gates;
  }

  /**
   * Validate gate configuration
   */
  validate(gateId: string): ValidationError[] {
    const errors: ValidationError[] = [];

    if (!gateId || gateId.trim().length === 0) {
      errors.push({ path: 'gateId', message: 'Gate ID is required' });
    }

    return errors;
  }

  /**
   * Check if gate is required for environment
   */
  isGateRequiredForEnvironment(
    gateId: string,
    environment: string,
  ): boolean {
    const productionGates = [
      'security',
      'privacy',
      'license-policy',
      'conformance',
      'e2e',
      'performance',
      'rollback-plan',
      'approval',
    ];

    if (environment === 'prod') {
      return productionGates.includes(gateId);
    }

    return false;
  }
}

/**
 * Gate plan
 */
export interface GatePlan {
  gateId: string;
  gateName: string;
  required: boolean;
  phase: ProductLifecyclePhase;
  status: 'pending' | 'passed' | 'failed' | 'skipped';
}

/**
 * Validation error
 */
export interface ValidationError {
  path: string;
  message: string;
}
