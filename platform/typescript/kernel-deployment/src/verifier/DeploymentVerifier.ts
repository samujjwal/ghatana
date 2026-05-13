/**
 * Deployment verifier
 */
export class DeploymentVerifier {
  /**
   * Verify deployment manifest
   */
  async verifyManifest(manifest: unknown): Promise<VerificationResult> {
    const errors: string[] = [];
    
    // Basic structure validation
    if (!manifest || typeof manifest !== 'object') {
      errors.push('Manifest must be an object');
      return { valid: false, errors };
    }

    const m = manifest as Record<string, unknown>;

    if (!m.schemaVersion) {
      errors.push('schemaVersion is required');
    }

    if (!m.productId) {
      errors.push('productId is required');
    }

    if (!m.version) {
      errors.push('version is required');
    }

    if (!m.environment) {
      errors.push('environment is required');
    }

    if (!m.deploymentId) {
      errors.push('deploymentId is required');
    }

    if (!m.surfaces || !Array.isArray(m.surfaces)) {
      errors.push('surfaces must be an array');
    }

    if (!m.rollbackPlan || typeof m.rollbackPlan !== 'object') {
      errors.push('rollbackPlan must be an object');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Verify deployment target
   */
  async verifyTarget(target: unknown): Promise<VerificationResult> {
    const errors: string[] = [];

    if (!target || typeof target !== 'object') {
      errors.push('Target must be an object');
      return { valid: false, errors };
    }

    const t = target as Record<string, unknown>;

    if (!t.id) {
      errors.push('Target id is required');
    }

    if (!t.name) {
      errors.push('Target name is required');
    }

    if (!t.type) {
      errors.push('Target type is required');
    }

    const validTypes = ['compose-local', 'kubernetes', 'helm', 'terraform'];
    if (t.type && !validTypes.includes(t.type as string)) {
      errors.push(`Invalid target type: ${t.type}`);
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Verify health check configuration
   */
  async verifyHealthCheck(check: unknown): Promise<VerificationResult> {
    const errors: string[] = [];

    if (!check || typeof check !== 'object') {
      errors.push('Health check must be an object');
      return { valid: false, errors };
    }

    const c = check as Record<string, unknown>;

    if (!c.checkId) {
      errors.push('checkId is required');
    }

    if (!c.checkName) {
      errors.push('checkName is required');
    }

    if (!c.checkType) {
      errors.push('checkType is required');
    }

    if (!c.config) {
      errors.push('config is required');
    }

    if (c.timeoutMs === undefined) {
      errors.push('timeoutMs is required');
    }

    if (c.retries === undefined) {
      errors.push('retries is required');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Verify rollback plan
   */
  async verifyRollbackPlan(rollbackPlan: unknown): Promise<VerificationResult> {
    const errors: string[] = [];

    if (!rollbackPlan || typeof rollbackPlan !== 'object') {
      errors.push('Rollback plan must be an object');
      return { valid: false, errors };
    }

    const r = rollbackPlan as Record<string, unknown>;

    if (!r.strategy) {
      errors.push('strategy is required');
    }

    if (!r.targetVersion) {
      errors.push('targetVersion is required');
    }

    if (!r.reason) {
      errors.push('reason is required');
    }

    if (!r.steps || !Array.isArray(r.steps)) {
      errors.push('steps must be an array');
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }
}

/**
 * Verification result
 */
export interface VerificationResult {
  valid: boolean;
  errors: string[];
}
