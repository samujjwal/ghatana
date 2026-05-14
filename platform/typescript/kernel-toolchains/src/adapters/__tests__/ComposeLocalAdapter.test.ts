import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComposeLocalAdapter } from '../ComposeLocalAdapter.js';
import type { ToolchainAdapterContext, AdapterLogger } from '../../ToolchainAdapter.js';
import { FakeCommandRunner } from '../../execution/FakeCommandRunner.js';

// Mock fs.access so validatePrerequisites succeeds in unit tests.
// ComposeLocalAdapter imports { promises as fs } from 'node:fs'.
vi.mock('node:fs', async (importOriginal: () => Promise<Record<string, unknown>>) => {
  const actual = await importOriginal();
  const promises = (actual['promises'] ?? {}) as Record<string, unknown>;
  return {
    ...actual,
    promises: {
      ...promises,
      access: vi.fn().mockResolvedValue(undefined),
      mkdir: vi.fn().mockResolvedValue(undefined),
      writeFile: vi.fn().mockResolvedValue(undefined),
    },
  };
});

const noop = (): void => {};

function makeLogger(): AdapterLogger {
  return {
    info: noop,
    warn: noop,
    error: noop,
    debug: noop,
  };
}

function makeContext(
  overrides: Partial<ToolchainAdapterContext> = {},
): ToolchainAdapterContext {
  return {
    productId: 'digital-marketing',
    phase: 'deploy',
    surface: {
      type: 'backend-api',
      adapter: 'compose-local',
      path: 'products/digital-marketing/dm-api',
    },
    dryRun: false,
    surfaceConfig: {
      composeFile: 'products/digital-marketing/deploy/local.compose.yaml',
    },
    phaseConfig: {},
    logger: makeLogger(),
    ...overrides,
  };
}

