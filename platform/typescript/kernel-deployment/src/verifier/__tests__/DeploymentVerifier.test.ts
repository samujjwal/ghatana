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
      expect(result.errors).toHaveLength(0);
      expect(result.checks).toHaveLength(2);
      expect(result.checks.every((c) => c.status === 'passed')).toBe(true);

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
  });
});
