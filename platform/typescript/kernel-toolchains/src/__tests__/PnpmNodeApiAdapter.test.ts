import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { PnpmNodeApiAdapter } from '../adapters/PnpmNodeApiAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('PnpmNodeApiAdapter', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'pnpm-node-api-adapter-'));
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('plans pnpm execution against a Node service package directory', async () => {
    await writePackageJson(repoRoot);
    const adapter = new PnpmNodeApiAdapter({ repoRoot });

    const plan = await adapter.plan(createContext(repoRoot));

    expect(plan[0].command).toEqual(['pnpm', '--dir', 'products/node-api', 'run', 'build']);
    expect(plan[0].workingDirectory).toBe(repoRoot);
  });

  it('prefers typecheck for validate when available', async () => {
    await writePackageJson(repoRoot);
    const adapter = new PnpmNodeApiAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'validate';

    const plan = await adapter.plan(context);

    expect(plan[0].command).toEqual(['pnpm', '--dir', 'products/node-api', 'run', 'typecheck']);
  });

  it('executes builds and emits a node-bundle artifact manifest', async () => {
    await writePackageJson(repoRoot);
    await fs.mkdir(path.join(repoRoot, 'products', 'node-api', 'dist'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'node-api', 'dist', 'index.js'), '');
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'tsc complete', stderr: '', durationMs: 16 },
    ]);
    const adapter = new PnpmNodeApiAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toContain('products/node-api/dist');
    expect(result.manifestRefs?.artifactManifest).toBe('.kernel/artifacts/node-product/backend-api/artifact-manifest.json');
    const manifest = JSON.parse(await fs.readFile(
      path.join(repoRoot, '.kernel', 'artifacts', 'node-product', 'backend-api', 'artifact-manifest.json'),
      'utf-8',
    )) as {
      schemaVersion: string;
      adapter: string;
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
    expect(manifest.adapter).toBe('pnpm-node-api');
    expect(manifest.trustState).toMatchObject({ status: 'verified', validation: 'expected-output-validation' });
    expect(manifest.artifacts[0]).toMatchObject({
      path: 'products/node-api/dist',
      type: 'node-bundle',
      directory: true,
      fingerprint: { algorithm: 'sha256' },
      metadata: { adapter: 'pnpm-node-api', sourcePath: 'products/node-api' },
    });
    expect(manifest.artifacts[0].sizeBytes).toBe(0);
    expect(manifest.artifacts[0].fingerprint.hash).toMatch(/^[a-f0-9]{64}$/);
    expect(commandRunner.invocations[0].args).toEqual(['--dir', 'products/node-api', 'run', 'build']);
  });

  it('fails before command execution when the package script is missing', async () => {
    await writePackageJson(repoRoot, { build: undefined });
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: '', stderr: '', durationMs: 1 },
    ]);
    const adapter = new PnpmNodeApiAdapter({ repoRoot, commandRunner });

    await expect(adapter.execute(createContext(repoRoot))).rejects.toThrow('script-not-found');
    expect(commandRunner.invocations).toHaveLength(0);
  });

  it('fails closed when dist output is missing after a successful build command', async () => {
    await writePackageJson(repoRoot);
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'tsc complete', stderr: '', durationMs: 16 },
    ]);
    const adapter = new PnpmNodeApiAdapter({ repoRoot, commandRunner });

    const result = await adapter.execute(createContext(repoRoot));

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing expected output');
  });
});

function createContext(repoRoot: string): ToolchainAdapterContext {
  return {
    productId: 'node-product',
    phase: 'build',
    surface: {
      type: 'backend-api',
      adapter: 'pnpm-node-api',
      path: 'products/node-api',
    },
    dryRun: false,
    surfaceConfig: {
      packagePath: 'products/node-api/package.json',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: path.join(repoRoot, '.kernel', 'artifacts', 'node-product', 'backend-api'),
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

async function writePackageJson(
  repoRoot: string,
  scriptOverrides: Record<string, string | undefined> = {},
): Promise<void> {
  const packageDir = path.join(repoRoot, 'products', 'node-api');
  await fs.mkdir(packageDir, { recursive: true });
  const scripts: Record<string, string> = {
    build: 'tsc',
    dev: 'tsx watch src/index.ts',
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
    path.join(packageDir, 'package.json'),
    JSON.stringify({ name: 'node-api', scripts }),
    'utf-8',
  );
}
