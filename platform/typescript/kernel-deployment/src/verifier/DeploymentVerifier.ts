import { promises as fs } from 'node:fs';
import * as path from 'node:path';

/**
 * Configuration for a single HTTP health check.
 */
export interface HealthCheckConfig {
  url: string;
  required?: boolean;
  retries?: number;
  retryIntervalMs?: number;
  timeoutMs?: number;
}

/**
 * Result entry for a single health check.
 */
export interface HealthCheckVerificationEntry {
  url: string;
  status: 'passed' | 'failed' | 'skipped';
  latencyMs: number | null;
  error: string | null;
  checkedAt: string;
}

/**
 * Overall result of live health check verification.
 */
export interface HealthCheckVerificationResult {
  allPassed: boolean;
  checks: HealthCheckVerificationEntry[];
  errors: string[];
}

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

  /**
   * Perform live HTTP health checks with retry + exponential backoff.
   * Writes results to `outputDir/health-check-results.json`.
   * Fails closed — if a required check does not pass after all retries, the result is not valid.
   */
  async verifyLiveHealthChecks(
    checks: HealthCheckConfig[],
    outputDir: string,
  ): Promise<HealthCheckVerificationResult> {
    const entries: HealthCheckVerificationEntry[] = [];
    const aggregateErrors: string[] = [];

    for (const check of checks) {
      const required = check.required ?? true;
      const maxRetries = check.retries ?? 3;
      const timeoutMs = check.timeoutMs ?? 5_000;
      const intervalMs = check.retryIntervalMs ?? 1_000;

      let lastError: string | null = null;
      let passed = false;
      let latencyMs: number | null = null;
      let checkedAt = new Date().toISOString();

      for (let attempt = 0; attempt < maxRetries; attempt++) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), timeoutMs);
        const requestStart = Date.now();

        try {
          const response = await fetch(check.url, { signal: controller.signal });
          latencyMs = Date.now() - requestStart;
          checkedAt = new Date().toISOString();

          if (response.ok) {
            passed = true;
            lastError = null;
            break;
          }

          lastError = `HTTP ${response.status} ${response.statusText}`;
        } catch (err: unknown) {
          latencyMs = Date.now() - requestStart;
          checkedAt = new Date().toISOString();
          lastError = err instanceof Error ? err.message : String(err);
        } finally {
          clearTimeout(timer);
        }

        if (attempt < maxRetries - 1) {
          // Exponential backoff
          await this.sleep(intervalMs * Math.pow(2, attempt));
        }
      }

      entries.push({
        url: check.url,
        status: passed ? 'passed' : 'failed',
        latencyMs: passed ? latencyMs : null,
        error: lastError,
        checkedAt,
      });

      if (!passed && required) {
        aggregateErrors.push(
          `Required health check failed: ${check.url} — ${lastError ?? 'no response'}`,
        );
      }
    }

    const result: HealthCheckVerificationResult = {
      allPassed: aggregateErrors.length === 0,
      checks: entries,
      errors: aggregateErrors,
    };

    // Persist results for observability
    await fs.mkdir(outputDir, { recursive: true });
    await fs.writeFile(
      path.join(outputDir, 'health-check-results.json'),
      JSON.stringify(result, null, 2),
      'utf-8',
    );

    return result;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}

/**
 * Verification result
 */
export interface VerificationResult {
  valid: boolean;
  errors: string[];
}
