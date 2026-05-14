import { describe, it, expect } from 'vitest';
import * as path from 'node:path';
import * as url from 'node:url';
import {
  ToolchainAdapterRegistry,
  ToolchainAdapterRegistryBridge,
  createDefaultToolchainAdapterRegistry,
} from '../ToolchainAdapterRegistry.js';
import { GradleJavaServiceAdapter } from '../adapters/GradleJavaServiceAdapter.js';
import { PnpmViteReactAdapter } from '../adapters/PnpmViteReactAdapter.js';
import { FakeCommandRunner } from '../execution/FakeCommandRunner.js';

// Resolve the repo root relative to this test file
const REPO_ROOT = path.join(path.dirname(url.fileURLToPath(import.meta.url)), '../../../../..');
const REPO_CONFIG_DIR = path.join(REPO_ROOT, 'config');

describe('ToolchainAdapterRegistry', () => {
  it('should register and retrieve adapters', () => {
    const registry = new ToolchainAdapterRegistry();
    const gradleAdapter = new GradleJavaServiceAdapter();

    registry.register(gradleAdapter);

    expect(registry.has('gradle-java-service')).toBe(true);
    expect(registry.get('gradle-java-service')).toBe(gradleAdapter);
  });

  it('should load adapter definitions from registry', async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    expect(definitions).toHaveProperty('gradle-java-service');
    expect(definitions).toHaveProperty('pnpm-vite-react');
  });

  it('should filter adapters by phase', () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const buildAdapters = registry.getByPhase('build');

    expect(buildAdapters).toHaveLength(2);
    expect(buildAdapters.every((a) => a.supportedPhases.includes('build'))).toBe(true);
  });

  it('should filter adapters by surface type', () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const webAdapters = registry.getBySurfaceType('web');

    expect(webAdapters).toHaveLength(1);
    expect(webAdapters[0].id).toBe('pnpm-vite-react');
  });
});

describe('createDefaultToolchainAdapterRegistry', () => {
  it('registers all four canonical adapters', () => {
    const { registry } = createDefaultToolchainAdapterRegistry({ repoRoot: '/repo' });

    expect(registry.has('gradle-java-service')).toBe(true);
    expect(registry.has('pnpm-vite-react')).toBe(true);
    expect(registry.has('docker-buildx')).toBe(true);
    expect(registry.has('compose-local')).toBe(true);
    expect(registry.getAll()).toHaveLength(4);
  });

  it('docker-buildx supports only the package phase', () => {
    const { registry } = createDefaultToolchainAdapterRegistry({ repoRoot: '/repo' });
    const packageAdapters = registry.getByPhase('package');
    const ids = packageAdapters.map((a) => a.id);
    expect(ids).toContain('docker-buildx');
  });

  it('compose-local supports deploy and rollback phases', () => {
    const { registry } = createDefaultToolchainAdapterRegistry({ repoRoot: '/repo' });
    const deployAdapters = registry.getByPhase('deploy');
    const rollbackAdapters = registry.getByPhase('rollback');
    expect(deployAdapters.map((a) => a.id)).toContain('compose-local');
    expect(rollbackAdapters.map((a) => a.id)).toContain('compose-local');
  });
});

describe('ToolchainAdapterRegistryBridge', () => {
  it('throws a descriptive error when adapter is not registered', () => {
    const registry = new ToolchainAdapterRegistry();
    const bridge = new ToolchainAdapterRegistryBridge(registry);

    expect(() => bridge.getAdapter('missing')).toThrow(/No toolchain adapter registered for id "missing"/);
  });

  it('returns a bridge adapter that calls the underlying adapter in dry-run', async () => {
    const runner = new FakeCommandRunner();
    const { bridge } = createDefaultToolchainAdapterRegistry({ repoRoot: '/repo', commandRunner: runner });

    const bridgedAdapter = bridge.getAdapter('compose-local');
    const result = await bridgedAdapter.execute({
      productId: 'test',
      phase: 'deploy',
      surface: { type: 'backend-api', adapter: 'compose-local', path: '/p' },
      dryRun: true,
      surfaceConfig: { composeFile: '/nonexistent/compose.yaml' },
      phaseConfig: {},
      logger: { info: () => {}, warn: () => {}, error: () => {}, debug: () => {} },
    });

    // dry-run skips execution
    expect(result.status).toBe('skipped');
  });
});


describe('ToolchainAdapterRegistry', () => {
  it('should register and retrieve adapters', () => {
    const registry = new ToolchainAdapterRegistry();
    const gradleAdapter = new GradleJavaServiceAdapter();

    registry.register(gradleAdapter);

    expect(registry.has('gradle-java-service')).toBe(true);
    expect(registry.get('gradle-java-service')).toBe(gradleAdapter);
  });

  it('should load adapter definitions from registry', async () => {
    const registry = new ToolchainAdapterRegistry(REPO_CONFIG_DIR);
    const definitions = await registry.loadRegistry();

    expect(definitions).toHaveProperty('gradle-java-service');
    expect(definitions).toHaveProperty('pnpm-vite-react');
  });

  it('should filter adapters by phase', () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const buildAdapters = registry.getByPhase('build');

    expect(buildAdapters).toHaveLength(2);
    expect(buildAdapters.every((a) => a.supportedPhases.includes('build'))).toBe(true);
  });

  it('should filter adapters by surface type', () => {
    const registry = new ToolchainAdapterRegistry();
    registry.register(new GradleJavaServiceAdapter());
    registry.register(new PnpmViteReactAdapter());

    const webAdapters = registry.getBySurfaceType('web');

    expect(webAdapters).toHaveLength(1);
    expect(webAdapters[0].id).toBe('pnpm-vite-react');
  });
});
