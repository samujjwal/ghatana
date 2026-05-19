/**
 * @fileoverview Idempotency tests for DS generator
 *
 * Tests that running the generator multiple times produces identical output.
 * Ensures the generator is deterministic and doesn't introduce drift.
 *
 * @doc.type test
 * @doc.purpose DS generator idempotency tests
 * @doc.layer ds-generator
 * @doc.pattern IdempotencyTesting
 */

import { describe, it, expect } from 'vitest';
import { renderPresetToCss } from '../presets/index.js';
import { PRESET_GHATANA_DEFAULT } from '../presets/index.js';

describe('DS Generator Idempotency Tests', () => {
  it('produces identical CSS across runs', () => {
    const css1 = renderPresetToCss(PRESET_GHATANA_DEFAULT);
    const css2 = renderPresetToCss(PRESET_GHATANA_DEFAULT);
    const css3 = renderPresetToCss(PRESET_GHATANA_DEFAULT);

    expect(css1).toBe(css2);
    expect(css2).toBe(css3);
  });

  it('maintains consistent output order in CSS', () => {
    const css1 = renderPresetToCss(PRESET_GHATANA_DEFAULT);
    const css2 = renderPresetToCss(PRESET_GHATANA_DEFAULT);

    // Check that properties appear in consistent order
    const lines1 = css1.split('\n').filter((line: string) => line.includes('--'));
    const lines2 = css2.split('\n').filter((line: string) => line.includes('--'));

    expect(lines1).toEqual(lines2);
  });
});
