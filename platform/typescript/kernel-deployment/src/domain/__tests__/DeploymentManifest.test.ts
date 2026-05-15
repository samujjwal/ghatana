import { describe, expect, it } from 'vitest';
import {
  DeploymentManifestGenerator,
  DeploymentManifestSchema,
} from '../DeploymentManifest.js';

describe('DeploymentManifest', () => {
  describe('DeploymentManifestGenerator.createManifest()', () => {
    it('creates a manifest with required fields', () => {
      const gen = new DeploymentManifestGenerator();
      const manifest = gen.createManifest({
        productId: 'digital-marketing',
        version: '1.0.0',
        environment: 'local',
        surfaces: [
          { surface: 'dm-api', status: 'deployed', artifactId: 'art-1', deploymentTarget: 'compose-local' },
        ],
        rollbackPlan: { strategy: 'previous-artifact', targetVersion: '0.9.0', reason: 'Testing', steps: ['stop', 'restart'] },
      });

      expect(manifest.schemaVersion).toBe('1.0.0');
      expect(manifest.productId).toBe('digital-marketing');
      expect(manifest.deploymentId).toMatch(/^deploy-\d+$/);
      expect(manifest.surfaces).toHaveLength(1);
      expect(manifest.surfaces[0].healthCheckPassed).toBe(false);
      expect(manifest.surfaces[0].deployedAt).toBeNull();
    });

    it('includes optional target and artifactManifestRef when provided', () => {
      const gen = new DeploymentManifestGenerator();
      const manifest = gen.createManifest({
        productId: 'digital-marketing',
        version: '2.0.0',
        environment: 'dev',
        surfaces: [],
        rollbackPlan: { strategy: 'blue-green', targetVersion: '1.9.0', reason: 'Rollback', steps: [] },
        target: 'compose-local',
        artifactManifestRef: 'artifacts/digital-marketing/artifact-manifest.json',
      });

      expect(manifest.target).toBe('compose-local');
      expect(manifest.artifactManifestRef).toBe('artifacts/digital-marketing/artifact-manifest.json');
      expect(manifest.environmentSafety).toBe('dev');
    });

    it('creates local compose manifests with lifecycle refs and scope', () => {
      const gen = new DeploymentManifestGenerator();
      const manifest = gen.createManifest({
        runId: 'run-123',
        correlationId: 'corr-123',
        productId: 'digital-marketing',
        productUnitId: 'product-unit:digital-marketing',
        version: '2.0.0',
        environment: 'local',
        environmentSafety: 'local',
        lifecyclePhase: 'deploy',
        surfaces: [
          { surface: 'backend-api', status: 'deployed', artifactId: 'api-image', deploymentTarget: 'compose-local' },
        ],
        rollbackPlan: { strategy: 'previous-artifact', targetVersion: '1.9.0', reason: 'Rollback', steps: [] },
        lifecycleResultRef: 'lifecycle-result.json',
        artifactManifestRef: 'artifact-manifest.json',
        target: 'compose-local',
        scope: {
          tenant: 'tenant-1',
          workspace: 'workspace-1',
          project: 'project-1',
        },
      });

      expect(manifest).toMatchObject({
        runId: 'run-123',
        correlationId: 'corr-123',
        productUnitId: 'product-unit:digital-marketing',
        lifecyclePhase: 'deploy',
        lifecycleResultRef: 'lifecycle-result.json',
        artifactManifestRef: 'artifact-manifest.json',
        scope: { tenant: 'tenant-1' },
      });
      expect(() => gen.validateManifest(manifest)).not.toThrow();
    });
  });

  describe('DeploymentManifestGenerator.validateManifest()', () => {
    it('validates a complete and correct manifest', () => {
      const gen = new DeploymentManifestGenerator();
      const raw = {
        schemaVersion: '1.0.0',
        productId: 'digital-marketing',
        version: '1.0.0',
        environment: 'local',
        deploymentId: 'deploy-123',
        surfaces: [
          {
            surface: 'dm-api',
            status: 'deployed',
            artifactId: 'art-1',
            deploymentTarget: 'compose-local',
            deployedAt: new Date().toISOString(),
            healthCheckPassed: true,
          },
        ],
        deployedAt: new Date().toISOString(),
        rollbackPlan: { strategy: 'previous-artifact', targetVersion: '0.9.0', reason: 'Rollback', steps: [] },
      };

      const result = gen.validateManifest(raw);
      expect(result.productId).toBe('digital-marketing');
    });

    it('throws when schemaVersion format is invalid', () => {
      const gen = new DeploymentManifestGenerator();
      expect(() =>
        gen.validateManifest({ schemaVersion: 'v1', productId: 'x', version: '1.0', environment: 'local', deploymentId: 'id', surfaces: [], deployedAt: new Date().toISOString(), rollbackPlan: {} }),
      ).toThrow();
    });

    it('requires lifecycle refs for deploy and verify manifests', () => {
      const gen = new DeploymentManifestGenerator();

      expect(() =>
        gen.validateManifest({
          schemaVersion: '1.0.0',
          productId: 'digital-marketing',
          version: '1.0',
          environment: 'local',
          environmentSafety: 'local',
          lifecyclePhase: 'verify',
          deploymentId: 'id',
          surfaces: [],
          deployedAt: new Date().toISOString(),
          rollbackPlan: { strategy: 'previous-artifact', targetVersion: '0.9.0', reason: 'Rollback', steps: [] },
        }),
      ).toThrow(/lifecycleResultRef/);
    });
  });

  describe('DeploymentManifestSchema optional extensions', () => {
    it('accepts manifest with extended fields: services, healthChecks, overallStatus, verifierResult', () => {
      const raw = {
        schemaVersion: '1.0.0',
        productId: 'digital-marketing',
        version: '1.0.0',
        environment: 'local',
        deploymentId: 'deploy-456',
        surfaces: [],
        deployedAt: new Date().toISOString(),
        rollbackPlan: { strategy: 'previous-artifact', targetVersion: '0.9.0', reason: 'x', steps: [] },
        target: 'compose-local',
        lifecyclePhase: 'verify',
        lifecycleResultRef: 'lifecycle-result.json',
        artifactManifestRef: 'artifact-manifest.json',
        environmentSafety: 'local',
        approvalRef: { approvalId: 'approval-1', status: 'approved', ref: 'approvals/approval-1.json' },
        rollbackVerification: { verified: false, checkedAt: new Date().toISOString(), details: 'not needed' },
        scope: { tenant: 'tenant-1', workspace: 'workspace-1', project: 'project-1' },
        services: {
          'dm-api-1': { status: 'running', healthCheckPassed: true },
        },
        healthChecks: [
          {
            url: 'http://localhost:8080/health/ready',
            status: 'passed',
            latencyMs: 42,
            error: null,
            checkedAt: new Date().toISOString(),
          },
        ],
        overallStatus: 'deployed',
        verifierResult: { valid: true, checkedAt: new Date().toISOString(), errors: [] },
      };

      const result = DeploymentManifestSchema.parse(raw);
      expect(result.target).toBe('compose-local');
      expect(result.services?.['dm-api-1'].status).toBe('running');
      expect(result.healthChecks?.[0].status).toBe('passed');
      expect(result.overallStatus).toBe('deployed');
      expect(result.verifierResult?.valid).toBe(true);
      expect(result.approvalRef?.status).toBe('approved');
    });

    it('accepts failed verification manifests with rollback verification status', () => {
      const result = DeploymentManifestSchema.parse({
        schemaVersion: '1.0.0',
        runId: 'run-123',
        correlationId: 'corr-123',
        productId: 'digital-marketing',
        productUnitId: 'product-unit:digital-marketing',
        version: '1.0.0',
        environment: 'local',
        environmentSafety: 'local',
        lifecyclePhase: 'verify',
        deploymentId: 'deploy-789',
        surfaces: [],
        deployedAt: new Date().toISOString(),
        lifecycleResultRef: 'lifecycle-result.json',
        artifactManifestRef: 'artifact-manifest.json',
        rollbackPlan: { strategy: 'previous-artifact', targetVersion: '0.9.0', reason: 'x', steps: [] },
        overallStatus: 'failed',
        verifierResult: {
          valid: false,
          checkedAt: new Date().toISOString(),
          errors: ['readiness failed'],
        },
        rollbackVerification: {
          verified: true,
          checkedAt: new Date().toISOString(),
          details: 'rollback target healthy',
        },
      });

      expect(result.overallStatus).toBe('failed');
      expect(result.rollbackVerification?.verified).toBe(true);
    });
  });
});
