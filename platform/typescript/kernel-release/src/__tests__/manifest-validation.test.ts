import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { ProductReleaseManager, ProductReleaseSchema } from '../ProductRelease.js';
import { ProductPromotionPlanManager, ProductPromotionPlanSchema } from '../ProductPromotionPlan.js';
import { ProductRollbackPlanManager, ProductRollbackPlanSchema } from '../ProductRollbackPlan.js';

describe('Release manifest validation', () => {
  let tempDir: string;
  let releaseManager: ProductReleaseManager;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(process.cwd(), 'test-manifest-'));
    releaseManager = new ProductReleaseManager(tempDir);
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it('should fail when artifact manifest is missing', async () => {
    const release = ProductReleaseSchema.parse({
      productId: 'test-product',
      version: '1.0.0',
      sourceRef: 'abc123',
      artifactManifest: 'artifacts/artifact-manifest.json',
      deploymentManifest: 'deploy/deployment-manifest.json',
      releaseManifest: 'release/release-manifest.json',
      environment: 'production',
      timestamp: new Date().toISOString(),
      releasedBy: 'test-user',
    });

    // Create deployment and release manifests
    await fs.mkdir(path.join(tempDir, 'deploy'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'deploy', 'deployment-manifest.json'), '{}');
    await fs.mkdir(path.join(tempDir, 'release'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'release', 'release-manifest.json'), '{}');

    try {
      await releaseManager.createRelease(release);
      expect.fail('Should have thrown error');
    } catch (error) {
      expect(error).toBeInstanceOf(Error);
      expect((error as Error).message).toContain('artifact manifest');
    }
  });

  it('should fail when artifact manifest is invalid', async () => {
    const release = ProductReleaseSchema.parse({
      productId: 'test-product',
      version: '1.0.0',
      sourceRef: 'abc123',
      artifactManifest: 'artifacts/artifact-manifest.json',
      deploymentManifest: 'deploy/deployment-manifest.json',
      releaseManifest: 'release/release-manifest.json',
      environment: 'production',
      timestamp: new Date().toISOString(),
      releasedBy: 'test-user',
    });

    // Create invalid artifact manifest
    await fs.mkdir(path.join(tempDir, 'artifacts'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'artifacts', 'artifact-manifest.json'), '{ invalid json }');
    await fs.mkdir(path.join(tempDir, 'deploy'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'deploy', 'deployment-manifest.json'), '{}');
    await fs.mkdir(path.join(tempDir, 'release'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'release', 'release-manifest.json'), '{}');

    try {
      await releaseManager.createRelease(release);
      expect.fail('Should have thrown error');
    } catch (error) {
      expect(error).toBeInstanceOf(Error);
      expect((error as Error).message).toContain('Invalid artifact manifest');
    }
  });

  it('should succeed when all manifests are valid', async () => {
    const validArtifactManifest = {
      schemaVersion: '1.0.0',
      productId: 'test-product',
      phase: 'release',
      surface: 'web',
      timestamp: new Date().toISOString(),
      artifacts: [
        {
          id: 'artifact-1',
          path: 'dist/index.html',
          metadata: {
            type: 'static-web-bundle',
            version: '1.0.0',
            timestamp: new Date().toISOString(),
            sizeBytes: 1024,
          },
          fingerprint: {
            algorithm: 'sha256',
            hash: 'abc123',
          },
          expected: true,
          found: true,
        },
      ],
    };

    const release = ProductReleaseSchema.parse({
      productId: 'test-product',
      version: '1.0.0',
      sourceRef: 'abc123',
      artifactManifest: 'artifacts/artifact-manifest.json',
      deploymentManifest: 'deploy/deployment-manifest.json',
      releaseManifest: 'release/release-manifest.json',
      environment: 'production',
      timestamp: new Date().toISOString(),
      releasedBy: 'test-user',
    });

    // Create all manifests
    await fs.mkdir(path.join(tempDir, 'artifacts'), { recursive: true });
    await fs.writeFile(
      path.join(tempDir, 'artifacts', 'artifact-manifest.json'),
      JSON.stringify(validArtifactManifest)
    );
    await fs.mkdir(path.join(tempDir, 'deploy'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'deploy', 'deployment-manifest.json'), '{}');
    await fs.mkdir(path.join(tempDir, 'release'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'release', 'release-manifest.json'), '{}');

    const result = await releaseManager.createRelease(release);
    expect(result.productId).toBe('test-product');
  });
});

