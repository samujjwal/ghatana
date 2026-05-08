import { describe, expect, it } from 'vitest';
import {
  applyPropAdapters,
  validateComponentCompatibility,
  type RendererContract,
} from '../ComponentCompatibilityValidator';

const baseContract: RendererContract = {
  requiredProps: [],
  optionalProps: [],
  propTypes: {},
  supportedEvents: [],
  version: '1.0.0',
};

describe('ComponentCompatibilityValidator', () => {
  it('creates executable prop adapters for convertible mismatches', () => {
    const result = validateComponentCompatibility(
      baseContract,
      { count: '42' },
      {
        ...baseContract,
        propTypes: {
          count: {
            type: 'number',
            required: false,
          },
        },
      },
    );

    expect(result.requiredAdapters).toHaveLength(1);
    expect(applyPropAdapters({ count: '42' }, result.requiredAdapters)).toEqual({ count: 42 });
  });

  it('preserves fallback details for non-adaptable missing required props', () => {
    const result = validateComponentCompatibility(
      baseContract,
      {},
      {
        ...baseContract,
        requiredProps: ['title'],
        propTypes: {
          title: {
            type: 'string',
            required: true,
          },
        },
      },
    );

    expect(result.compatible).toBe(false);
    expect(result.fallback).toMatchObject({
      fallbackComponent: 'FallbackComponent',
      reason: 'Missing required props',
      severity: 'error',
    });
  });
});
