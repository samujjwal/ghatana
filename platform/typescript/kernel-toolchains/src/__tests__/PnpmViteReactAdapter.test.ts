import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { PnpmViteReactAdapter } from '../adapters/PnpmViteReactAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('PnpmViteReactAdapter', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'pnpm-adapter-'));
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('plans pnpm execution against the package directory', async () => {
    const adapter = new PnpmViteReactAdapter({ repoRoot });

    const plan = await adapter.plan(createContext(repoRoot));

    expect(plan).toHaveLength(1);
    expect(plan[0].workingDirectory).toBe(repoRoot);
    expect(plan[0].command).toEqual([
      'pnpm',
      '--dir',
      'products/digital-marketing/web',
      'run',
      'build',
    ]);
  });

  it('requires packagePath during planning', async () => {
    const adapter = new PnpmViteReactAdapter({ repoRoot });
    const context = createContext(repoRoot);
    delete context.surfaceConfig.packagePath;

    await expect(adapter.plan(context)).rejects.toThrow('packagePath is required');
  });

  it('returns schema-backed dry-run evidence without executing commands', async () => {
    const commandRunner = new FakeCommandRunner([]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.dryRun = true;

    const result = await adapter.execute(context);

    expect(result.status).toBe('skipped');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.observability).toMatchObject({ commandId: 'pnpm-build', durationMs: 0 });
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('executes successfully when dist output exists', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'dist'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'package.json'), '{"name":"web"}');
    await fs.writeFile(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'dist', 'index.html'), '<html></html>');

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'vite build complete', stderr: '', durationMs: 15 },
    ]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('succeeded');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.observability).toMatchObject({
      commandId: 'pnpm-build',
      exitCode: 0,
      stdoutBytes: 'vite build complete'.length,
      stdoutTruncated: false,
    });
    expect(result.artifacts).toContain('products/digital-marketing/web/dist');
    expect(commandRunner.invocations[0].args).toEqual(['--dir', 'products/digital-marketing/web', 'run', 'build']);
  });

  it('fails closed when required configured output is missing', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'dist'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'package.json'), '{"name":"web"}');
    await fs.writeFile(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'dist', 'index.html'), '<html></html>');

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'vite build complete', stderr: '', durationMs: 15 },
    ]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

    const context = createContext(repoRoot);
    context.surfaceConfig.expectedOutputs = {
      build: ['products/digital-marketing/web/dist/missing.html'],
    };

    const result = await adapter.execute(context);

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing expected output');
  });

  it('preserves command failure cause and truncated output metadata', async () => {
    const longOutput = 'x'.repeat(10_001);
    const commandRunner = new FakeCommandRunner([
      { exitCode: 2, stdout: longOutput, stderr: 'lint failed', durationMs: 15 },
    ]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('failed');
    expect(result.failure?.cause).toBe('lint failed');
    expect(result.steps[0].stdout).toHaveLength(10_000);
    expect(result.observability?.stdoutTruncated).toBe(true);
  });

  it('returns validation details when dist/index.html is missing', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'dist'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'package.json'), '{"name":"web"}');

    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'vite build complete', stderr: '', durationMs: 15 },
    ]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing Vite static entrypoint');
    expect(result.failure?.message).toContain('static-web-bundle');
  });

  it('rejects unsupported phases instead of falling back', async () => {
    const adapter = new PnpmViteReactAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'deploy';

    await expect(adapter.plan(context)).rejects.toThrow('does not support phase deploy');
  });

  describe('dev phase', () => {
    it('skips dist validation and writes processes.json', async () => {
      const outputDir = path.join(repoRoot, '.kernel', 'out', 'dev');
      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'VITE v5 ready', stderr: '', durationMs: 20 },
      ]);
      const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'dev';
      context.outputDir = outputDir;

      const result = await adapter.execute(context);

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toHaveLength(0);

      const processesJson = path.join(outputDir, 'processes.json');
      const raw = await fs.readFile(processesJson, 'utf-8');
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      expect(parsed.schemaVersion).toBe('1.0.0');
      expect(parsed.adapter).toBe('pnpm-vite-react');
      expect(parsed.productId).toBe('digital-marketing');
    });

    it('validateOutputs returns valid for dev phase', async () => {
      const adapter = new PnpmViteReactAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'dev';

      const result = await adapter.validateOutputs(context);
      expect(result.status).toBe('valid');
      expect(result.missingArtifacts).toHaveLength(0);
    });

    it('plans the dev script for dev phase', async () => {
      const adapter = new PnpmViteReactAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'dev';

      const plan = await adapter.plan(context);
      expect(plan[0].command).toEqual(['pnpm', '--dir', 'products/digital-marketing/web', 'run', 'dev']);
    });
  });

  describe('package phase', () => {
    it('throws when expectedArtifactType is container-image', async () => {
      const commandRunner = new FakeCommandRunner([]);
      const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'package';
      context.surfaceConfig.expectedArtifactType = 'container-image';

      await expect(adapter.execute(context)).rejects.toThrow('docker-buildx adapter');
    });

    it('throws when expectedArtifactType is not static-web-bundle', async () => {
      const commandRunner = new FakeCommandRunner([]);
      const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'package';
      context.surfaceConfig.expectedArtifactType = 'wasm-bundle';

      await expect(adapter.execute(context)).rejects.toThrow('static-web-bundle');
    });

    it('succeeds for static-web-bundle artifacts when dist exists', async () => {
      const webDir = path.join(repoRoot, 'products', 'digital-marketing', 'web');
      await fs.mkdir(path.join(webDir, 'dist'), { recursive: true });
      await fs.writeFile(path.join(webDir, 'dist', 'index.html'), '<html></html>');
      await fs.writeFile(path.join(webDir, 'package.json'), '{"name":"web"}');

      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'build complete', stderr: '', durationMs: 10 },
      ]);
      const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'package';

      const result = await adapter.execute(context);
      expect(result.status).toBe('succeeded');
      expect(result.manifestRefs?.artifactManifest).toBe('products/digital-marketing/web/build/artifact-manifest.json');
      expect(result.evidenceRefs).toContain('artifact:products/digital-marketing/web/dist');
    });

    it('packages with an absolute package path', async () => {
      const webDir = path.join(repoRoot, 'products', 'digital-marketing', 'web');
      await fs.mkdir(path.join(webDir, 'dist'), { recursive: true });
      await fs.writeFile(path.join(webDir, 'dist', 'index.html'), '<html></html>');
      await fs.writeFile(path.join(webDir, 'package.json'), '{"name":"web"}');

      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'build complete', stderr: '', durationMs: 10 },
      ]);
      const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'package';
      context.surfaceConfig.packagePath = path.join(webDir, 'package.json');

      const result = await adapter.execute(context);
      expect(result.status).toBe('succeeded');
      expect(result.manifestRefs?.artifactManifest).toBe('products/digital-marketing/web/build/artifact-manifest.json');
    });
  });

  describe('validate phase', () => {
    it('validates package.json and returns no build artifacts', async () => {
      const webDir = path.join(repoRoot, 'products', 'digital-marketing', 'web');
      await fs.mkdir(webDir, { recursive: true });
      await fs.writeFile(path.join(webDir, 'package.json'), '{"name":"web"}');

      const commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: 'lint complete', stderr: '', durationMs: 10 },
      ]);
      const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

      const context = createContext(repoRoot);
      context.phase = 'validate';
      context.surfaceConfig.expectedOutputs = { validate: 'not-an-array' };

      const result = await adapter.execute(context);
      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toHaveLength(0);
    });
  });

  describe('test phase', () => {
    it('uses the configured test script', async () => {
      const adapter = new PnpmViteReactAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'test';
      context.surfaceConfig.testScript = 'test:unit';

      const plan = await adapter.plan(context);
      expect(plan[0].command).toEqual(['pnpm', '--dir', 'products/digital-marketing/web', 'run', 'test:unit']);
    });
  });
});

function createContext(repoRoot: string): ToolchainAdapterContext {
  return {
    productId: 'digital-marketing',
    runId: 'run-123',
    correlationId: 'corr-123',
    phase: 'build',
    surface: {
      type: 'web',
      adapter: 'pnpm-vite-react',
      path: 'products/digital-marketing/web',
    },
    dryRun: false,
    surfaceConfig: {
      packagePath: 'products/digital-marketing/web/package.json',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: path.join(repoRoot, '.kernel', 'artifacts'),
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
