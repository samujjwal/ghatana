import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { DeploymentVerifier } from '../DeploymentVerifier.js';
import type { HealthCheckConfig } from '../DeploymentVerifier.js';

describe('DeploymentVerifier', () => {
  let verifier: DeploymentVerifier;

  beforeEach(() => {
    verifier = new DeploymentVerifier();
  });

  describe('verifyManifest()', () => {
    it('returns valid for a complete manifest', async () => {
      const manifest = {
        schemaVersion: '1.0.0',
        productId: 'digital-marketing',
        version: '1.2.3',
        environment: 'local',
        deploymentId: 'deploy-123',
        surfaces: [],
        deployedAt: new Date().toISOString(),
        rollbackPlan: { strategy: 'previous-artifact', targetVersion: '1.2.2', reason: 'Test', steps: [] },
      };

      const result = await verifier.verifyManifest(manifest);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('returns invalid when schemaVersion is missing', async () => {
      const result = await verifier.verifyManifest({ productId: 'x', version: '1.0', environment: 'local', deploymentId: 'id' });

      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('schemaVersion'))).toBe(true);
    });

    it('returns all required manifest field errors', async () => {
      const result = await verifier.verifyManifest({});

      expect(result.valid).toBe(false);
      expect(result.errors).toEqual([
        'schemaVersion is required',
        'productId is required',
        'version is required',
        'environment is required',
        'deploymentId is required',
        'surfaces must be an array',
        'rollbackPlan must be an object',
      ]);
    });

    it('returns invalid for non-objects', async () => {
      const result = await verifier.verifyManifest('not-an-object');

      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain('object');
    });
  });

  describe('verifyTarget()', () => {
    it('returns valid for a well-formed target', async () => {
      const target = { id: 't1', name: 'Local Compose', type: 'compose-local' };
      const result = await verifier.verifyTarget(target);
      expect(result.valid).toBe(true);
    });

    it('returns invalid for unknown target type', async () => {
      const result = await verifier.verifyTarget({ id: 't1', name: 'foo', type: 'invalid-type' });
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Invalid target type'))).toBe(true);
    });

    it('returns invalid when id is missing', async () => {
      const result = await verifier.verifyTarget({ name: 'foo', type: 'compose-local' });
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('id'))).toBe(true);
    });

    it('returns invalid for non-object and incomplete targets', async () => {
      await expect(verifier.verifyTarget(null)).resolves.toEqual({
        valid: false,
        errors: ['Target must be an object'],
      });

      const result = await verifier.verifyTarget({});
      expect(result.errors).toEqual([
        'Target id is required',
        'Target name is required',
        'Target type is required',
      ]);
    });
  });

  describe('verifyHealthCheck()', () => {
    it('returns valid for a complete health check config', async () => {
      const check = { checkId: 'hc1', checkName: 'API Ready', checkType: 'http', config: { url: 'http://localhost/health' }, timeoutMs: 5000, retries: 3 };
      const result = await verifier.verifyHealthCheck(check);
      expect(result.valid).toBe(true);
    });

    it('returns invalid when checkId is missing', async () => {
      const result = await verifier.verifyHealthCheck({ checkName: 'x', checkType: 'http', config: {}, timeoutMs: 1000, retries: 1 });
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('checkId'))).toBe(true);
    });

    it('returns invalid for non-object and incomplete health checks', async () => {
      await expect(verifier.verifyHealthCheck(undefined)).resolves.toEqual({
        valid: false,
        errors: ['Health check must be an object'],
      });

      const result = await verifier.verifyHealthCheck({});
      expect(result.errors).toEqual([
        'checkId is required',
        'checkName is required',
        'checkType is required',
        'config is required',
        'timeoutMs is required',
        'retries is required',
      ]);
    });
  });

  describe('verifyRollbackPlan()', () => {
    it('returns valid for a complete rollback plan', async () => {
      const plan = { strategy: 'previous-artifact', targetVersion: '1.0.0', reason: 'Test', steps: ['step1'] };
      const result = await verifier.verifyRollbackPlan(plan);
      expect(result.valid).toBe(true);
    });

    it('returns invalid when strategy is missing', async () => {
      const result = await verifier.verifyRollbackPlan({ targetVersion: '1.0.0', reason: 'x', steps: [] });
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('strategy'))).toBe(true);
    });

    it('returns invalid for non-object and incomplete rollback plans', async () => {
      await expect(verifier.verifyRollbackPlan('nope')).resolves.toEqual({
        valid: false,
        errors: ['Rollback plan must be an object'],
      });

      const result = await verifier.verifyRollbackPlan({});
      expect(result.errors).toEqual([
        'strategy is required',
        'targetVersion is required',
        'reason is required',
        'steps must be an array',
      ]);
    });
  });

  describe('verifyLiveHealthChecks()', () => {
    let outputDir: string;

    beforeEach(async () => {
      outputDir = await fs.mkdtemp(path.join(os.tmpdir(), 'verifier-'));
    });

    afterEach(async () => {
      await fs.rm(outputDir, { recursive: true, force: true });
    });

    it('passes when all fetch responses are 200 OK', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
      });
      vi.stubGlobal('fetch', mockFetch);

      const checks: HealthCheckConfig[] = [
        { url: 'http://localhost:8080/health/ready', required: true, retries: 1 },
        { url: 'http://localhost:8080/health/live', required: true, retries: 1 },
      ];

      const result = await verifier.verifyLiveHealthChecks(checks, outputDir);

      expect(result.allPassed).toBe(true);
      expect(result.schemaVersion).toBe('1.0.0');
      expect(result.errors).toHaveLength(0);
      expect(result.checks).toHaveLength(2);
      expect(result.checks.every((c) => c.status === 'passed')).toBe(true);
      expect(result.checks[0]).toMatchObject({
        checkId: 'http-localhost-8080-health-ready',
        name: 'http://localhost:8080/health/ready',
        attempts: 1,
        evidenceRefs: ['health-check:http-localhost-8080-health-ready'],
      });

      vi.unstubAllGlobals();
    });

    it('fails closed when a required check returns a non-2xx response', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        statusText: 'Service Unavailable',
      });
      vi.stubGlobal('fetch', mockFetch);

      const checks: HealthCheckConfig[] = [
        { url: 'http://localhost:8080/health/ready', required: true, retries: 1 },
      ];

      const result = await verifier.verifyLiveHealthChecks(checks, outputDir);

      expect(result.allPassed).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.checks[0].status).toBe('failed');

      vi.unstubAllGlobals();
    });

    it('does not fail when a non-required check fails', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        statusText: 'Service Unavailable',
      });
      vi.stubGlobal('fetch', mockFetch);

      const checks: HealthCheckConfig[] = [
        { url: 'http://localhost:8080/health/info', required: false, retries: 1 },
      ];

      const result = await verifier.verifyLiveHealthChecks(checks, outputDir);

      expect(result.allPassed).toBe(true);
      expect(result.errors).toHaveLength(0);

      vi.unstubAllGlobals();
    });

    it('writes health-check-results.json to outputDir', async () => {
      const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 200, statusText: 'OK' });
      vi.stubGlobal('fetch', mockFetch);

      const checks: HealthCheckConfig[] = [
        { url: 'http://localhost:8080/health/ready', required: true, retries: 1 },
      ];

      await verifier.verifyLiveHealthChecks(checks, outputDir);

      const resultsPath = path.join(outputDir, 'health-check-results.json');
      const raw = await fs.readFile(resultsPath, 'utf-8');
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      expect(parsed.allPassed).toBe(true);
      expect(parsed.schemaVersion).toBe('1.0.0');
      expect(Array.isArray(parsed.checks)).toBe(true);

      vi.unstubAllGlobals();
    });

    it('fails closed when fetch throws a network error', async () => {
      const mockFetch = vi.fn().mockRejectedValue(new Error('ECONNREFUSED'));
      vi.stubGlobal('fetch', mockFetch);

      const checks: HealthCheckConfig[] = [
        { url: 'http://localhost:9999/health/ready', required: true, retries: 1 },
      ];

      const result = await verifier.verifyLiveHealthChecks(checks, outputDir);

      expect(result.allPassed).toBe(false);
      expect(result.checks[0].error).toContain('ECONNREFUSED');

      vi.unstubAllGlobals();
    });

    it('retries before passing and records attempt count', async () => {
      const mockFetch = vi.fn()
        .mockResolvedValueOnce({ ok: false, status: 503, statusText: 'Unavailable' })
        .mockResolvedValueOnce({ ok: true, status: 200, statusText: 'OK' });
      vi.stubGlobal('fetch', mockFetch);

      const result = await verifier.verifyLiveHealthChecks([
        {
          url: 'http://localhost:8080/health/ready',
          required: true,
          retries: 2,
          retryIntervalMs: 0,
        },
      ], outputDir);

      expect(result.allPassed).toBe(true);
      expect(result.checks[0].attempts).toBe(2);
      expect(mockFetch).toHaveBeenCalledTimes(2);

      vi.unstubAllGlobals();
    });

    it('uses default retry and timeout settings', async () => {
      const mockFetch = vi.fn()
        .mockResolvedValueOnce({ ok: false, status: 503, statusText: 'Unavailable' })
        .mockResolvedValueOnce({ ok: true, status: 200, statusText: 'OK' });
      vi.stubGlobal('fetch', mockFetch);

      const result = await verifier.verifyLiveHealthChecks([
        {
          url: 'http://localhost:8080/health/defaults',
          retryIntervalMs: 0,
        },
      ], outputDir);

      expect(result.allPassed).toBe(true);
      expect(result.checks[0].attempts).toBe(2);

      vi.unstubAllGlobals();
    });

    it('records no response when zero retry attempts are configured', async () => {
      const mockFetch = vi.fn();
      vi.stubGlobal('fetch', mockFetch);

      const result = await verifier.verifyLiveHealthChecks([
        {
          url: 'http://localhost:8080/health/never-run',
          required: true,
          retries: 0,
        },
      ], outputDir);

      expect(result.allPassed).toBe(false);
      expect(result.errors[0]).toContain('no response');
      expect(result.checks[0].attempts).toBe(0);
      expect(mockFetch).not.toHaveBeenCalled();

      vi.unstubAllGlobals();
    });

    it('records timeout failures with configured timeout', async () => {
      const abortError = new Error('aborted');
      abortError.name = 'AbortError';
      const mockFetch = vi.fn().mockRejectedValue(abortError);
      vi.stubGlobal('fetch', mockFetch);

      const result = await verifier.verifyLiveHealthChecks([
        {
          url: 'http://localhost:8080/health/ready',
          required: true,
          retries: 1,
          timeoutMs: 25,
        },
      ], outputDir);

      expect(result.allPassed).toBe(false);
      expect(result.checks[0].error).toBe('Timed out after 25ms');

      vi.unstubAllGlobals();
    });

    it('aborts in-flight health checks when the timeout elapses', async () => {
      vi.useFakeTimers();
      const mockFetch = vi.fn((_url: string, init?: RequestInit) => new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => {
          const abortError = new Error('aborted');
          abortError.name = 'AbortError';
          reject(abortError);
        });
      }));
      vi.stubGlobal('fetch', mockFetch);

      const verification = verifier.verifyLiveHealthChecks([
        {
          url: 'http://localhost:8080/health/slow',
          required: true,
          retries: 1,
          timeoutMs: 25,
        },
      ], outputDir);

      await vi.advanceTimersByTimeAsync(25);
      const result = await verification;

      expect(result.allPassed).toBe(false);
      expect(result.checks[0].error).toBe('Timed out after 25ms');

      vi.useRealTimers();
      vi.unstubAllGlobals();
    });

    it('handles non-Error thrown values and empty check ids', async () => {
      const mockFetch = vi.fn().mockRejectedValue('socket closed');
      vi.stubGlobal('fetch', mockFetch);

      const result = await verifier.verifyLiveHealthChecks([
        {
          url: '://',
          required: true,
          retries: 1,
        },
      ], outputDir);

      expect(result.allPassed).toBe(false);
      expect(result.checks[0].checkId).toBe('health-check');
      expect(result.checks[0].error).toBe('socket closed');

      vi.unstubAllGlobals();
    });
  });
});
