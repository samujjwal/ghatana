import { describe, expect, it, vi } from 'vitest';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import * as os from 'node:os';
import * as path from 'node:path';
import type {
  KernelLifecycleProviderContext,
  KernelLifecycleEvent,
  LifecycleArtifactProvider,
  LifecycleEventProvider,
  LifecycleHealthProvider,
} from '@ghatana/kernel-product-contracts';
import type { ProductLifecycleResult } from '../../domain/ProductLifecyclePhase.js';
import { LifecycleManifestWriter } from '../LifecycleManifestWriter.js';

function makeResult(overrides: Partial<ProductLifecycleResult> = {}): ProductLifecycleResult {
  return {
    schemaVersion: '1.0.0',
    runId: 'run-001',
    correlationId: 'corr-001',
    providerMode: 'bootstrap',
    productId: 'digital-marketing',
    phase: 'deploy',
    status: 'succeeded',
    startedAt: '2026-05-14T00:00:00.000Z',
    completedAt: '2026-05-14T00:01:00.000Z',
    steps: [
      {
        stepId: 'deploy-web',
        status: 'succeeded',
        durationMs: 1000,
      },
    ],
    gates: [],
    artifacts: [
      {
        id: 'web-image',
        surface: 'web',
        type: 'container-image',
        path: 'ghatana/web:run-001',
        fingerprint: 'sha256:abc123',
        producedBy: 'docker-buildx',
        sizeBytes: 12,
      },
    ],
    outputDirectory: '/tmp/out',
    ...overrides,
  };
}

function artifactProvider(overrides: Partial<LifecycleArtifactProvider> = {}): LifecycleArtifactProvider {
  return {
    providerId: 'artifact-provider',
    version: '1.0.0',
    capabilities: ['artifact-manifests'],
    recordArtifactManifest: vi.fn().mockResolvedValue({ success: true, ref: 'artifact-provider-ref' }),
    listArtifactManifests: vi.fn().mockResolvedValue([]),
    ...overrides,
  };
}

function healthProvider(overrides: Partial<LifecycleHealthProvider> = {}): LifecycleHealthProvider {
  return {
    providerId: 'health-provider',
    version: '1.0.0',
    capabilities: ['health'],
    recordHealthSnapshot: vi.fn().mockResolvedValue({ success: true, ref: 'health-provider-ref' }),
    getLatestHealthSnapshot: vi.fn().mockResolvedValue(null),
    ...overrides,
  };
}

function lifecycleEvent(): KernelLifecycleEvent {
  return {
    metadata: {
      eventId: 'event-1',
      schemaVersion: '1.0.0',
      eventType: 'lifecycle.phase.started',
      productUnitId: 'digital-marketing',
      runId: 'run-001',
      phase: 'deploy',
      timestamp: '2026-05-14T00:00:00.000Z',
      source: 'kernel-lifecycle',
      correlationId: 'corr-001',
    },
    payload: {
      phase: 'deploy',
      status: 'running',
      startedAt: '2026-05-14T00:00:00.000Z',
    },
  };
}

function eventProvider(overrides: Partial<LifecycleEventProvider> = {}): LifecycleEventProvider {
  return {
    providerId: 'events-provider',
    version: '1.0.0',
    capabilities: ['lifecycle-events'],
    appendEvent: vi.fn().mockResolvedValue({ success: true, ref: 'events-provider-ref' }),
    listEvents: vi.fn().mockResolvedValue([lifecycleEvent()]),
    ...overrides,
  };
}

