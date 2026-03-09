import { describe, expect, it } from 'vitest';
import {
  getPlaybook,
  getTopActions,
  hasPlaybook,
} from '../../../analytics/guidance/ActionPlaybooks';

describe('ActionPlaybooks', () => {
  it('provides playbook definitions for critical LCP regressions', () => {
    expect(hasPlaybook('lcp', 'critical')).toBe(true);
    const playbook = getPlaybook('lcp', 'critical');
    expect(playbook).toBeDefined();
    expect(playbook?.actions.length).toBeGreaterThan(0);
  });

  it('returns the highest priority actions first', () => {
    const actions = getTopActions('lcp', 'critical', 2);
    expect(actions).toHaveLength(2);
    expect(actions[0].priority).toBe('high');
    expect(['high', 'medium']).toContain(actions[1].priority);
  });

  it('gracefully handles missing playbooks', () => {
    expect(hasPlaybook('nonexistent-metric')).toBe(false);
    expect(getPlaybook('nonexistent-metric', 'critical')).toBeUndefined();
    expect(getTopActions('nonexistent-metric', 'critical')).toEqual([]);
  });
});
