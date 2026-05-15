import { describe, expect, it, vi } from 'vitest';
import type {
  KernelLifecycleEvent,
  LifecycleEventProvider,
  TelemetryEvent,
  TelemetryProvider,
} from '@ghatana/kernel-product-contracts';
import { KernelLifecycleEventEmitter } from '../KernelLifecycleEventEmitter.js';

function createTelemetryProvider(overrides: Partial<TelemetryProvider> = {}): TelemetryProvider {
  return {
    providerId: 'telemetry-test',
    version: '1.0.0',
    capabilities: ['events'],
    emitEvent: vi.fn().mockResolvedValue(undefined),
    recordMetric: vi.fn().mockResolvedValue(undefined),
    getEvents: vi.fn().mockResolvedValue([]),
    getMetrics: vi.fn().mockResolvedValue([]),
    ...overrides,
  };
}

function emittedEvents(provider: TelemetryProvider): TelemetryEvent[] {
  return vi.mocked(provider.emitEvent).mock.calls.map(([event]) => event);
}

function createLifecycleEventProvider(): LifecycleEventProvider {
  return {
    providerId: 'events-test',
    version: '1.0.0',
    capabilities: ['events'],
    appendEvent: vi.fn().mockResolvedValue({ success: true, ref: 'events/run-1.jsonl' }),
    listEvents: vi.fn().mockResolvedValue([]),
  };
}

function appendedEvents(provider: LifecycleEventProvider): KernelLifecycleEvent[] {
  return vi.mocked(provider.appendEvent).mock.calls.map(([event]) => event);
}

