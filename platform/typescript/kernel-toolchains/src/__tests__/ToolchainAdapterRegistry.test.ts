import { describe, it, expect } from 'vitest';
import { ToolchainAdapterRegistry } from '../ToolchainAdapterRegistry.js';
import { GradleJavaServiceAdapter } from '../adapters/GradleJavaServiceAdapter.js';
import { PnpmViteReactAdapter } from '../adapters/PnpmViteReactAdapter.js';

describe('ToolchainAdapterRegistry', () => {
  it('should register and retrieve adapters', () => {
    const registry = new ToolchainAdapterRegistry();
    const gradleAdapter = new GradleJavaServiceAdapter();

    registry.register(gradleAdapter);

    expect(registry.has('gradle-java-service')).toBe(true);
    expect(registry.get('gradle-java-service')).toBe(gradleAdapter);
  });

  it('should load adapter definitions from registry', async () => {
    const registry = new ToolchainAdapterRegistry();
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
