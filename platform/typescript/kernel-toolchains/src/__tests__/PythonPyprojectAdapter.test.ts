import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { PythonPyprojectAdapter } from '../adapters/PythonPyprojectAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';
import type { AdapterLogger, ToolchainAdapterContext } from '../ToolchainAdapter.js';

describe('PythonPyprojectAdapter', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'python-pyproject-adapter-'));
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('plans configured validation commands for a pyproject package', async () => {
    await writePyproject(repoRoot);
    const adapter = new PythonPyprojectAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'validate';
    context.surfaceConfig.validateCommands = [['py', '-m', 'ruff', 'check', '.'], ['py', '-m', 'mypy', '.']];

    const plan = await adapter.plan(context);

    expect(plan.map((step) => step.command)).toEqual([
      ['py', '-m', 'ruff', 'check', '.'],
      ['py', '-m', 'mypy', '.'],
    ]);
    expect(plan[1].dependsOn).toEqual(['python-validate-1']);
  });

  it('uses pytest for the test phase by default', async () => {
    await writePyproject(repoRoot);
    const adapter = new PythonPyprojectAdapter({ repoRoot });
    const context = createContext(repoRoot);
    context.phase = 'test';

    const plan = await adapter.plan(context);

    expect(plan[0].command).toEqual(['py', '-m', 'pytest']);
  });

  it('executes package builds and emits an artifact manifest when dist exists', async () => {
    await writePyproject(repoRoot);
    await fs.mkdir(path.join(repoRoot, 'products', 'python-service', 'dist'), { recursive: true });
    await fs.writeFile(path.join(repoRoot, 'products', 'python-service', 'dist', 'service-0.1.0.whl'), '');
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'Successfully built', stderr: '', durationMs: 30 },
    ]);
    const adapter = new PythonPyprojectAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.phase = 'package';

    const result = await adapter.execute(context);

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toContain('products/python-service/dist');
    expect(result.manifestRefs?.artifactManifest).toBe('.kernel/artifacts/python-product/backend-api/artifact-manifest.json');
    const manifest = JSON.parse(await fs.readFile(
      path.join(repoRoot, '.kernel', 'artifacts', 'python-product', 'backend-api', 'artifact-manifest.json'),
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
      }>;
    };
    expect(manifest.schemaVersion).toBe('1.0.0');
    expect(manifest.adapter).toBe('python-pyproject');
    expect(manifest.trustState).toMatchObject({ status: 'verified', validation: 'expected-output-validation' });
    expect(manifest.artifacts[0]).toMatchObject({
      path: 'products/python-service/dist',
      type: 'python-service',
      directory: true,
      fingerprint: { algorithm: 'sha256' },
    });
    expect(manifest.artifacts[0].sizeBytes).toBe(0);
    expect(manifest.artifacts[0].fingerprint.hash).toMatch(/^[a-f0-9]{64}$/);
    expect(commandRunner.invocations[0].command).toBe('py');
    expect(commandRunner.invocations[0].args).toEqual(['-m', 'build']);
  });

  it('fails closed when the Python package artifact is missing', async () => {
    await writePyproject(repoRoot);
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'Successfully built', stderr: '', durationMs: 30 },
    ]);
    const adapter = new PythonPyprojectAdapter({ repoRoot, commandRunner });
    const context = createContext(repoRoot);
    context.phase = 'package';

    const result = await adapter.execute(context);

    expect(result.status).toBe('failed');
    expect(result.failure?.message).toContain('Missing expected output');
  });

  it('preflight blocks when pyproject.toml is missing', async () => {
    const commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: 'Python 3.12.0', stderr: '', durationMs: 1 },
    ]);
    const adapter = new PythonPyprojectAdapter({ repoRoot, commandRunner });

    const result = await adapter.preflight(createContext(repoRoot));

    expect(result.status).toBe('blocked');
    expect(result.blockingIssues.some((issue) => issue.includes('pyproject.toml'))).toBe(true);
  });
});

function createContext(repoRoot: string): ToolchainAdapterContext {
  return {
    productId: 'python-product',
    phase: 'build',
    surface: {
      type: 'backend-api',
      adapter: 'python-pyproject',
      path: 'products/python-service',
    },
    dryRun: false,
    surfaceConfig: {
      pyprojectPath: 'products/python-service/pyproject.toml',
      pythonCommand: 'py',
    },
    phaseConfig: {},
    logger: createLogger(),
    outputDir: path.join(repoRoot, '.kernel', 'artifacts', 'python-product', 'backend-api'),
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

async function writePyproject(repoRoot: string): Promise<void> {
  const packageDir = path.join(repoRoot, 'products', 'python-service');
  await fs.mkdir(packageDir, { recursive: true });
  await fs.writeFile(
    path.join(packageDir, 'pyproject.toml'),
    '[project]\nname = "python-service"\nversion = "0.1.0"\n',
    'utf-8',
  );
}
