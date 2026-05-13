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
    expect(result.artifacts).toContain('products/digital-marketing/web/dist');
    expect(commandRunner.invocations[0].args).toEqual(['--dir', 'products/digital-marketing/web', 'run', 'build']);
  });
});

function createContext(repoRoot: string): ToolchainAdapterContext {
  return {
    productId: 'digital-marketing',
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