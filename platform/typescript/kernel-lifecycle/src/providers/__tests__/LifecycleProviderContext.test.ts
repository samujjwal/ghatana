import { describe, expect, it } from 'vitest';
import type {
  GateProvider,
  LifecycleApprovalProvider,
  LifecycleArtifactProvider,
  LifecycleEventProvider,
  LifecycleHealthProvider,
  LifecycleMemoryProvider,
  LifecycleProvenanceProvider,
  LifecycleRuntimeTruthProvider,
} from '@ghatana/kernel-product-contracts';
import {
  requireBootstrapLifecycleContext,
  requireLifecycleContextProvider,
  requirePlatformLifecycleContext,
  validateLifecycleProviderContext,
  type LifecycleProviderContext,
} from '../LifecycleProviderContext.js';

const events: LifecycleEventProvider = {
  providerId: 'events',
  version: '1.0.0',
  capabilities: ['events'],
  appendEvent: async () => ({ success: true, ref: 'events.json' }),
  listEvents: async () => [],
};

const gate: GateProvider = {
  providerId: 'gates',
  version: '1.0.0',
  capabilities: ['gates'],
  evaluateGate: async () => ({
    gateId: 'security',
    passed: true,
    reason: 'passed',
    evidence: [],
    evaluatedAt: '2026-05-14T00:00:00.000Z',
    duration: 0,
  }),
  getGateConfig: async () => null,
  listGates: async () => ['security'],
};

const artifacts: LifecycleArtifactProvider = {
  providerId: 'artifacts',
  version: '1.0.0',
  capabilities: ['artifacts'],
  recordArtifactManifest: async () => ({ success: true, ref: 'artifacts.json' }),
  listArtifactManifests: async () => [],
};

const health: LifecycleHealthProvider = {
  providerId: 'health',
  version: '1.0.0',
  capabilities: ['health'],
  recordHealthSnapshot: async () => ({ success: true, ref: 'health.json' }),
  getLatestHealthSnapshot: async () => null,
};

const approvals: LifecycleApprovalProvider = {
  providerId: 'approvals',
  version: '1.0.0',
  capabilities: ['approvals'],
  requestLifecycleApproval: async () => ({ success: true, ref: 'approval.json' }),
  decideLifecycleApproval: async () => ({ success: true, ref: 'approval.json' }),
};

const provenance: LifecycleProvenanceProvider = {
  providerId: 'provenance',
  version: '1.0.0',
  capabilities: ['provenance'],
  recordProvenance: async () => ({ success: true, ref: 'provenance.json' }),
  listProvenance: async () => [],
};

const memory: LifecycleMemoryProvider = {
  providerId: 'memory',
  version: '1.0.0',
  capabilities: ['memory'],
  recordMemory: async () => ({ success: true, ref: 'memory.json' }),
  listMemory: async () => [],
};

const runtimeTruth: LifecycleRuntimeTruthProvider = {
  providerId: 'runtime-truth',
  version: '1.0.0',
  capabilities: ['runtime-truth'],
  recordRuntimeTruth: async () => ({ success: true, ref: 'runtime-truth.json' }),
  getRuntimeTruth: async () => null,
};

function bootstrapContext(): LifecycleProviderContext {
  return {
    mode: 'bootstrap',
    events,
    artifacts,
    health,
    approvals,
    provenance,
    runtimeTruth,
  };
}

describe('LifecycleProviderContext', () => {
  it('returns concrete lifecycle providers by name', () => {
    const context: LifecycleProviderContext = {
      mode: 'bootstrap',
      events,
      gates: {
        security: gate,
      },
    };

    expect(requireLifecycleContextProvider<LifecycleEventProvider>(context, 'events')).toBe(events);
    expect(requireLifecycleContextProvider<Record<string, GateProvider>>(context, 'gates')).toEqual({
      security: gate,
    });
  });

  it('fails closed when provider context is missing', () => {
    expect(() => requireLifecycleContextProvider(undefined, 'events')).toThrow(
      'Kernel provider context is required for lifecycle provider: events',
    );
  });

  it('fails closed when a named provider is missing in platform mode', () => {
    const context: LifecycleProviderContext = {
      mode: 'platform',
    };

    expect(() => requireLifecycleContextProvider(context, 'runtimeTruth')).toThrow(
      'Kernel platform mode requires lifecycle provider: runtimeTruth',
    );
  });

  it('validates bootstrap provider requirements', () => {
    expect(validateLifecycleProviderContext(bootstrapContext())).toEqual({
      valid: true,
      missingProviders: [],
      mode: 'bootstrap',
      reasonCodes: [],
    });
  });

  it('fails platform validation when memory and runtime truth are missing', () => {
    const context: LifecycleProviderContext = {
      ...bootstrapContext(),
      mode: 'platform',
      memory: undefined,
      runtimeTruth: undefined,
    };

    expect(validateLifecycleProviderContext(context)).toEqual({
      valid: false,
      missingProviders: ['memory', 'runtimeTruth'],
      mode: 'platform',
      reasonCodes: ['missing-provider'],
    });
  });

  it('requires bootstrap context with correlation ID in missing-provider errors', () => {
    const context: LifecycleProviderContext = {
      ...bootstrapContext(),
      health: undefined,
    };

    expect(() => requireBootstrapLifecycleContext(context, 'corr-1')).toThrow(
      'Kernel bootstrap mode requires lifecycle providers: health (correlationId=corr-1)',
    );
  });

  it('requires platform context with all platform providers', () => {
    const context: LifecycleProviderContext = {
      ...bootstrapContext(),
      mode: 'platform',
      memory,
    };

    expect(requirePlatformLifecycleContext(context, 'corr-2')).toBe(context);
  });

  it('rejects the wrong provider mode for platform requirements', () => {
    expect(() => requirePlatformLifecycleContext(bootstrapContext(), 'corr-3')).toThrow(
      'Expected platform lifecycle provider context but received bootstrap (correlationId=corr-3)',
    );
  });
});
