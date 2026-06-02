/**
 * K-004: Kernel PHI Policy Facade with product policy hooks.
 * 
 * Provides a facade for PHI policy evaluation with hooks for product-specific
 * policy implementations. Products can register policy evaluators for different
 * policy types (consent, treatment-relationship, facility-scope, emergency, fchv-community).
 *
 * @doc.type class
 * @doc.purpose Provides PHI policy evaluation facade with product policy hooks
 * @doc.layer platform
 * @doc.pattern Facade
 */

import type {
  ProductPolicyEvaluationRequest,
  ProductPolicyEvaluationResult,
  PolicyDecision,
  PolicyReasonCode,
  PolicyRequirement,
} from './ProductPolicyContract';

/**
 * Policy evaluator function type for product-specific policy evaluation
 */
export type PolicyEvaluator = (
  request: ProductPolicyEvaluationRequest
) => Promise<ProductPolicyEvaluationResult>;

/**
 * Policy hook registration options
 */
export interface PolicyHookOptions {
  readonly priority?: number;
  readonly enabled?: boolean;
}

/**
 * Policy hook registration
 */
export interface PolicyHookRegistration {
  readonly evaluator: PolicyEvaluator;
  readonly options: PolicyHookOptions;
}

/**
 * Kernel PHI Policy Facade
 * 
 * Provides a centralized facade for PHI policy evaluation with hooks for
 * product-specific policy implementations.
 */
export class KernelPhiPolicyFacade {
  private consentHooks: Map<string, PolicyHookRegistration> = new Map();
  private treatmentRelationshipHooks: Map<string, PolicyHookRegistration> = new Map();
  private facilityScopeHooks: Map<string, PolicyHookRegistration> = new Map();
  private emergencyHooks: Map<string, PolicyHookRegistration> = new Map();
  private fchvCommunityHooks: Map<string, PolicyHookRegistration> = new Map();

  /**
   * Register a consent policy evaluator hook
   */
  registerConsentEvaluator(
    id: string,
    evaluator: PolicyEvaluator,
    options: PolicyHookOptions = {}
  ): void {
    this.consentHooks.set(id, { evaluator, options: { ...options, enabled: options.enabled ?? true } });
  }

  /**
   * Register a treatment relationship policy evaluator hook
   */
  registerTreatmentRelationshipEvaluator(
    id: string,
    evaluator: PolicyEvaluator,
    options: PolicyHookOptions = {}
  ): void {
    this.treatmentRelationshipHooks.set(id, { evaluator, options: { ...options, enabled: options.enabled ?? true } });
  }

  /**
   * Register a facility scope policy evaluator hook
   */
  registerFacilityScopeEvaluator(
    id: string,
    evaluator: PolicyEvaluator,
    options: PolicyHookOptions = {}
  ): void {
    this.facilityScopeHooks.set(id, { evaluator, options: { ...options, enabled: options.enabled ?? true } });
  }

  /**
   * Register an emergency policy evaluator hook
   */
  registerEmergencyEvaluator(
    id: string,
    evaluator: PolicyEvaluator,
    options: PolicyHookOptions = {}
  ): void {
    this.emergencyHooks.set(id, { evaluator, options: { ...options, enabled: options.enabled ?? true } });
  }

  /**
   * Register an FCHV community policy evaluator hook
   */
  registerFchvCommunityEvaluator(
    id: string,
    evaluator: PolicyEvaluator,
    options: PolicyHookOptions = {}
  ): void {
    this.fchvCommunityHooks.set(id, { evaluator, options: { ...options, enabled: options.enabled ?? true } });
  }

  /**
   * Unregister a consent policy evaluator hook
   */
  unregisterConsentEvaluator(id: string): void {
    this.consentHooks.delete(id);
  }

  /**
   * Unregister a treatment relationship policy evaluator hook
   */
  unregisterTreatmentRelationshipEvaluator(id: string): void {
    this.treatmentRelationshipHooks.delete(id);
  }

  /**
   * Unregister a facility scope policy evaluator hook
   */
  unregisterFacilityScopeEvaluator(id: string): void {
    this.facilityScopeHooks.delete(id);
  }

  /**
   * Unregister an emergency policy evaluator hook
   */
  unregisterEmergencyEvaluator(id: string): void {
    this.emergencyHooks.delete(id);
  }

  /**
   * Unregister an FCHV community policy evaluator hook
   */
  unregisterFchvCommunityEvaluator(id: string): void {
    this.fchvCommunityHooks.delete(id);
  }