describe('LifecycleManifestWriter', () => {
  it('writes required lifecycle, event, artifact, deployment, health, and verify manifests with provider refs', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const artifacts = artifactProvider();
    const health = healthProvider();
    const events = eventProvider();
    const context: KernelLifecycleProviderContext = {
      mode: 'bootstrap',
      artifacts,
      health,
      events,
    };
    const writer = new LifecycleManifestWriter({ outputDirectory, providerContext: context });

    const write = await writer.writeRequiredManifests({
      result: makeResult({ phase: 'verify' }),
      requiredManifests: [
        'lifecycle-result',
        'artifact-manifest',
        'deployment-manifest',
        'verify-health-report',
        'lifecycle-health-snapshot',
        'lifecycle-events',
      ],
      environment: 'staging',
    });

    expect(write.failure).toBeUndefined();
    expect(write.result.status).toBe('succeeded');
    expect(write.manifestRefs).toMatchObject({
      lifecycleResult: expect.stringContaining('lifecycle-result.json'),
      artifactManifest: expect.stringContaining('artifact-manifest.json'),
      deploymentManifest: expect.stringContaining('deployment-manifest.json'),
      verifyHealthReport: expect.stringContaining('verify-health-report.json'),
      lifecycleEvents: expect.stringContaining('lifecycle-events.json'),
    });
    expect(vi.mocked(artifacts.recordArtifactManifest)).toHaveBeenCalledWith(
      expect.objectContaining({
        productUnitId: 'digital-marketing',
        runId: 'run-001',
        artifactCount: 1,
      }),
      {
        required: true,
        correlationId: 'corr-001',
      },
    );
    expect(vi.mocked(health.recordHealthSnapshot)).toHaveBeenCalledWith(
      expect.objectContaining({
        productUnitId: 'digital-marketing',
        runId: 'run-001',
        status: 'healthy',
      }),
      {
        required: true,
        correlationId: 'corr-001',
      },
    );

    const artifactManifest = JSON.parse(
      await readFile(write.manifestRefs.artifactManifest ?? '', 'utf-8'),
    ) as Record<string, unknown>;
    expect(artifactManifest).toMatchObject({
      schemaVersion: '1.0.0',
      productId: 'digital-marketing',
      phase: 'verify',
      surface: 'web',
    });

    const deploymentManifest = JSON.parse(
      await readFile(write.manifestRefs.deploymentManifest ?? '', 'utf-8'),
    ) as Record<string, unknown>;
    expect(deploymentManifest).toMatchObject({
      schemaVersion: '1.0.0',
      productId: 'digital-marketing',
      environment: 'staging',
      target: 'compose-local',
    });
    const eventsManifest = JSON.parse(
      await readFile(write.manifestRefs.lifecycleEvents ?? '', 'utf-8'),
    ) as { eventCount: number; events: KernelLifecycleEvent[] };
    expect(eventsManifest.eventCount).toBe(1);
    expect(eventsManifest.events[0].metadata.eventId).toBe('event-1');
    expect(write.result.eventsRef).toBe(write.manifestRefs.lifecycleEvents);
    const eventFiles = await readdir(path.dirname(write.manifestRefs.lifecycleEvents ?? ''));
    expect(eventFiles.some((fileName) => fileName.includes('.tmp'))).toBe(false);

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('fails closed when a required provider-backed manifest write fails', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const artifacts = artifactProvider({
      recordArtifactManifest: vi.fn().mockResolvedValue({ success: false, error: 'provider unavailable' }),
    });
    const writer = new LifecycleManifestWriter({
      outputDirectory,
      providerContext: {
        mode: 'bootstrap',
        artifacts,
      },
    });

    const write = await writer.writeRequiredManifests({
      result: makeResult(),
      requiredManifests: ['artifact-manifest'],
    });

    expect(write.result.status).toBe('failed');
    expect(write.result.failure).toMatchObject({
      reasonCode: 'manifest-write-failed',
      stepId: 'manifest-writer',
    });
    expect(write.result.failure?.message).toContain('provider unavailable');

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('normalizes unknown artifact types and deployment environments into safe manifest values', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const writer = new LifecycleManifestWriter({ outputDirectory });

    const write = await writer.writeRequiredManifests({
      result: makeResult({
        artifacts: [
          {
            id: 'custom',
            surface: 'worker',
            type: 'custom-artifact',
            path: 'custom.json',
            fingerprint: 'plain-hash',
            producedBy: 'custom-adapter',
          },
          {
            id: 'node-service',
            surface: 'worker',
            type: 'node-service',
            path: 'service.tgz',
            fingerprint: 'sha256:node',
            producedBy: 'npm',
          },
          {
            id: 'sdk',
            surface: 'worker',
            type: 'sdk-package',
            path: 'sdk.tgz',
            fingerprint: 'sha256:sdk',
            producedBy: 'npm',
          },
          {
            id: 'test-report',
            surface: 'worker',
            type: 'test-report',
            path: 'test-report.json',
            fingerprint: 'sha256:test',
            producedBy: 'vitest',
          },
          {
            id: 'domain-pack',
            surface: 'worker',
            type: 'domain-pack',
            path: 'domain-pack.json',
            fingerprint: 'sha256:domain',
            producedBy: 'packager',
          },
        ],
      }),
      requiredManifests: ['artifact-manifest', 'deployment-manifest'],
      environment: 'qa',
    });

    const artifactManifest = JSON.parse(
      await readFile(write.manifestRefs.artifactManifest ?? '', 'utf-8'),
    ) as { artifacts: Array<{ metadata: { type: string; packaging: string } }> };
    expect(artifactManifest.artifacts[0].metadata).toMatchObject({
      type: 'documentation',
      packaging: 'json',
    });
    expect(artifactManifest.artifacts[1].metadata).toMatchObject({
      type: 'node-service',
      packaging: 'npm',
    });
    expect(artifactManifest.artifacts[2].metadata).toMatchObject({
      type: 'sdk-package',
      packaging: 'npm',
    });
    expect(artifactManifest.artifacts[3].metadata).toMatchObject({
      type: 'test-report',
      packaging: 'json',
    });
    expect(artifactManifest.artifacts[4].metadata).toMatchObject({
      type: 'domain-pack',
      packaging: 'json',
    });

    const deploymentManifest = JSON.parse(
      await readFile(write.manifestRefs.deploymentManifest ?? '', 'utf-8'),
    ) as { environment: string };
    expect(deploymentManifest.environment).toBe('local');

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('uses run id as provider correlation fallback and surfaces default artifact provider failures', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const artifacts = artifactProvider({
      recordArtifactManifest: vi.fn().mockResolvedValue({ success: false }),
    });
    const writer = new LifecycleManifestWriter({
      outputDirectory,
      providerContext: {
        mode: 'bootstrap',
        artifacts,
      },
    });

    const result = makeResult({
      correlationId: undefined,
      artifacts: [
        {
          id: 'library',
          surface: 'backend-api',
          type: 'jvm-library',
          path: 'lib.jar',
          fingerprint: 'sha256:lib',
          producedBy: 'gradle',
        },
      ],
    });
    const write = await writer.writeRequiredManifests({
      result,
      requiredManifests: ['artifact-manifest'],
    });

    expect(vi.mocked(artifacts.recordArtifactManifest).mock.calls[0]?.[1]).toEqual({
      required: true,
      correlationId: 'run-001',
    });
    expect(write.result.status).toBe('failed');
    expect(write.result.failure?.message).toContain('artifact provider write failed');

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('fails closed when provider writes reject with non-error values', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const health = healthProvider({
      recordHealthSnapshot: vi.fn().mockRejectedValue('provider-offline'),
    });
    const writer = new LifecycleManifestWriter({
      outputDirectory,
      providerContext: {
        mode: 'bootstrap',
        health,
      },
    });

    const write = await writer.writeRequiredManifests({
      result: makeResult({ artifacts: [] }),
      requiredManifests: ['lifecycle-health-snapshot'],
    });

    expect(write.result.status).toBe('failed');
    expect(write.result.failure?.message).toContain('provider-offline');

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('fails closed with the default health provider failure message', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const health = healthProvider({
      recordHealthSnapshot: vi.fn().mockResolvedValue({ success: false }),
    });
    const writer = new LifecycleManifestWriter({
      outputDirectory,
      providerContext: {
        mode: 'bootstrap',
        health,
      },
    });

    const write = await writer.writeRequiredManifests({
      result: makeResult({ artifacts: [] }),
      requiredManifests: ['lifecycle-health-snapshot'],
    });

    expect(write.result.status).toBe('failed');
    expect(write.result.failure?.message).toContain('health provider write failed');

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('writes deployment manifests without artifact refs and uses health provider correlation fallback', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const health = healthProvider();
    const writer = new LifecycleManifestWriter({
      outputDirectory,
      providerContext: {
        mode: 'bootstrap',
        health,
      },
    });

    const write = await writer.writeRequiredManifests({
      result: makeResult({
        correlationId: undefined,
        artifacts: [],
      }),
      requiredManifests: ['deployment-manifest', 'lifecycle-health-snapshot'],
    });

    const deploymentManifest = JSON.parse(
      await readFile(write.manifestRefs.deploymentManifest ?? '', 'utf-8'),
    ) as Record<string, unknown>;
    expect(deploymentManifest).not.toHaveProperty('artifactManifestRef');
    expect(vi.mocked(health.recordHealthSnapshot).mock.calls[0]?.[1]).toEqual({
      required: true,
      correlationId: 'run-001',
    });

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('writes gate and planned manifest references, handles multiple surfaces, and maps failed deployments', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const writer = new LifecycleManifestWriter({ outputDirectory });

    const write = await writer.writeRequiredManifests({
      result: makeResult({
        status: 'failed',
        gates: [
          {
            gateId: 'smoke',
            gateName: 'Smoke',
            status: 'failed',
            checkedAt: '2026-05-14T00:00:30.000Z',
          },
        ],
        artifacts: [
          {
            id: 'api-jar',
            surface: 'backend-api',
            type: 'jvm-service',
            path: 'api.jar',
            fingerprint: 'sha512:def456',
            producedBy: 'gradle',
            sizeBytes: 10,
          },
          {
            id: 'web-dist',
            surface: 'web',
            type: 'static-web-bundle',
            path: 'dist',
            fingerprint: 'sha256:abc123',
            producedBy: 'vite',
            sizeBytes: 20,
          },
        ],
      }),
      requiredManifests: [
        'artifact-manifest',
        'deployment-manifest',
        'gate-result-manifest',
        'lifecycle-plan',
        'lifecycle-events',
      ],
      environment: 'prod',
    });

    expect(write.result.status).toBe('failed');
    expect(write.manifestRefs.lifecyclePlan).toContain('lifecycle-plan.json');
    expect(write.manifestRefs.lifecycleEvents).toContain('lifecycle-events.json');
    expect(write.manifestRefs.gateResultManifest).toContain('gate-result-manifest.json');

    const artifactManifest = JSON.parse(
      await readFile(write.manifestRefs.artifactManifest ?? '', 'utf-8'),
    ) as {
      surface: string;
      artifacts: Array<{
        fingerprint: { algorithm: string; hash: string };
        metadata: { packaging: string };
      }>;
    };
    expect(artifactManifest.surface).toBe('multiple');
    expect(artifactManifest.artifacts[0]).toMatchObject({
      fingerprint: { algorithm: 'sha512', hash: 'def456' },
      metadata: { packaging: 'jar' },
    });
    expect(artifactManifest.artifacts[1].metadata.packaging).toBe('static-files');

    const deploymentManifest = JSON.parse(
      await readFile(write.manifestRefs.deploymentManifest ?? '', 'utf-8'),
    ) as { surfaces: Array<{ status: string }> };
    expect(deploymentManifest.surfaces).toEqual([
      expect.objectContaining({ status: 'failed' }),
      expect.objectContaining({ status: 'failed' }),
    ]);

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('fails closed when lifecycle event providers return invalid events', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const events = eventProvider({
      listEvents: vi.fn().mockResolvedValue([
        {
          ...lifecycleEvent(),
          metadata: {
            ...lifecycleEvent().metadata,
            eventId: '',
          },
        },
      ]),
    });
    const writer = new LifecycleManifestWriter({
      outputDirectory,
      providerContext: {
        mode: 'bootstrap',
        events,
      },
    });

    const write = await writer.writeRequiredManifests({
      result: makeResult({ artifacts: [] }),
      requiredManifests: ['lifecycle-events'],
    });

    expect(write.result.status).toBe('failed');
    expect(write.result.failure?.message).toContain('lifecycle-events');

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('queries lifecycle events without correlation when the result has none', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const events = eventProvider({ listEvents: vi.fn().mockResolvedValue([]) });
    const writer = new LifecycleManifestWriter({
      outputDirectory,
      providerContext: {
        mode: 'bootstrap',
        events,
      },
    });

    const write = await writer.writeRequiredManifests({
      result: makeResult({ correlationId: undefined, artifacts: [] }),
      requiredManifests: ['lifecycle-events'],
    });

    expect(write.result.status).toBe('succeeded');
    expect(vi.mocked(events.listEvents).mock.calls[0]?.[0]).toEqual({
      productUnitId: 'digital-marketing',
      runId: 'run-001',
    });

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('writes product-level manifests for runs without artifacts and maps skipped deployments to pending', async () => {
    const outputDirectory = await mkdtemp(path.join(os.tmpdir(), 'manifest-writer-'));
    const writer = new LifecycleManifestWriter({ outputDirectory });

    const write = await writer.writeRequiredManifests({
      result: makeResult({
        status: 'skipped',
        artifacts: [],
      }),
      requiredManifests: ['artifact-manifest', 'deployment-manifest', 'lifecycle-health-snapshot'],
    });

    const artifactManifest = JSON.parse(
      await readFile(write.manifestRefs.artifactManifest ?? '', 'utf-8'),
    ) as { surface: string; artifacts: unknown[] };
    expect(artifactManifest).toMatchObject({
      surface: 'product',
      artifacts: [],
    });

    const deploymentManifest = JSON.parse(
      await readFile(write.manifestRefs.deploymentManifest ?? '', 'utf-8'),
    ) as { surfaces: Array<{ surface: string; status: string }> };
    expect(deploymentManifest.surfaces).toEqual([
      expect.objectContaining({
        surface: 'product',
        status: 'pending',
      }),
    ]);

    const healthSnapshot = JSON.parse(
      await readFile(write.manifestRefs.lifecycleResult ?? path.join(outputDirectory, 'missing'), 'utf-8').catch(
        () => '{}',
      ),
    ) as Record<string, unknown>;
    expect(healthSnapshot).toEqual({});

    await rm(outputDirectory, { recursive: true, force: true });
  });

  it('fails closed when lifecycle-result itself cannot be written', async () => {
    const outputFile = path.join(os.tmpdir(), `manifest-writer-file-${Date.now()}.json`);
    await readFile(outputFile, 'utf-8').catch(async () => {
      await import('node:fs/promises').then(({ writeFile }) => writeFile(outputFile, '{}', 'utf-8'));
    });
    const writer = new LifecycleManifestWriter({ outputDirectory: outputFile });

    const write = await writer.writeRequiredManifests({
      result: makeResult({ artifacts: [] }),
      requiredManifests: ['lifecycle-result'],
    });

    expect(write.result.status).toBe('failed');
    expect(write.failure?.message).toContain('lifecycle-result');

    await rm(outputFile, { force: true });
  });
});
