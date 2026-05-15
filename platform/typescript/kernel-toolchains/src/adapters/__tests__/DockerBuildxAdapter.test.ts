import { describe, it, expect, beforeEach } from 'vitest';
import { DockerBuildxAdapter } from '../DockerBuildxAdapter.js';
import type { ToolchainAdapterContext, AdapterLogger } from '../../ToolchainAdapter.js';
import { FakeCommandRunner } from '../../execution/FakeCommandRunner.js';

const noop = (): void => {};

function makeLogger(): AdapterLogger {
  return { info: noop, warn: noop, error: noop, debug: noop };
}

function makeContext(overrides: Partial<ToolchainAdapterContext> = {}): ToolchainAdapterContext {
  return {
    productId: 'digital-marketing',
    phase: 'package',
    surface: {
      type: 'backend-api',
      adapter: 'docker-buildx',
      path: 'products/digital-marketing/dm-api',
    },
    dryRun: false,
    surfaceConfig: {
      dockerfile: 'products/digital-marketing/dm-api/Dockerfile',
      context: 'products/digital-marketing/dm-api',
      image: 'ghatana/digital-marketing-api',
      tag: 'local',
    },
    phaseConfig: {},
    logger: makeLogger(),
    ...overrides,
  };
}

describe('DockerBuildxAdapter', () => {
  let commandRunner: FakeCommandRunner;
  let adapter: DockerBuildxAdapter;

  beforeEach(() => {
    commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: '', stderr: '', durationMs: 100 },
      { exitCode: 0, stdout: '[]', stderr: '', durationMs: 10 }, // docker image inspect
    ]);
    adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });
  });

  describe('identity', () => {
    it('has id docker-buildx', () => {
      expect(adapter.id).toBe('docker-buildx');
    });

    it('only supports the package phase', () => {
      expect(adapter.supportedPhases).toEqual(['package']);
    });
  });

  describe('plan()', () => {
    it('generates docker buildx build --load command', async () => {
      const [step] = await adapter.plan(makeContext());

      expect(step.command[0]).toBe('docker');
      expect(step.command).toContain('buildx');
      expect(step.command).toContain('build');
      expect(step.command).toContain('--load');
    });

    it('includes -f <dockerfile> in the command', async () => {
      const [step] = await adapter.plan(makeContext());

      const fIndex = step.command.indexOf('-f');
      expect(fIndex).toBeGreaterThan(-1);
      expect(step.command[fIndex + 1]).toContain('Dockerfile');
    });

    it('includes -t <image>:<tag> in the command', async () => {
      const [step] = await adapter.plan(makeContext());

      const tIndex = step.command.indexOf('-t');
      expect(tIndex).toBeGreaterThan(-1);
      expect(step.command[tIndex + 1]).toBe('ghatana/digital-marketing-api:local');
    });

    it('includes --build-arg pairs when buildArgs are configured', async () => {
      const ctx = makeContext({
        surfaceConfig: {
          dockerfile: 'Dockerfile',
          context: '.',
          image: 'test/img',
          tag: 'v1',
          buildArgs: { NODE_ENV: 'production', VERSION: '1.0' },
        },
      });

      const [step] = await adapter.plan(ctx);

      const buildArgCount = step.command.filter((a) => a === '--build-arg').length;
      expect(buildArgCount).toBe(2);
    });

    it('requires dockerfile, context, image, and tag', async () => {
      const ctx = makeContext({ surfaceConfig: { image: 'test/img', tag: 'v1', context: '.' } });

      await expect(adapter.plan(ctx)).rejects.toThrow('surfaceConfig.dockerfile');
    });
  });

  describe('execute()', () => {
    it('calls docker buildx build and returns succeeded on exit 0', async () => {
      // Need 3 results: build + validateOutputs (inspect) + extractArtifacts (inspect)
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5000 },
        { exitCode: 0, stdout: 'sha256:abc123', stderr: '', durationMs: 10 },
        { exitCode: 0, stdout: 'sha256:abc123', stderr: '', durationMs: 10 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('succeeded');
      expect(result.schemaVersion).toBe('1.0.0');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api:local');
      expect(result.evidenceRefs).toContain('container-image:ghatana/digital-marketing-api:local');
      expect(result.observability).toMatchObject({
        commandId: 'docker-buildx-package-backend-api',
        exitCode: 0,
      });
      expect(commandRunner.invocations[0].command).toBe('docker');
    });

    it('returns digest artifact refs when docker inspect exposes RepoDigests', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5000 },
        {
          exitCode: 0,
          stdout: '["ghatana/digital-marketing-api@sha256:abc123"]|sha256:imageid',
          stderr: '',
          durationMs: 10,
        },
        {
          exitCode: 0,
          stdout: '["ghatana/digital-marketing-api@sha256:abc123"]|sha256:imageid',
          stderr: '',
          durationMs: 10,
        },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api@sha256:abc123');
      expect(result.evidenceRefs).toContain('container-image:ghatana/digital-marketing-api@sha256:abc123');
    });

    it('returns failed when docker buildx exits non-zero', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 1, stdout: '', stderr: 'Build failed', durationMs: 100 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('failed');
      expect(result.failure?.cause).toBe('Build failed');
    });

    it('returns failed when image inspect cannot find the built image', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5000 },
        { exitCode: 1, stdout: '', stderr: 'not found', durationMs: 10 },
        { exitCode: 1, stdout: '', stderr: 'not found', durationMs: 10 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('not found in local Docker daemon');
    });

    it('falls back to image ref when inspect output is empty', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5000 },
        { exitCode: 0, stdout: '', stderr: '', durationMs: 10 },
        { exitCode: 0, stdout: '', stderr: '', durationMs: 10 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('failed');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api:local');
    });

    it('falls back to image ref when inspect digest segment is empty', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5000 },
        { exitCode: 0, stdout: '|sha256:imageid', stderr: '', durationMs: 10 },
        { exitCode: 0, stdout: '|sha256:imageid', stderr: '', durationMs: 10 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api:local');
    });

    it('falls back to image ref when inspect digest payload is not an array', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5000 },
        { exitCode: 0, stdout: '{"Id":"sha256:imageid"}|sha256:imageid', stderr: '', durationMs: 10 },
        { exitCode: 0, stdout: '{"Id":"sha256:imageid"}|sha256:imageid', stderr: '', durationMs: 10 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot: '/repo', commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api:local');
    });

    it('skips execution in dryRun mode', async () => {
      const result = await adapter.execute(makeContext({ dryRun: true }));

      expect(result.status).toBe('skipped');
      expect(commandRunner.invocations).toHaveLength(0);
    });
  });
});
