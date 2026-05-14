import { describe, it, expect } from 'vitest';
import * as path from 'node:path';
import * as url from 'node:url';
import { ProductLifecyclePlanner } from '../planning/ProductLifecyclePlanner.js';
import { SurfaceSelector } from '../planning/SurfaceSelector.js';
import { LifecycleProfileResolver } from '../planning/LifecycleProfileResolver.js';
import { GateResolver } from '../planning/GateResolver.js';
import { ArtifactResolver } from '../planning/ArtifactResolver.js';
import { EnvironmentResolver } from '../planning/EnvironmentResolver.js';
import { ToolchainResolver } from '../planning/ToolchainResolver.js';
import { ProductSurface, ProductLifecyclePhase, ProductSurfaceType } from '../domain/ProductLifecyclePhase.js';

const REPO_ROOT = path.join(path.dirname(url.fileURLToPath(import.meta.url)), '../../../../..');

describe('ProductLifecyclePlanner', () => {
  it('should load product configuration', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const config = await planner.loadProductConfig('digital-marketing');

    expect(config.productId).toBe('digital-marketing');
    expect(config.lifecycleProfile).toBe('standard-web-api-product');
    expect(config.surfaces).toHaveProperty('backend-api');
    expect(config.surfaces).toHaveProperty('web');
  });

  it('should plan build phase for digital-marketing', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan('digital-marketing', 'build');

    expect(plan.productId).toBe('digital-marketing');
    expect(plan.phase).toBe('build');
    expect(plan.surfaces.length).toBeGreaterThan(0);
    expect(plan.steps.length).toBeGreaterThan(0);
  });

  it('should plan dev phase in parallel mode', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan('digital-marketing', 'dev');

    expect(plan.surfaces.length).toBeGreaterThan(0);
  });

  it('should throw error for unknown product', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);

    await expect(planner.loadProductConfig('unknown-product')).rejects.toThrow(
      'not found in the canonical registry',
    );
  });
});

describe('SurfaceSelector', () => {
  it('should select all surfaces when no filters provided', () => {
    const selector = new SurfaceSelector();
    const surfaces: Record<string, ProductSurface> = {
      'backend-api': {
        type: 'backend-api' as ProductSurfaceType,
        adapter: 'gradle-java-service',
        path: '/backend',
        implementationStatus: 'implemented',
      },
      web: {
        type: 'web' as ProductSurfaceType,
        adapter: 'pnpm-vite-react',
        path: '/web',
        implementationStatus: 'implemented',
      },
    };

    const selected = selector.select(surfaces);
    expect(selected).toHaveLength(2);
  });

  it('should filter by surface type', () => {
    const selector = new SurfaceSelector();
    const surfaces: Record<string, ProductSurface> = {
      'backend-api': {
        type: 'backend-api' as ProductSurfaceType,
        adapter: 'gradle-java-service',
        path: '/backend',
        implementationStatus: 'implemented',
      },
      web: {
        type: 'web' as ProductSurfaceType,
        adapter: 'pnpm-vite-react',
        path: '/web',
        implementationStatus: 'implemented',
      },
    };

    const selected = selector.select(surfaces, {
      surfaceTypes: ['backend-api' as ProductSurfaceType],
    });
    expect(selected).toHaveLength(1);
    expect(selected[0].type).toBe('backend-api');
  });

  it('should filter by implementation status', () => {
    const selector = new SurfaceSelector();
    const surfaces: Record<string, ProductSurface> = {
      'backend-api': {
        type: 'backend-api' as ProductSurfaceType,
        adapter: 'gradle-java-service',
        path: '/backend',
        implementationStatus: 'implemented',
      },
      web: {
        type: 'web' as ProductSurfaceType,
        adapter: 'pnpm-vite-react',
        path: '/web',
        implementationStatus: 'planned',
      },
    };

    const selected = selector.select(surfaces, {
      implementationStatus: ['implemented'],
    });
    expect(selected).toHaveLength(1);
    expect(selected[0].type).toBe('backend-api');
  });

  it('should validate surface configuration', () => {
    const selector = new SurfaceSelector();
    const surface: ProductSurface = {
      type: 'backend-api' as ProductSurfaceType,
      adapter: 'gradle-java-service',
      path: '/backend',
      implementationStatus: 'implemented',
    };

    const errors = selector.validate(surface);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid surface configuration', () => {
    const selector = new SurfaceSelector();
    const surface = {} as ProductSurface;

    const errors = selector.validate(surface);
    expect(errors.length).toBeGreaterThan(0);
  });
});

describe('GateResolver', () => {
  it('should resolve gates for phase', () => {
    const resolver = new GateResolver();
    const requiredGates = ['security', 'privacy'];
    const optionalGates = ['performance'];

    const gates = resolver.resolve('build' as ProductLifecyclePhase, requiredGates, optionalGates);

    expect(gates).toHaveLength(3);
    expect(gates.filter((g) => g.required)).toHaveLength(2);
    expect(gates.filter((g) => !g.required)).toHaveLength(1);
  });

  it('should validate gate configuration', () => {
    const resolver = new GateResolver();
    const errors = resolver.validate('security-check');
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid gate ID', () => {
    const resolver = new GateResolver();
    const errors = resolver.validate('');
    expect(errors.length).toBeGreaterThan(0);
  });

  it('should check gate requirement for production environment', () => {
    const resolver = new GateResolver();
    const isRequired = resolver.isGateRequiredForEnvironment('security', 'prod');
    expect(isRequired).toBe(true);
  });

  it('should not require production gates for dev environment', () => {
    const resolver = new GateResolver();
    const isRequired = resolver.isGateRequiredForEnvironment('security', 'dev');
    expect(isRequired).toBe(false);
  });
});

describe('ArtifactResolver', () => {
  it('should resolve build artifacts for backend-api surface', () => {
    const resolver = new ArtifactResolver();
    const artifacts = resolver.resolve('build' as ProductLifecyclePhase, 'backend-api' as ProductSurfaceType);

    expect(artifacts.length).toBeGreaterThan(0);
    expect(artifacts.some((a) => a.type === 'jvm-classes')).toBe(true);
  });

  it('should resolve build artifacts for web surface', () => {
    const resolver = new ArtifactResolver();
    const artifacts = resolver.resolve('build' as ProductLifecyclePhase, 'web' as ProductSurfaceType);

    expect(artifacts.length).toBeGreaterThan(0);
    expect(artifacts.some((a) => a.type === 'static-web-bundle')).toBe(true);
  });

  it('should resolve package artifacts for backend-api surface', () => {
    const resolver = new ArtifactResolver();
    const artifacts = resolver.resolve('package' as ProductLifecyclePhase, 'backend-api' as ProductSurfaceType);

    expect(artifacts.length).toBeGreaterThan(0);
    expect(artifacts.some((a) => a.type === 'container-image')).toBe(true);
  });

  it('should validate artifact configuration', () => {
    const resolver = new ArtifactResolver();
    const artifact = { type: 'jar', required: true };
    const errors = resolver.validate(artifact);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid artifact type', () => {
    const resolver = new ArtifactResolver();
    const artifact = { type: '', required: true };
    const errors = resolver.validate(artifact);
    expect(errors.length).toBeGreaterThan(0);
  });
});
