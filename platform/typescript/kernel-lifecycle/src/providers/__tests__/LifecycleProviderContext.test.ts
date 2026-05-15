import { describe, expect, it } from 'vitest';
import type { GateProvider, LifecycleEventProvider } from '@ghatana/kernel-product-contracts';
import {
  requireLifecycleContextProvider,
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
});