describe('Promotion plan manifest validation', () => {
  let tempDir: string;
  let promotionManager: ProductPromotionPlanManager;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(process.cwd(), 'test-promotion-'));
    promotionManager = new ProductPromotionPlanManager(tempDir);
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it('should fail when manifest files are missing', async () => {
    const plan = ProductPromotionPlanSchema.parse({
      productId: 'test-product',
      sourceEnvironment: 'staging',
      targetEnvironment: 'production',
      promotionRequirements: {
        artifactManifest: true,
        deploymentManifest: true,
        releaseManifest: true,
        securityChecks: true,
        privacyChecks: true,
        licenseChecks: true,
        conformanceChecks: true,
        e2eChecks: true,
        performanceChecks: true,
      },
      manifestPaths: {
        artifactManifest: 'artifacts/artifact-manifest.json',
        deploymentManifest: 'deploy/deployment-manifest.json',
        releaseManifest: 'release/release-manifest.json',
      },
      approvalGate: {
        required: false,
        approvers: [],
        approved: true,
      },
      rollbackPlan: {
        strategy: 'previous-artifact',
        previousArtifact: 'v0.9.0',
      },
    });

    try {
      await promotionManager.createPromotionPlan(plan);
      expect.fail('Should have thrown error');
    } catch (error) {
      expect(error).toBeInstanceOf(Error);
      expect((error as Error).message).toContain('not found');
    }
  });
});

describe('Rollback plan manifest validation', () => {
  let tempDir: string;
  let rollbackManager: ProductRollbackPlanManager;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(process.cwd(), 'test-rollback-'));
    rollbackManager = new ProductRollbackPlanManager(tempDir);
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it('should fail when manifest file is missing', async () => {
    const plan = ProductRollbackPlanSchema.parse({
      productId: 'test-product',
      environment: 'production',
      currentVersion: '1.0.0',
      targetVersion: '0.9.0',
      strategy: 'previous-artifact',
      reason: 'Critical bug',
      rollbackBy: 'test-user',
      timestamp: new Date().toISOString(),
      manifestPath: 'rollback/rollback-manifest.json',
      verificationPlan: {
        healthChecks: true,
        smokeTests: true,
        metrics: true,
      },
    });

    try {
      await rollbackManager.createRollbackPlan(plan);
      expect.fail('Should have thrown error');
    } catch (error) {
      expect(error).toBeInstanceOf(Error);
      expect((error as Error).message).toContain('not found');
    }
  });

  it('should succeed when manifest file exists', async () => {
    const plan = ProductRollbackPlanSchema.parse({
      productId: 'test-product',
      environment: 'production',
      currentVersion: '1.0.0',
      targetVersion: '0.9.0',
      strategy: 'previous-artifact',
      reason: 'Critical bug',
      rollbackBy: 'test-user',
      timestamp: new Date().toISOString(),
      manifestPath: 'rollback/rollback-manifest.json',
      verificationPlan: {
        healthChecks: true,
        smokeTests: true,
        metrics: true,
      },
    });

    // Create manifest file
    await fs.mkdir(path.join(tempDir, 'rollback'), { recursive: true });
    await fs.writeFile(path.join(tempDir, 'rollback', 'rollback-manifest.json'), '{}');

    const result = await rollbackManager.createRollbackPlan(plan);
    expect(result.productId).toBe('test-product');
  });
});
