import { describe, expect, it } from 'vitest';
import { getRouteByPath } from '@/lib/routing/RouteCapabilityRegistry';

describe('Route terminology boundary', () => {
  it('marks build pipeline route as orchestration runtime surface', () => {
    const route = getRouteByPath('/build/pipelines');
    expect(route).toBeDefined();
    expect(route?.label).toBe('Pipelines');
    expect(route?.capabilities).toContain('orchestration-runtime');
    expect(route?.description?.toLowerCase()).toContain('agentic orchestration');
  });

  it('marks workflow catalog route as orchestration template surface', () => {
    const route = getRouteByPath('/catalog/workflows');
    expect(route).toBeDefined();
    expect(route?.label).toBe('Workflows');
    expect(route?.capabilities).toContain('orchestration-templates');
    expect(route?.description?.toLowerCase()).toContain('agentic orchestration workflow catalog');
  });
});
