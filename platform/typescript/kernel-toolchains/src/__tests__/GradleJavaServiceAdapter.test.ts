import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { GradleJavaServiceAdapter } from '../adapters/GradleJavaServiceAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('GradleJavaServiceAdapter', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'gradle-adapter-'));
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('plans Gradle execution from the repo root', async () => {
    const adapter = new GradleJavaServiceAdapter({ repoRoot });

    const plan = await adapter.plan(createContext(repoRoot));

    expect(plan).toHaveLength(1);
    expect(plan[0].workingDirectory).toBe(repoRoot);
    expect(plan[0].command).toContain('--no-daemon');
    expect(plan[0].command[1]).toBe(':products:digital-marketing:backend:build');
  });

  it('executes successfully when the expected jar output exists', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toContain('products/digital-marketing/backend/build/libs/digital-marketing.jar');
    expect(commandRunner.invocations).toHaveLength(1);
    expect(commandRunner.invocations[0].options.cwd).toBe(repoRoot);
  });

  it('fails closed when required configured output is missing', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'libs', 'digital-marketing.jar'),
      'jar-content',
    );

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
    ]);
    const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

    const context = createContext(repoRoot);
    context.surfaceConfig.expectedOutputs = {
      build: ['products/digital-marketing/backend/build/libs/missing.jar'],
    };

    const result = await adapter.execute(context);

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing expected output');
  });

  it('rejects unsupported phases instead of falling back', async () => {
    const adapter = new GradleJavaServiceAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'deploy';

    await expect(adapter.plan(context)).rejects.toThrow('does not support phase deploy');
  });

  describe('dev phase', () => {
    it('skips output validation and writes processes.json', async () => {
      const outputDir = path.join(repoRoot, '.kernel', 'out', 'dev');
      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'Started application', stderr: '', durationMs: 50 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'dev';
      context.outputDir = outputDir;

      const result = await adapter.execute(context);

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toHaveLength(0);

      // processes.json should be written
      const processesJson = path.join(outputDir, 'processes.json');
      const raw = await fs.readFile(processesJson, 'utf-8');
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      expect(parsed.schemaVersion).toBe('1.0.0');
      expect(parsed.adapter).toBe('gradle-java-service');
      expect(parsed.productId).toBe('digital-marketing');
    });

    it('validateOutputs returns valid for dev phase without checking disk', async () => {
      const adapter = new GradleJavaServiceAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'dev';

      const result = await adapter.validateOutputs(context);
      expect(result.status).toBe('valid');
      expect(result.missingArtifacts).toHaveLength(0);
    });

    it('plans bootRun task for dev phase', async () => {
      const adapter = new GradleJavaServiceAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'dev';

      const plan = await adapter.plan(context);
      expect(plan[0].command[1]).toBe(':products:digital-marketing:backend:bootRun');
    });
  });

  describe('test phase', () => {
    it('fails when test result directories are missing', async () => {
      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'Tests passed', stderr: '', durationMs: 10 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'test';

      const result = await adapter.execute(context);

      // No test result directories exist -> validation fails
      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('Missing expected output');
    });

    it('succeeds when test result directories exist', async () => {
      await fs.mkdir(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'reports', 'tests'),
        { recursive: true },
      );
      await fs.mkdir(
        path.join(repoRoot, 'products', 'digital-marketing', 'backend', 'build', 'test-results', 'test'),
        { recursive: true },
      );

      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'BUILD SUCCESSFUL', stderr: '', durationMs: 10 },
      ]);
      const adapter = new GradleJavaServiceAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'test';

      const result = await adapter.execute(context);
      expect(result.status).toBe('succeeded');
    });
  });
});

function createContext(repoRoot: string): ToolchainAdapterContext {
  return {
    productId: 'digital-marketing',
    phase: 'build',
    surface: {
      type: 'backend-api',
      adapter: 'gradle-java-service',
      path: 'products/digital-marketing/backend',
    },
    dryRun: false,
    surfaceConfig: {
      gradleModule: ':products:digital-marketing:backend',
      source: 'products/digital-marketing/backend',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: path.join(repoRoot, '.kernel', 'artifacts'),
    metadata: {
      version: '1.0.0',
      gitCommit: 'abcdef0',
      gitBranch: 'main',
    },
  };
}

function createLogger(): AdapterLogger {
  return {
    info: () => undefined,
    warn: () => undefined,
    error: () => undefined,
    debug: () => undefined,
  };
}