describe('ComposeLocalAdapter', () => {
  let commandRunner: FakeCommandRunner;
  let adapter: ComposeLocalAdapter;

  beforeEach(() => {
    commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: '', stderr: '', durationMs: 50 },
    ]);
    adapter = new ComposeLocalAdapter({ repoRoot: '/repo', commandRunner });
  });

  describe('identity', () => {
    it('has id compose-local', () => {
      expect(adapter.id).toBe('compose-local');
    });

    it('supports deploy / verify / rollback / operate phases', () => {
      expect(adapter.supportedPhases).toEqual(
        expect.arrayContaining(['deploy', 'verify', 'rollback', 'operate']),
      );
    });

    it('supports backend-api, web, worker, operator surface types', () => {
      expect(adapter.supportedSurfaceTypes).toEqual(
        expect.arrayContaining(['backend-api', 'web', 'worker', 'operator']),
      );
    });
  });

  describe('plan()', () => {
    it('generates docker compose up -d for deploy', async () => {
      const ctx = makeContext({ phase: 'deploy' });
      const [step] = await adapter.plan(ctx);

      expect(step.command).toEqual(
        expect.arrayContaining(['docker', 'compose', '-f', expect.stringContaining('local.compose.yaml'), 'up', '-d']),
      );
    });

    it('generates docker compose down for rollback', async () => {
      const ctx = makeContext({ phase: 'rollback' });
      const [step] = await adapter.plan(ctx);

      expect(step.command).toContain('down');
    });

    it('includes --env-file when envFile is configured', async () => {
      const ctx = makeContext({
        phase: 'deploy',
        surfaceConfig: {
          composeFile: 'products/digital-marketing/deploy/local.compose.yaml',
          envFile: 'products/digital-marketing/deploy/local.env',
        },
      });
      const [step] = await adapter.plan(ctx);

      expect(step.command).toContain('--env-file');
    });

    it('does not include --env-file when no envFile is configured', async () => {
      const ctx = makeContext({ phase: 'deploy' });
      const [step] = await adapter.plan(ctx);

      expect(step.command).not.toContain('--env-file');
    });

    it('generates ps command for verify plan', async () => {
      const ctx = makeContext({ phase: 'verify' });
      const [step] = await adapter.plan(ctx);

      expect(step.command).toContain('ps');
    });
  });

  describe('execute() — deploy', () => {
    it('calls docker compose up and returns succeeded on exit 0', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'Started', stderr: '', durationMs: 100 },
        // Second call: docker compose ps inside writeDeploymentManifest
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5 },
      ]);
      adapter = new ComposeLocalAdapter({ repoRoot: '/repo', commandRunner });

      const ctx = makeContext({ phase: 'deploy' });
      const result = await adapter.execute(ctx);

      expect(result.status).toBe('succeeded');
      expect(commandRunner.invocations).toHaveLength(2);
      const [inv] = commandRunner.invocations;
      expect(inv.command).toBe('docker');
      expect(inv.args).toContain('up');
    });

    it('returns failed when docker compose exits non-zero', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 1, stdout: '', stderr: 'Error: network not found', durationMs: 50 },
      ]);
      adapter = new ComposeLocalAdapter({ repoRoot: '/repo', commandRunner });

      const ctx = makeContext({ phase: 'deploy' });
      const result = await adapter.execute(ctx);

      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('exit');
    });

    it('skips execution in dryRun mode', async () => {
      const ctx = makeContext({ phase: 'deploy', dryRun: true });
      const result = await adapter.execute(ctx);

      expect(result.status).toBe('skipped');
      expect(commandRunner.invocations).toHaveLength(0);
    });
  });

  describe('execute() — rollback', () => {
    it('calls docker compose down', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 80 },
      ]);
      adapter = new ComposeLocalAdapter({ repoRoot: '/repo', commandRunner });

      const ctx = makeContext({ phase: 'rollback' });
      const result = await adapter.execute(ctx);

      expect(result.status).toBe('succeeded');
      const [inv] = commandRunner.invocations;
      expect(inv.args).toContain('down');
    });
  });

  describe('validateOutputs()', () => {
    it('returns valid status — compose-local validates container state, not config files', async () => {
      // compose-local output validation checks deployment state, not config file existence.
      // Config file existence is a prerequisite checked during execute().
      const ctx = makeContext({
        surfaceConfig: {
          composeFile: '/nonexistent/path/docker-compose.yaml',
        },
      });

      const validation = await adapter.validateOutputs(ctx);

      expect(validation.status).toBe('valid');
      expect(Array.isArray(validation.errors)).toBe(true);
      expect(Array.isArray(validation.missingArtifacts)).toBe(true);
    });
  });
});


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
      productId: 'test-product',
      phase: 'deploy',
      surface: {
        type: 'backend-api',
        adapter: 'compose-local',
        path: 'products/test-product/dm-api',
      },
      surfaceConfig: {
        id: 'backend-api',
        type: 'backend-api',
        composeFile: 'docker-compose.yaml',
      },
      phaseConfig: {},
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
      expect(plan[0].id).toContain('compose-deploy');
      expect(plan[0].description).toContain('up');
      expect(plan[0].command).toContain('docker');
      expect(plan[0].command).toContain('compose');
      expect(plan[0].command).toContain('up');
      expect(plan[0].command.join(' ')).toContain('docker-compose.yaml');
    });

    it('should generate correct plan for rollback phase', async () => {
      context.phase = 'rollback';
      const plan = await adapter.plan(context);

      expect(plan).toHaveLength(1);
      expect(plan[0].id).toContain('compose-rollback');
      expect(plan[0].command.join(' ')).toContain('docker-compose.yaml');
      expect(plan[0].command).toContain('down');
    });

    it('should generate correct plan for operate phase', async () => {
      context.phase = 'operate';
      const plan = await adapter.plan(context);

      expect(plan).toHaveLength(1);
      expect(plan[0].command).toContain('ps');
      expect(plan[0].command.join(' ')).toContain('docker-compose.yaml');
    });

    it('should use default compose file if not specified', async () => {
      context.surfaceConfig = { id: 'web', type: 'web' };
      const plan = await adapter.plan(context);

      expect(plan[0].command.join(' ')).toContain('docker-compose.yaml');
    });

    it('should use custom compose file if specified', async () => {
      context.surfaceConfig = {
        id: 'backend-api',
        type: 'backend-api',
        composeFile: 'custom-compose.yml',
      };
      const plan = await adapter.plan(context);

      expect(plan[0].command.join(' ')).toContain('custom-compose.yml');
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
