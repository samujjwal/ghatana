import * as fs from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
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
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'ghatana-docker-buildx-'));
    await fs.mkdir(path.join(repoRoot, 'products/digital-marketing/dm-api'), { recursive: true });
    await fs.writeFile(
      path.join(repoRoot, 'products/digital-marketing/dm-api/Dockerfile'),
      'FROM scratch\n',
      'utf-8',
    );
    commandRunner = new FakeCommandRunner([
      { exitCode: 0, stdout: '', stderr: '', durationMs: 100 },
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
    adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
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

    it('adds lifecycle labels and declared platforms without inventing platform defaults', async () => {
      const [step] = await adapter.plan(makeContext({
        runId: 'run-1',
        correlationId: 'corr-1',
        surfaceConfig: {
          dockerfile: 'products/digital-marketing/dm-api/Dockerfile',
          context: 'products/digital-marketing/dm-api',
          image: 'ghatana/digital-marketing-api',
          tag: 'local',
          platforms: ['linux/amd64'],
        },
      }));

      expect(step.command).toContain('ghatana.productUnit=digital-marketing');
      expect(step.command).toContain('ghatana.surface=backend-api');
      expect(step.command).toContain('ghatana.kernel.runId=run-1');
      expect(step.command).toContain('ghatana.kernel.correlationId=corr-1');
      expect(step.command).toContain('--platform');
      expect(step.command).toContain('linux/amd64');
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
      adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('succeeded');
      expect(result.schemaVersion).toBe('1.0.0');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api@sha256:abc123');
      expect(result.evidenceRefs).toContain('container-image:ghatana/digital-marketing-api@sha256:abc123');
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
      adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('succeeded');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api@sha256:abc123');
      expect(result.evidenceRefs).toContain('container-image:ghatana/digital-marketing-api@sha256:abc123');
    });

    it('returns failed when docker buildx exits non-zero', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 1, stdout: '', stderr: 'Build failed', durationMs: 100 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });

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
      adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });

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
      adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });

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
      adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('container-image-digest-missing');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api:local');
    });

    it('falls back to image ref when inspect digest payload is not an array', async () => {
      commandRunner = new FakeCommandRunner([
        { exitCode: 0, stdout: '', stderr: '', durationMs: 5000 },
        { exitCode: 0, stdout: '{"Id":"sha256:imageid"}|sha256:imageid', stderr: '', durationMs: 10 },
        { exitCode: 0, stdout: '{"Id":"sha256:imageid"}|sha256:imageid', stderr: '', durationMs: 10 },
      ]);
      adapter = new DockerBuildxAdapter({ repoRoot, commandRunner });

      const result = await adapter.execute(makeContext());

      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('container-image-digest-missing');
      expect(result.artifacts).toContain('ghatana/digital-marketing-api:local');
    });

    it('skips execution in dryRun mode', async () => {
      const result = await adapter.execute(makeContext({ dryRun: true }));

      expect(result.status).toBe('skipped');
      expect(commandRunner.invocations).toHaveLength(0);
    });

    it('fails before execution when Dockerfile or context is missing', async () => {
      const result = await adapter.execute(makeContext({
        surfaceConfig: {
          dockerfile: 'missing/Dockerfile',
          context: 'missing',
          image: 'ghatana/digital-marketing-api',
          tag: 'local',
        },
      }));

      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('surfaceConfig.dockerfile-not-found');
      expect(commandRunner.invocations).toHaveLength(0);
    });

    it('fails explicitly when SBOM export is requested before support is ready', async () => {
      const result = await adapter.execute(makeContext({
        surfaceConfig: {
          dockerfile: 'products/digital-marketing/dm-api/Dockerfile',
          context: 'products/digital-marketing/dm-api',
          image: 'ghatana/digital-marketing-api',
          tag: 'local',
          sbom: true,
        },
      }));

      expect(result.status).toBe('failed');
      expect(result.failure?.message).toContain('sbom-not-ready');
      expect(commandRunner.invocations).toHaveLength(0);
    });

    it('redacts build arg values in logs and command runner options', async () => {
      const messages: string[] = [];
      const result = await adapter.execute(makeContext({
        surfaceConfig: {
          dockerfile: 'products/digital-marketing/dm-api/Dockerfile',
          context: 'products/digital-marketing/dm-api',
          image: 'ghatana/digital-marketing-api',
          tag: 'local',
          buildArgs: { API_TOKEN: 'secret-token' },
        },
        logger: {
          info: (message) => messages.push(message),
          warn: noop,
          error: noop,
          debug: noop,
        },
      }));

      expect(result.status).toBe('succeeded');
      expect(messages.join('\n')).not.toContain('secret-token');
      expect(commandRunner.invocations[0].options.redact?.('--build-arg API_TOKEN=secret-token')).toBe(
        '--build-arg API_TOKEN=<redacted>',
      );
    });
  });
});
