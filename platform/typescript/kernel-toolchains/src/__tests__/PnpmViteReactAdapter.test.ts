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
    await writeWebPackageJson(repoRoot);

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

  it('preflights package.json and lifecycle script readiness', async () => {
    await writeWebPackageJson(repoRoot);
    const adapter = new PnpmViteReactAdapter({ repoRoot });

    const result = await adapter.preflight(createContext(repoRoot));

    expect(result.status).toBe('ready');
    expect(result.blockingIssues).toHaveLength(0);
    expect(result.checks.map((check) => check.checkId)).toEqual([
      'pnpm-package-json',
      'pnpm-package-script',
    ]);
  });

  it('blocks preflight when packagePath is missing or the lifecycle script is absent', async () => {
    await writeWebPackageJson(repoRoot, { build: undefined });
    const adapter = new PnpmViteReactAdapter({ repoRoot });
    const missingConfigContext = createContext(repoRoot);
    delete missingConfigContext.surfaceConfig.packagePath;

    const missingConfig = await adapter.preflight(missingConfigContext);
    const missingScript = await adapter.preflight(createContext(repoRoot));

    expect(missingConfig.status).toBe('blocked');
    expect(missingConfig.blockingIssues).toContainEqual(expect.stringContaining('pnpm-package-path-config'));
    expect(missingScript.status).toBe('blocked');
    expect(missingScript.blockingIssues).toContainEqual(expect.stringContaining('pnpm-package-script'));
  });

  it('classifies package configuration failures', async () => {
    const adapter = new PnpmViteReactAdapter({ repoRoot });

    const classification = await adapter.classifyFailure(
      new Error('script-not-found: package does not define script "build"'),
      createContext(repoRoot),
    );

    expect(classification.category).toBe('config');
    expect(classification.relatedFailureCodes).toContain('pnpm-vite-react-package-config');
    expect(classification.requiresHumanIntervention).toBe(true);
  });

  it('returns schema-backed dry-run evidence without executing commands', async () => {
    const commandRunner = new FakeCommandRunner([]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.dryRun = true;
    await writeWebPackageJson(repoRoot);

    const result = await adapter.execute(context);

    expect(result.status).toBe('skipped');
    expect(result.schemaVersion).toBe('1.0.0');
    expect(result.observability).toMatchObject({ commandId: 'pnpm-build', durationMs: 0 });
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('executes successfully when dist output exists', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'dist'), { recursive: true });
    await writeWebPackageJson(repoRoot);
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
    await writeWebPackageJson(repoRoot);
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
    await writeWebPackageJson(repoRoot);

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('failed');
    expect(result.failure?.cause).toBe('lint failed');
    expect(result.steps[0].stdout).toHaveLength(10_000);
    expect(result.observability?.stdoutTruncated).toBe(true);
  });

  it('returns validation details when dist/index.html is missing', async () => {
    await fs.mkdir(path.join(repoRoot, 'products', 'digital-marketing', 'web', 'dist'), { recursive: true });
    await writeWebPackageJson(repoRoot);

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
      await writeWebPackageJson(repoRoot);

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
      await writeWebPackageJson(repoRoot);

      const result = await adapter.validateOutputs(context);
      expect(result.status).toBe('valid');
      expect(result.missingArtifacts).toHaveLength(0);
    });

    it('plans the dev script for dev phase', async () => {
      const adapter = new PnpmViteReactAdapter({ repoRoot });
      const context = createContext(repoRoot);
      context.phase = 'dev';
      await writeWebPackageJson(repoRoot);

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
      await writeWebPackageJson(repoRoot);

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
      const manifest = JSON.parse(await fs.readFile(
        path.join(webDir, 'build', 'artifact-manifest.json'),
        'utf-8',
      )) as {
        schemaVersion: string;
        adapter: string;
        source: { path: string };
        trustState: { status: string; validation: string };
        artifacts: Array<{
          path: string;
          type: string;
          directory: boolean;
          sizeBytes: number;
          fingerprint: { algorithm: string; hash: string };
          metadata: { adapter: string; sourcePath: string };
        }>;
      };
      expect(manifest.schemaVersion).toBe('1.0.0');
      expect(manifest.adapter).toBe('pnpm-vite-react');
      expect(manifest.source.path).toBe('products/digital-marketing/web');
      expect(manifest.trustState).toMatchObject({ status: 'verified', validation: 'expected-output-validation' });
      expect(manifest.artifacts[0]).toMatchObject({
        path: 'products/digital-marketing/web/dist',
        type: 'static-web-bundle',
        directory: true,
        fingerprint: { algorithm: 'sha256' },
        metadata: { adapter: 'pnpm-vite-react', sourcePath: 'products/digital-marketing/web' },
      });
      expect(manifest.artifacts[0].sizeBytes).toBe('<html></html>'.length);
      expect(manifest.artifacts[0].fingerprint.hash).toMatch(/^[a-f0-9]{64}$/);
    });

    it('packages with an absolute package path', async () => {
      const webDir = path.join(repoRoot, 'products', 'digital-marketing', 'web');
      await fs.mkdir(path.join(webDir, 'dist'), { recursive: true });
      await fs.writeFile(path.join(webDir, 'dist', 'index.html'), '<html></html>');
      await writeWebPackageJson(repoRoot);

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
      await writeWebPackageJson(repoRoot);

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
      await writeWebPackageJson(repoRoot, { 'test:unit': 'vitest run' });

      const plan = await adapter.plan(context);
      expect(plan[0].command).toEqual(['pnpm', '--dir', 'products/digital-marketing/web', 'run', 'test:unit']);
    });
  });

  it('fails before command execution when package path is missing', async () => {
    const commandRunner = new FakeCommandRunner([{ exitCode: 0, stdout: '', stderr: '', durationMs: 1 }]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

    await expect(adapter.execute(createContext(repoRoot))).rejects.toThrow('package-path-not-found');
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('fails before command execution when the required package script is missing', async () => {
    await writeWebPackageJson(repoRoot, { build: undefined });
    const commandRunner = new FakeCommandRunner([{ exitCode: 0, stdout: '', stderr: '', durationMs: 1 }]);
    const adapter = new PnpmViteReactAdapter({ repoRoot, commandRunner });

    await expect(adapter.execute(createContext(repoRoot))).rejects.toThrow('script-not-found');
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('prefers typecheck for validate when no validateScript is configured', async () => {
    await writeWebPackageJson(repoRoot);
    const adapter = new PnpmViteReactAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'validate';

    const plan = await adapter.plan(context);

    expect(plan[0].command).toEqual(['pnpm', '--dir', 'products/digital-marketing/web', 'run', 'typecheck']);
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

async function writeWebPackageJson(
  repoRoot: string,
  scriptOverrides: Record<string, string | undefined> = {},
): Promise<void> {
  const webDir = path.join(repoRoot, 'products', 'digital-marketing', 'web');
  await fs.mkdir(webDir, { recursive: true });
  const scripts: Record<string, string> = {
    build: 'vite build',
    dev: 'vite dev',
    lint: 'eslint .',
    test: 'vitest run',
    typecheck: 'tsc --noEmit',
  };
  for (const [script, command] of Object.entries(scriptOverrides)) {
    if (command === undefined) {
      delete scripts[script];
    } else {
      scripts[script] = command;
    }
  }
  await fs.writeFile(
    path.join(webDir, 'package.json'),
    JSON.stringify({ name: 'web', scripts }),
    'utf-8',
  );
}
