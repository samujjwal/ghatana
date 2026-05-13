import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { ToolchainOutputValidator } from '../ToolchainOutputValidator.js';
import type { ToolchainAdapterContext, AdapterLogger } from '../ToolchainAdapter.js';

describe('ToolchainOutputValidator', () => {
  let validator: ToolchainOutputValidator;
  let testDir: string;

  beforeEach(async () => {
    validator = new ToolchainOutputValidator();
    testDir = `/tmp/test-validator-${Date.now()}`;
    await fs.mkdir(testDir, { recursive: true });
  });

  afterEach(async () => {
    await fs.rm(testDir, { recursive: true, force: true });
  });

  it('should validate when all expected artifacts are present', async () => {
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), 'content1');
    await fs.writeFile(path.join(testDir, 'artifact2.jar'), 'content2');

    const context: ToolchainAdapterContext = {
      productId: 'test-product',
      phase: 'build',
      surface: { type: 'backend-api', adapter: 'gradle', path: '/backend' },
      dryRun: false,
      surfaceConfig: {},
      phaseConfig: {},
      logger: {} as AdapterLogger,
      outputDir: testDir,
    };

    const result = await validator.validateOutputs(context, ['artifact1.jar', 'artifact2.jar']);

    expect(result.status).toBe('valid');
    expect(result.errors).toHaveLength(0);
    expect(result.missingArtifacts).toHaveLength(0);
  });

  it('should detect missing expected artifacts', async () => {
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), 'content1');

    const context: ToolchainAdapterContext = {
      productId: 'test-product',
      phase: 'build',
      surface: { type: 'backend-api', adapter: 'gradle', path: '/backend' },
      dryRun: false,
      surfaceConfig: {},
      phaseConfig: {},
      logger: {} as AdapterLogger,
      outputDir: testDir,
    };

    const result = await validator.validateOutputs(context, ['artifact1.jar', 'artifact2.jar']);

    expect(result.status).toBe('invalid');
    expect(result.missingArtifacts).toContain('artifact2.jar');
  });

  it('should detect unexpected artifacts', async () => {
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), 'content1');
    await fs.writeFile(path.join(testDir, 'unexpected.txt'), 'content2');

    const context: ToolchainAdapterContext = {
      productId: 'test-product',
      phase: 'build',
      surface: { type: 'backend-api', adapter: 'gradle', path: '/backend' },
      dryRun: false,
      surfaceConfig: {},
      phaseConfig: {},
      logger: {} as AdapterLogger,
      outputDir: testDir,
    };

    const result = await validator.validateOutputs(context, ['artifact1.jar']);

    expect(result.status).toBe('partial');
    expect(result.unexpectedArtifacts).toContain('unexpected.txt');
  });

  it('should return invalid when output directory does not exist', async () => {
    const context: ToolchainAdapterContext = {
      productId: 'test-product',
      phase: 'build',
      surface: { type: 'backend-api', adapter: 'gradle', path: '/backend' },
      dryRun: false,
      surfaceConfig: {},
      phaseConfig: {},
      logger: {} as AdapterLogger,
      outputDir: '/nonexistent-directory',
    };

    const result = await validator.validateOutputs(context, ['artifact1.jar']);

    expect(result.status).toBe('invalid');
    expect(result.errors.some((e) => e.path === 'outputDir')).toBe(true);
  });

  it('should validate artifact checksums', async () => {
    const crypto = await import('node:crypto');
    const content = 'test content';
    const checksum = crypto.createHash('sha256').update(content).digest('hex');
    
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), content);

    const result = await validator.validateArtifactChecksums(testDir, {
      'artifact1.jar': checksum,
    });

    expect(result.status).toBe('valid');
  });

  it('should detect checksum mismatch', async () => {
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), 'content');

    const result = await validator.validateArtifactChecksums(testDir, {
      'artifact1.jar': 'wrongchecksum',
    });

    expect(result.status).toBe('invalid');
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('should validate artifact size constraints', async () => {
    const content = 'x'.repeat(1000);
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), content);

    const result = await validator.validateArtifactSizes(testDir, {
      'artifact1.jar': { min: 500, max: 2000 },
    });

    expect(result.status).toBe('valid');
  });

  it('should detect artifact too small', async () => {
    const content = 'x'.repeat(100);
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), content);

    const result = await validator.validateArtifactSizes(testDir, {
      'artifact1.jar': { min: 500 },
    });

    expect(result.status).toBe('invalid');
    expect(result.errors.some((e) => e.message.includes('too small'))).toBe(true);
  });

  it('should detect artifact too large', async () => {
    const content = 'x'.repeat(10000);
    await fs.writeFile(path.join(testDir, 'artifact1.jar'), content);

    const result = await validator.validateArtifactSizes(testDir, {
      'artifact1.jar': { max: 1000 },
    });

    expect(result.status).toBe('invalid');
    expect(result.errors.some((e) => e.message.includes('too large'))).toBe(true);
  });
});