  /**
   * Evaluate PHI policy for a given request
   * 
   * This method orchestrates policy evaluation by calling registered hooks
   * in priority order and combining results.
   */
  async evaluatePolicy(request: ProductPolicyEvaluationRequest): Promise<ProductPolicyEvaluationResult> {
    // Evaluate all registered hooks in priority order
    const results: ProductPolicyEvaluationResult[] = [];

    // Evaluate consent hooks
    for (const [id, hook] of this.getSortedHooks(this.consentHooks)) {
      if (hook.options.enabled) {
        try {
          const result = await hook.evaluator(request);
          results.push(result);
        } catch (error) {
          console.error(`Consent policy evaluator ${id} failed:`, error);
        }
      }
    }

    // Evaluate treatment relationship hooks
    for (const [id, hook] of this.getSortedHooks(this.treatmentRelationshipHooks)) {
      if (hook.options.enabled) {
        try {
          const result = await hook.evaluator(request);
          results.push(result);
        } catch (error) {
          console.error(`Treatment relationship policy evaluator ${id} failed:`, error);
        }
      }
    }

    // Evaluate facility scope hooks
    for (const [id, hook] of this.getSortedHooks(this.facilityScopeHooks)) {
      if (hook.options.enabled) {
        try {
          const result = await hook.evaluator(request);
          results.push(result);
        } catch (error) {
          console.error(`Facility scope policy evaluator ${id} failed:`, error);
        }
      }
    }

    // Evaluate emergency hooks
    for (const [id, hook] of this.getSortedHooks(this.emergencyHooks)) {
      if (hook.options.enabled) {
        try {
          const result = await hook.evaluator(request);
          results.push(result);
        } catch (error) {
          console.error(`Emergency policy evaluator ${id} failed:`, error);
        }
      }
    }

    // Evaluate FCHV community hooks
    for (const [id, hook] of this.getSortedHooks(this.fchvCommunityHooks)) {
      if (hook.options.enabled) {
        try {
          const result = await hook.evaluator(request);
          results.push(result);
        } catch (error) {
          console.error(`FCHV community policy evaluator ${id} failed:`, error);
        }
      }
    }

    // Combine results: deny if any hook denies, otherwise allow
    return this.combineResults(results);
  }

  /**
   * Get hooks sorted by priority (higher priority first)
   */
  private getSortedHooks(
    hooks: Map<string, PolicyHookRegistration>
  ): [string, PolicyHookRegistration][] {
    return Array.from(hooks.entries()).sort(
      ([, a], [, b]) => (b.options.priority ?? 0) - (a.options.priority ?? 0)
    );
  }

  /**
   * Combine multiple policy evaluation results
   * 
   * Returns denied if any result is denied, otherwise returns allowed.
   * Combines requirements from all results.
   */
  private combineResults(results: ProductPolicyEvaluationResult[]): ProductPolicyEvaluationResult {
    if (results.length === 0) {
      // No hooks registered, return default deny
      return {
        decision: 'denied' as PolicyDecision,
        reasonCode: 'UNKNOWN_POLICY' as PolicyReasonCode,
        requirements: {
          auditRequired: true,
          justificationRequired: false,
          notificationRequired: false,
          reviewRequired: false,
        },
        policies: [],
      };
    }

    // Check if any result is denied
    const deniedResult = results.find(r => r.decision === 'denied');
    const decision = deniedResult?.decision ?? ('allowed' as PolicyDecision);
    const reasonCode = deniedResult?.reasonCode ?? ('PATIENT_OWN_RECORD' as PolicyReasonCode);

    // Combine requirements
    const requirements: PolicyRequirement = {
      auditRequired: results.some(r => r.requirements.auditRequired),
      justificationRequired: results.some(r => r.requirements.justificationRequired),
      notificationRequired: results.some(r => r.requirements.notificationRequired),
      reviewRequired: results.some(r => r.requirements.reviewRequired),
    };

    // Combine all policies
    const policies = results.flatMap(r => r.policies);

    return {
      decision,
      reasonCode,
      requirements,
      policies,
    };
  }

  /**
   * Get the number of registered hooks for each policy type
   */
  getHookCounts(): {
    consent: number;
    treatmentRelationship: number;
    facilityScope: number;
    emergency: number;
    fchvCommunity: number;
  } {
    return {
      consent: this.consentHooks.size,
      treatmentRelationship: this.treatmentRelationshipHooks.size,
      facilityScope: this.facilityScopeHooks.size,
      emergency: this.emergencyHooks.size,
      fchvCommunity: this.fchvCommunityHooks.size,
    };
  }

  /**
   * Clear all registered hooks
   */
  clearAllHooks(): void {
    this.consentHooks.clear();
    this.treatmentRelationshipHooks.clear();
    this.facilityScopeHooks.clear();
    this.emergencyHooks.clear();
    this.fchvCommunityHooks.clear();
  }
}

/**
 * Global singleton instance of the Kernel PHI Policy Facade
 */
export const kernelPhiPolicyFacade = new KernelPhiPolicyFacade();
