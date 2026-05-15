import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { promises as fs } from 'node:fs';
import * as path from 'node:path';
import { PnpmViteReactAdapter } from '../PnpmViteReactAdapter.js';
import type { ToolchainAdapterContext } from '../../ToolchainAdapter.js';
import { FakeCommandRunner } from '../../execution/FakeCommandRunner.js';

describe('PnpmViteReactAdapter - static artifact generation', () => {
  let tempDir: string;
  let adapter: PnpmViteReactAdapter;
  let commandRunner: FakeCommandRunner;

  beforeEach(async () => {
    tempDir = await fs.mkdtemp(path.join(process.cwd(), 'test-temp-'));
    // Provide a FakeCommandRunner to avoid spawning real pnpm/docker binaries.
    // The build step runs pnpm build; we return success so artifact detection logic runs.
    commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: '', stderr: '', durationMs: 50 },
    ]);
    adapter = new PnpmViteReactAdapter({ repoRoot: tempDir, commandRunner });
  });

  afterEach(async () => {
    await fs.rm(tempDir, { recursive: true, force: true });
  });

  it('does not build a Docker image when a Dockerfile is present', async () => {
    const packageDir = path.join(tempDir, 'ui');
    await fs.mkdir(packageDir, { recursive: true });
    
    // Create Dockerfile
    await fs.writeFile(path.join(packageDir, 'Dockerfile'), 'FROM node:22');
    
    // Create required package.json and dist
    await fs.writeFile(path.join(packageDir, 'package.json'), JSON.stringify({
      scripts: {
        build: 'vite build',
      },
    }));
    await fs.mkdir(path.join(packageDir, 'dist'), { recursive: true });
    await fs.writeFile(path.join(packageDir, 'dist', 'index.html'), '<html></html>');
    
    // Create build directory for manifest
    await fs.mkdir(path.join(packageDir, 'build'), { recursive: true });

    const context: ToolchainAdapterContext = {
      productId: 'test-product',
      phase: 'package',
      surface: {
        id: 'web',
        type: 'web',
        path: 'ui',
        config: {},
      },
      surfaceConfig: {
        packagePath: 'ui/package.json',
        source: 'ui',
      },
      metadata: {
        version: '1.0.0',
        buildNumber: '123',
        gitCommit: 'abc123',
        gitBranch: 'main',
      },
      outputDir: path.join(tempDir, 'ui'),
      dryRun: false,
      logger: {
        debug: () => {},
        info: () => {},
        warn: () => {},
        error: () => {},
      },
    };

    // Execute package phase
    const result = await adapter.execute(context);

    expect(result.status).toBe('succeeded');

    // Verify manifest was written
    const manifestPath = path.join(packageDir, 'build', 'artifact-manifest.json');
    const manifestExists = await fs.access(manifestPath).then(() => true).catch(() => false);
    expect(manifestExists).toBe(true);

    // Verify manifest contains only the static-web-bundle; container images belong to docker-buildx.
    const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf-8'));
    expect(manifest.artifacts).toBeDefined();
    
    const artifactTypes = manifest.artifacts.map((a: { metadata: { type: string } }) => a.metadata.type);
    expect(artifactTypes).toContain('static-web-bundle');
    expect(artifactTypes).not.toContain('docker-image');
    expect(commandRunner.invocations).toHaveLength(1);
    expect(commandRunner.invocations[0].command).toBe('pnpm');
  });

  it('should only generate static-web-bundle when no Dockerfile present', async () => {
    const packageDir = path.join(tempDir, 'ui');
    await fs.mkdir(packageDir, { recursive: true });
    
    // Create required package.json and dist (no Dockerfile)
    await fs.writeFile(path.join(packageDir, 'package.json'), JSON.stringify({
      scripts: {
        build: 'vite build',
      },
    }));
    await fs.mkdir(path.join(packageDir, 'dist'), { recursive: true });
    await fs.writeFile(path.join(packageDir, 'dist', 'index.html'), '<html></html>');
    
    // Create build directory for manifest
    await fs.mkdir(path.join(packageDir, 'build'), { recursive: true });

    const context: ToolchainAdapterContext = {
      productId: 'test-product',
      phase: 'package',
      surface: {
        id: 'web',
        type: 'web',
        path: 'ui',
        config: {},
      },
      surfaceConfig: {
        packagePath: 'ui/package.json',
        source: 'ui',
      },
      metadata: {
        version: '1.0.0',
        buildNumber: '123',
      },
      outputDir: path.join(tempDir, 'ui'),
      dryRun: false,
      logger: {
        debug: () => {},
        info: () => {},
        warn: () => {},
        error: () => {},
      },
    };

    // Execute package phase
    const result = await adapter.execute(context);

    expect(result.status).toBe('succeeded');

    // Verify manifest was written
    const manifestPath = path.join(packageDir, 'build', 'artifact-manifest.json');
    const manifestExists = await fs.access(manifestPath).then(() => true).catch(() => false);
    expect(manifestExists).toBe(true);

    // Verify manifest contains only static-web-bundle
    const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf-8'));
    expect(manifest.artifacts).toBeDefined();
    
    const artifactTypes = manifest.artifacts.map((a: { metadata: { type: string } }) => a.metadata.type);
    expect(artifactTypes).toContain('static-web-bundle');
    expect(artifactTypes).not.toContain('docker-image');
  });
});
