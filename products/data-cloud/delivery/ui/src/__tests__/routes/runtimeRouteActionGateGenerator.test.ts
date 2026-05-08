import { describe, expect, it } from 'vitest';
import { generateRouteActionGates } from '@/lib/routing/RuntimeRouteActionGateGenerator';
import type { CapabilitySignal } from '@/api/capabilities.service';

describe('generateRouteActionGates', () => {
  it('generates route/action gates from runtime capability signals', () => {
    const capabilities: CapabilitySignal[] = [
      {
        key: 'alert-triage',
        label: 'Alert Triage',
        status: 'active',
        summary: 'ACTIVE',
        rawValue: { status: 'ACTIVE' },
      },
      {
        key: 'event-stream',
        label: 'Event Stream',
        status: 'degraded',
        summary: 'DEGRADED',
        rawValue: { status: 'DEGRADED' },
      },
      {
        key: 'agentCatalog',
        label: 'Agent Catalog',
        status: 'unavailable',
        summary: 'UNAVAILABLE',
        rawValue: { status: 'UNAVAILABLE' },
      },
    ];

    const generated = generateRouteActionGates(capabilities);
    const alertsRoute = generated.find((route) => route.path === '/alerts');
    const eventsRoute = generated.find((route) => route.path === '/events');

    expect(generated.length).toBeGreaterThan(0);
    expect(alertsRoute?.status).toBe('active');
    expect(eventsRoute?.actions.some((action) => action.status === 'degraded')).toBe(true);
  });

  it('returns unknown status when route capabilities are not present', () => {
    const generated = generateRouteActionGates([]);
    const connectorsRoute = generated.find((route) => route.path === '/connectors');

    expect(connectorsRoute).toBeDefined();
    expect(connectorsRoute?.status).toBe('unknown');
  });
});