describe('KernelLifecycleEventEmitter', () => {
  it('emits canonical phase events to telemetry with explicit correlation ids', () => {
    const provider = createTelemetryProvider();
    const emitter = new KernelLifecycleEventEmitter({
      telemetryProvider: provider,
      enableConsoleLogging: false,
    });

    emitter.emitLifecyclePhaseStart('product-1', 'run-1', 'build', 'corr-1');
    emitter.emitLifecyclePhaseComplete('product-1', 'run-1', 'build', 'succeeded', 12, 'corr-1');

    expect(emittedEvents(provider)).toMatchObject([
      {
        eventType: 'lifecycle.phase.started',
        productUnitId: 'product-1',
        payload: {
          phase: 'build',
          status: 'running',
        },
      },
      {
        eventType: 'lifecycle.phase.completed',
        productUnitId: 'product-1',
        payload: {
          phase: 'build',
          status: 'succeeded',
          durationMs: 12,
        },
      },
    ]);
  });

  it('maps legacy helper methods to canonical lifecycle event payloads', () => {
    const provider = createTelemetryProvider();
    const emitter = new KernelLifecycleEventEmitter({
      telemetryProvider: provider,
      enableConsoleLogging: false,
    });

    emitter.emitGateEvaluated('product-1', 'run-1', 'validate', 'typecheck', true, 'passed', ['log:typecheck'], 5);
    emitter.emitArtifactProduced(
      'product-1',
      'run-1',
      'package',
      'artifact-1',
      'dist/app.tar',
      '1.2.3',
      'static-web-bundle',
      1024,
      'sha256:abc',
      'studio'
    );
    emitter.emitDeploymentComplete(
      'product-1',
      'run-1',
      'deploy',
      'deployment-1',
      'staging',
      'succeeded',
      ['artifact-1'],
      ['https://example.test'],
      42
    );
    emitter.emitHealthCheckResult(
      'product-1',
      'run-1',
      'verify',
      'smoke',
      'Smoke test',
      'healthy',
      'ok',
      7,
      'deployment-1',
      'staging'
    );
    emitter.emitAgentGovernanceEvent(
      'product-1',
      'run-1',
      'dev',
      'agent-1',
      'apply-plan',
      'requires-approval',
      'release policy',
      'corr-agent',
      'supervised',
      'plan',
      ['policy:release']
    );

    expect(emittedEvents(provider)).toMatchObject([
      {
        eventType: 'lifecycle.gate.evaluated',
        payload: {
          gateId: 'typecheck',
          status: 'passed',
          required: true,
          evidenceRefs: ['log:typecheck'],
          durationMs: 5,
        },
      },
      {
        eventType: 'lifecycle.artifact.recorded',
        payload: {
          artifactId: 'artifact-1',
          artifactType: 'static-web-bundle',
          required: true,
          path: 'dist/app.tar',
          fingerprint: 'sha256:abc',
          evidenceRefs: ['surface:studio', 'version:1.2.3', 'size:1024'],
        },
      },
      {
        eventType: 'lifecycle.deployment.completed',
        payload: {
          deploymentId: 'deployment-1',
          environment: 'staging',
          status: 'succeeded',
          artifactIds: ['artifact-1'],
          endpoints: ['https://example.test'],
          durationMs: 42,
        },
      },
      {
        eventType: 'lifecycle.health.checked',
        payload: {
          checkId: 'smoke',
          checkName: 'Smoke test',
          status: 'healthy',
          message: 'ok',
          durationMs: 7,
          deploymentId: 'deployment-1',
          environment: 'staging',
        },
      },
      {
        eventType: 'lifecycle.agent.governance.evaluated',
        payload: {
          agentId: 'agent-1',
          actionType: 'apply-plan',
          decision: 'requires-approval',
          reason: 'release policy',
          masteryState: 'supervised',
          executionMode: 'plan',
          evidenceRefs: ['policy:release'],
        },
      },
    ]);
  });

  it('emits canonical step events to telemetry and durable lifecycle providers', () => {
    const telemetryProvider = createTelemetryProvider();
    const lifecycleEventProvider = createLifecycleEventProvider();
    const emitter = new KernelLifecycleEventEmitter({
      telemetryProvider,
      lifecycleEventProvider,
      enableConsoleLogging: false,
    });

    emitter.emitLifecycleStepStart(
      'product-1',
      'run-1',
      'build',
      'build-web',
      'surface',
      'web',
      'pnpm-vite-react',
      'corr-1'
    );
    emitter.emitLifecycleStepComplete(
      'product-1',
      'run-1',
      'build',
      'build-web',
      'surface',
      'web',
      'pnpm-vite-react',
      'succeeded',
      99,
      ['artifact:web-dist'],
      'corr-1',
      0
    );

    expect(emittedEvents(telemetryProvider)).toMatchObject([
      {
        eventType: 'lifecycle.step.started',
        productUnitId: 'product-1',
        payload: {
          stepId: 'build-web',
          stepKind: 'surface',
          surface: 'web',
          adapter: 'pnpm-vite-react',
          status: 'running',
        },
      },
      {
        eventType: 'lifecycle.step.completed',
        productUnitId: 'product-1',
        payload: {
          stepId: 'build-web',
          status: 'succeeded',
          durationMs: 99,
          exitCode: 0,
          evidenceRefs: ['artifact:web-dist'],
        },
      },
    ]);

    expect(appendedEvents(lifecycleEventProvider)).toMatchObject([
      {
        metadata: {
          eventType: 'lifecycle.step.started',
          correlationId: 'corr-1',
        },
      },
      {
        metadata: {
          eventType: 'lifecycle.step.completed',
          correlationId: 'corr-1',
        },
      },
    ]);
    expect(vi.mocked(lifecycleEventProvider.appendEvent).mock.calls[0]?.[1]).toEqual({
      required: true,
      correlationId: 'corr-1',
    });
  });

  it('logs lifecycle event provider write failures without hiding telemetry events', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const telemetryProvider = createTelemetryProvider();
    const lifecycleEventProvider: LifecycleEventProvider = {
      ...createLifecycleEventProvider(),
      appendEvent: vi.fn().mockResolvedValue({ success: false, error: 'disk full' }),
    };
    const emitter = new KernelLifecycleEventEmitter({
      telemetryProvider,
      lifecycleEventProvider,
      enableConsoleLogging: false,
    });

    emitter.emitLifecycleStepComplete(
      'product-1',
      'run-1',
      'build',
      'build-web',
      'surface',
      'web',
      'pnpm-vite-react',
      'failed',
      10,
      [],
      'corr-1'
    );
    await Promise.resolve();

    expect(emittedEvents(telemetryProvider)[0]?.eventType).toBe('lifecycle.step.completed');
    expect(consoleError).toHaveBeenCalledWith('Failed to append lifecycle event:', 'disk full');

    consoleError.mockRestore();
  });

  it('logs unknown lifecycle event provider write failures when providers omit an error', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const lifecycleEventProvider: LifecycleEventProvider = {
      ...createLifecycleEventProvider(),
      appendEvent: vi.fn().mockResolvedValue({ success: false }),
    };
    const emitter = new KernelLifecycleEventEmitter({
      lifecycleEventProvider,
      enableConsoleLogging: false,
    });

    emitter.emitLifecycleStepComplete(
      'product-1',
      'run-1',
      'build',
      'build-web',
      'surface',
      'web',
      'pnpm-vite-react',
      'failed',
      10,
      [],
      'corr-1'
    );
    await Promise.resolve();

    expect(consoleError).toHaveBeenCalledWith('Failed to append lifecycle event:', 'unknown provider error');

    consoleError.mockRestore();
  });

  it('logs lifecycle event provider rejection failures', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const lifecycleEventProvider: LifecycleEventProvider = {
      ...createLifecycleEventProvider(),
      appendEvent: vi.fn().mockRejectedValue(new Error('provider offline')),
    };
    const emitter = new KernelLifecycleEventEmitter({
      lifecycleEventProvider,
      enableConsoleLogging: false,
    });

    emitter.emitLifecycleStepStart(
      'product-1',
      'run-1',
      'build',
      'build-web',
      'surface',
      'web',
      'pnpm-vite-react',
      'corr-1'
    );
    await Promise.resolve();
    await Promise.resolve();

    expect(consoleError).toHaveBeenCalledWith('Failed to append lifecycle event:', expect.any(Error));

    consoleError.mockRestore();
  });

  it('logs events when telemetry is not configured', () => {
    const consoleLog = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    const emitter = new KernelLifecycleEventEmitter();

    emitter.emitGateEvaluated('product-1', 'run-1', 'validate', 'lint', false, 'lint failed', [], 1);

    expect(consoleLog).toHaveBeenCalledTimes(1);
    expect(consoleLog.mock.calls[0]?.[0]).toContain('"eventType": "lifecycle.gate.evaluated"');

    consoleLog.mockRestore();
  });

  it('falls back to console logging when telemetry emission fails', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const consoleLog = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    const provider = createTelemetryProvider({
      emitEvent: vi.fn().mockRejectedValue(new Error('telemetry unavailable')),
    });
    const emitter = new KernelLifecycleEventEmitter({ telemetryProvider: provider });

    emitter.emitLifecyclePhaseStart('product-1', 'run-1', 'build');
    await Promise.resolve();

    expect(consoleError).toHaveBeenCalledWith(
      'Failed to emit event via telemetry provider:',
      expect.any(Error)
    );
    expect(consoleLog.mock.calls[0]?.[0]).toContain('"eventType": "lifecycle.phase.started"');

    consoleError.mockRestore();
    consoleLog.mockRestore();
  });

  it('suppresses fallback logging when console logging is disabled', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const consoleLog = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    const provider = createTelemetryProvider({
      emitEvent: vi.fn().mockRejectedValue(new Error('telemetry unavailable')),
    });
    const emitter = new KernelLifecycleEventEmitter({
      telemetryProvider: provider,
      enableConsoleLogging: false,
    });

    emitter.emitLifecyclePhaseStart('product-1', 'run-1', 'build');
    await Promise.resolve();

    expect(consoleError).toHaveBeenCalledTimes(1);
    expect(consoleLog).not.toHaveBeenCalled();

    consoleError.mockRestore();
    consoleLog.mockRestore();
  });

  it('does not log when telemetry and console logging are both disabled', () => {
    const consoleLog = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    const emitter = new KernelLifecycleEventEmitter({ enableConsoleLogging: false });

    emitter.emitLifecyclePhaseStart('product-1', 'run-1', 'build');

    expect(consoleLog).not.toHaveBeenCalled();

    consoleLog.mockRestore();
  });

  it('omits optional health and agent metadata when not supplied', () => {
    const provider = createTelemetryProvider();
    const emitter = new KernelLifecycleEventEmitter({
      telemetryProvider: provider,
      enableConsoleLogging: false,
    });

    emitter.emitHealthCheckResult('product-1', 'run-1', 'verify', 'smoke', 'Smoke test', 'unknown', 'pending', 7);
    emitter.emitAgentGovernanceEvent(
      'product-1',
      'run-1',
      'dev',
      'agent-1',
      'apply-plan',
      'allowed',
      'policy matched'
    );

    expect(emittedEvents(provider)).toMatchObject([
      {
        eventType: 'lifecycle.health.checked',
        payload: {
          checkId: 'smoke',
          status: 'unknown',
        },
      },
      {
        eventType: 'lifecycle.agent.governance.evaluated',
        payload: {
          agentId: 'agent-1',
          decision: 'allowed',
          evidenceRefs: [],
        },
      },
    ]);
    expect(emittedEvents(provider)[0]?.payload).not.toHaveProperty('deploymentId');
    expect(emittedEvents(provider)[0]?.payload).not.toHaveProperty('environment');
    expect(emittedEvents(provider)[1]?.payload).not.toHaveProperty('masteryState');
    expect(emittedEvents(provider)[1]?.payload).not.toHaveProperty('executionMode');
  });
});
