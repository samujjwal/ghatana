import { describe, it, expect } from 'vitest';

// Small pure helper tests for normalization behavior used by the canvas
// For now we implement a dummy normalize helper inline to test expectations

function normalizeNodePayload(payload: unknown) {
  // emulate the normalizer used in the app: ensure id, position and data exist
  return {
    id: String(payload.id ?? `node-${Math.random().toString(36).slice(2, 8)}`),
    position: payload.position ?? { x: 0, y: 0 },
    data: payload.data ?? {},
    type: payload.type ?? 'default',
  };
}

describe('normalizeNodePayload', () => {
  it('fills missing fields with defaults', () => {
    const input = { label: 'test' };
    const normalized = normalizeNodePayload(input);
    expect(normalized.id).toBeDefined();
    expect(normalized.position).toEqual({ x: 0, y: 0 });
    expect(normalized.data).toEqual({});
    expect(normalized.type).toBe('default');
  });

  it('preserves provided values', () => {
    const input = {
      id: 'n1',
      position: { x: 10, y: 20 },
      data: { label: 'ok' },
      type: 'custom',
    };
    const normalized = normalizeNodePayload(input);
    expect(normalized.id).toBe('n1');
    expect(normalized.position).toEqual({ x: 10, y: 20 });
    expect(normalized.data).toEqual({ label: 'ok' });
    expect(normalized.type).toBe('custom');
  });
});
