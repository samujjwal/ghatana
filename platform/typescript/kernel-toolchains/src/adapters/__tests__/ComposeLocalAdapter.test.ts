import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComposeLocalAdapter } from '../adapters/ComposeLocalAdapter';
import type { ToolchainAdapterContext } from '../ToolchainAdapter';

describe('ComposeLocalAdapter', () => {
  let adapter: ComposeLocalAdapter;
  let context: ToolchainAdapterContext;
  let mockLogger: any;

  beforeEach(() => {
    adapter = new ComposeLocalAdapter();
    mockLogger = {
      info: vi.fn(),
      error: vi.fn(),
      debug: vi.fn(),
      warn: vi.fn(),
    };

    context = {
      phase: 'deploy',
      surfaceConfig: {
        id: 'backend-api',
        type: 'backend-api',
        composeFile: 'docker-compose.yaml',
      },
      outputDir: '/tmp/test',
      dryRun: false,
      logger: mockLogger,
    };
  });

  describe('initialization', () => {
    it('should have correct id', () => {
      expect(adapter.id).toBe('compose-local');
    });

    it('should support deploy, operate, and rollback phases', () => {
      expect(adapter.supportedPhases).toContain('deploy');
      expect(adapter.supportedPhases).toContain('operate');
      expect(adapter.supportedPhases).toContain('rollback');
    });

    it('should support backend-api, web, worker, and operator surface types', () => {
      expect(adapter.supportedSurfaceTypes).toContain('backend-api');
      expect(adapter.supportedSurfaceTypes).toContain('web');
      expect(adapter.supportedSurfaceTypes).toContain('worker');
      expect(adapter.supportedSurfaceTypes).toContain('operator');
    });
  });

  describe('plan()', () => {
    it('should generate correct plan for deploy phase', async () => {
      context.phase = 'deploy';
      const plan = await adapter.plan(context);

      expect(plan).toHaveLength(1);
      expect(plan[0].id).toBe('compose-deploy');
      expect(plan[0].description).toContain('up');
      expect(plan[0].command).toEqual(['docker', 'compose', '-f', 'docker-compose.yaml', 'up', '-d']);
      expect(plan[0].workingDirectory).toBe('/tmp/test');
    });

    it('should generate correct plan for rollback phase', async () => {
      context.phase = 'rollback';
      const plan = await adapter.plan(context);

      expect(plan).toHaveLength(1);
      expect(plan[0].id).toBe('compose-rollback');
      expect(plan[0].command).toEqual(['docker', 'compose', '-f', 'docker-compose.yaml', 'down']);
    });

    it('should generate correct plan for operate phase', async () => {
      context.phase = 'operate';
      const plan = await adapter.plan(context);

      expect(plan).toHaveLength(1);
      expect(plan[0].command).toEqual(['docker', 'compose', '-f', 'docker-compose.yaml', 'ps']);
    });

    it('should use default compose file if not specified', async () => {
      context.surfaceConfig = { id: 'web', type: 'web' };
      const plan = await adapter.plan(context);

      expect(plan[0].command).toContain('docker-compose.yaml');
    });

    it('should use custom compose file if specified', async () => {
      context.surfaceConfig = {
        id: 'backend-api',
        type: 'backend-api',
        composeFile: 'custom-compose.yml',
      };
      const plan = await adapter.plan(context);

      expect(plan[0].command).toContain('custom-compose.yml');
    });

    it('should use current directory as working directory if not specified', async () => {
      context.outputDir = undefined;
      const plan = await adapter.plan(context);

      expect(plan[0].workingDirectory).toBe(process.cwd());
    });
  });

  describe('execute()', () => {
    it('should skip execution in dry-run mode', async () => {
      context.dryRun = true;
      const result = await adapter.execute(context);

      expect(result.status).toBe('skipped');
      expect(result.steps).toHaveLength(1);
      expect(result.steps[0].status).toBe('skipped');
      expect(mockLogger.info).toHaveBeenCalledWith(expect.stringContaining('[DRY-RUN]'));
    });

    it('should log execution start', async () => {
      context.dryRun = true;
      await adapter.execute(context);

      expect(mockLogger.info).toHaveBeenCalled();
    });
  });

  describe('validateOutputs()', () => {
    it('should return valid status', async () => {
      const result = await adapter.validateOutputs(context);

      expect(result.status).toBe('valid');
      expect(Array.isArray(result.errors)).toBe(true);
      expect(Array.isArray(result.missingArtifacts)).toBe(true);
      expect(Array.isArray(result.unexpectedArtifacts)).toBe(true);
    });

    it('should log validation debug info', async () => {
      await adapter.validateOutputs(context);

      expect(mockLogger.debug).toHaveBeenCalled();
    });
  });

  describe('phase mapping', () => {
    it('should map all supported phases to valid arguments', async () => {
      const phases = ['deploy', 'operate', 'rollback', 'dev', 'validate', 'test', 'build', 'package', 'release', 'verify', 'promote', 'retire', 'create', 'bootstrap'] as const;

      for (const phase of phases) {
        context.phase = phase;
        const plan = await adapter.plan(context);
        expect(plan).toHaveLength(1);
        expect(plan[0].command.length).toBeGreaterThan(0);
      }
    });
  });

  describe('command safety', () => {
    it('should use command arrays instead of shell strings', async () => {
      const plan = await adapter.plan(context);

      // Verify command is an array, not a string with spaces
      expect(Array.isArray(plan[0].command)).toBe(true);
      expect(typeof plan[0].command).not.toBe('string');

      // Verify first element is executable
      expect(plan[0].command[0]).toBe('docker');

      // Verify arguments are separate array elements
      expect(plan[0].command).toContain('compose');
    });
  });
});
