import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ArtifactManifestGenerator, type ProductArtifactManifest } from '../ArtifactManifestGenerator';
import type { ToolchainAdapterContext } from '../../ToolchainAdapter';

describe('ArtifactManifestGenerator', () => {
  let context: ToolchainAdapterContext;
  let mockLogger: any;

  beforeEach(() => {
    mockLogger = {
      info: vi.fn(),
      error: vi.fn(),
      debug: vi.fn(),
      warn: vi.fn(),
    };

    context = {
      productId: 'test-product',
      phase: 'package',
      surface: {
        id: 'web',
        type: 'web',
        adapter: 'pnpm-vite-react',
        path: 'products/test-product/ui',
      },
      outputDir: '/tmp/test-output',
      dryRun: false,
      logger: mockLogger,
      metadata: {
        version: '1.0.0',
        buildNumber: '42',
        gitCommit: 'abc123def456',
        gitBranch: 'main',
      },
      surfaceConfig: {},
      phaseConfig: {},
    };
  });

  describe('generateManifest()', () => {
    it('should create manifest with correct schema version', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, []);

      expect(manifest.schemaVersion).toBe('1.0.0');
    });

    it('should include correct product and phase info', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, []);

      expect(manifest.productId).toBe('test-product');
      expect(manifest.phase).toBe('package');
      expect(manifest.surface).toBe('web');
    });

    it('should include ISO timestamp', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, []);

      expect(manifest.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/);
    });

    it('should generate artifact entries with default IDs', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts).toHaveLength(1);
      expect(manifest.artifacts[0].id).toMatch(/index.html-package/);
    });

    it('should use custom artifact IDs when provided', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/bundle.js', type: 'static-web-bundle', id: 'custom-bundle' },
      ]);

      expect(manifest.artifacts[0].id).toBe('custom-bundle');
    });

    it('should include metadata in artifact entries', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      const artifact = manifest.artifacts[0];
      expect(artifact.metadata.version).toBe('1.0.0');
      expect(artifact.metadata.buildNumber).toBe('42');
      expect(artifact.metadata.gitCommit).toBe('abc123def456');
      expect(artifact.metadata.gitBranch).toBe('main');
      expect(artifact.metadata.type).toBe('static-web-bundle');
    });

    it('should include fingerprint with SHA256', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      const artifact = manifest.artifacts[0];
      expect(artifact.fingerprint.algorithm).toBe('sha256');
      expect(artifact.fingerprint.hash).toBeDefined();
    });

    it('should mark artifact as expected and found status', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      const artifact = manifest.artifacts[0];
      expect(artifact.expected).toBe(true);
      expect(artifact.found).toBeTypeOf('boolean');
    });

    it('should handle multiple artifact types', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
        { path: 'build/test-report.json', type: 'test-report' },
        { path: 'build/coverage.json', type: 'coverage-report' },
      ]);

      expect(manifest.artifacts).toHaveLength(3);
      expect(manifest.artifacts[0].metadata.type).toBe('static-web-bundle');
      expect(manifest.artifacts[1].metadata.type).toBe('test-report');
      expect(manifest.artifacts[2].metadata.type).toBe('coverage-report');
    });

    it('should use default version when metadata not provided', async () => {
      const contextWithoutMetadata: ToolchainAdapterContext = {
        ...context,
        metadata: undefined,
      };

      const manifest = await ArtifactManifestGenerator.generateManifest(contextWithoutMetadata, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts[0].metadata.version).toBe('0.0.0');
    });

    it('should use default surface when not provided', async () => {
      const contextWithoutSurface: ToolchainAdapterContext = {
        ...context,
        surface: undefined as any,
      };

      const manifest = await ArtifactManifestGenerator.generateManifest(contextWithoutSurface, []);

      expect(manifest.surface).toBe('unknown');
    });

    it('should handle relative paths', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts[0].path).toBe('dist/index.html');
    });

    it('should include size in bytes for artifacts', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts[0].metadata.sizeBytes).toBeTypeOf('number');
      expect(manifest.artifacts[0].metadata.sizeBytes).toBeGreaterThanOrEqual(0);
    });

    it('P1-03: should include build command metadata when provided', async () => {
      const contextWithBuildCommand: ToolchainAdapterContext = {
        ...context,
        surfaceConfig: {
          ...context.surfaceConfig,
          buildCommand: 'pnpm build',
        },
      };

      const manifest = await ArtifactManifestGenerator.generateManifest(contextWithBuildCommand, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts[0].metadata.buildCommand).toBe('pnpm build');
    });

    it('P1-03: should include runtime metadata when provided', async () => {
      const contextWithRuntime: ToolchainAdapterContext = {
        ...context,
        surfaceConfig: {
          ...context.surfaceConfig,
          runtime: 'node18',
        },
      };

      const manifest = await ArtifactManifestGenerator.generateManifest(contextWithRuntime, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts[0].metadata.runtime).toBe('node18');
    });

    it('P1-03: should include target metadata when provided', async () => {
      const contextWithTarget: ToolchainAdapterContext = {
        ...context,
        surfaceConfig: {
          ...context.surfaceConfig,
          target: 'esnext',
        },
      };

      const manifest = await ArtifactManifestGenerator.generateManifest(contextWithTarget, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts[0].metadata.target).toBe('esnext');
    });

    it('P1-03: should include language metadata when provided', async () => {
      const contextWithLanguage: ToolchainAdapterContext = {
        ...context,
        surfaceConfig: {
          ...context.surfaceConfig,
          language: 'typescript',
        },
      };

      const manifest = await ArtifactManifestGenerator.generateManifest(contextWithLanguage, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      expect(manifest.artifacts[0].metadata.language).toBe('typescript');
    });

    it('P1-03: should include all enhanced metadata fields when provided', async () => {
      const contextWithAllMetadata: ToolchainAdapterContext = {
        ...context,
        surfaceConfig: {
          ...context.surfaceConfig,
          buildCommand: 'cargo build --release',
          runtime: 'rustc 1.75.0',
          target: 'x86_64-unknown-linux-gnu',
          language: 'rust',
        },
      };

      const manifest = await ArtifactManifestGenerator.generateManifest(contextWithAllMetadata, [
        { path: 'target/release/service', type: 'jar' },
      ]);

      const artifact = manifest.artifacts[0];
      expect(artifact.metadata.buildCommand).toBe('cargo build --release');
      expect(artifact.metadata.runtime).toBe('rustc 1.75.0');
      expect(artifact.metadata.target).toBe('x86_64-unknown-linux-gnu');
      expect(artifact.metadata.language).toBe('rust');
    });
  });

  describe('manifest structure validation', () => {
    it('should produce valid manifest structure', async () => {
      const manifest = await ArtifactManifestGenerator.generateManifest(context, [
        { path: 'dist/index.html', type: 'static-web-bundle' },
      ]);

      // Verify required fields
      expect(manifest).toHaveProperty('schemaVersion');
      expect(manifest).toHaveProperty('productId');
      expect(manifest).toHaveProperty('phase');
      expect(manifest).toHaveProperty('surface');
      expect(manifest).toHaveProperty('timestamp');
      expect(manifest).toHaveProperty('artifacts');

      // Verify artifact structure
      const artifact = manifest.artifacts[0];
      expect(artifact).toHaveProperty('id');
      expect(artifact).toHaveProperty('path');
      expect(artifact).toHaveProperty('metadata');
      expect(artifact).toHaveProperty('fingerprint');
      expect(artifact).toHaveProperty('expected');
      expect(artifact).toHaveProperty('found');
    });
  });

  describe('artifact types', () => {
    it('should support all required artifact types', async () => {
      const types = ['jar', 'war', 'static-web-bundle', 'docker-image', 'npm-package'] as const;

      for (const type of types) {
        const manifest = await ArtifactManifestGenerator.generateManifest(context, [
          { path: `artifact.${type}`, type },
        ]);

        expect(manifest.artifacts[0].metadata.type).toBe(type);
      }
    });
  });
});
