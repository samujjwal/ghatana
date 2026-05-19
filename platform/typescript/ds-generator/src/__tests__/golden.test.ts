/**
 * @fileoverview Golden tests for DS generator
 *
 * Snapshot tests that compare generated output against expected golden files.
 * Ensures that code generation produces consistent, expected results.
 *
 * @doc.type test
 * @doc.purpose DS generator golden tests
 * @doc.layer ds-generator
 * @doc.pattern SnapshotTesting
 */

import { describe, it, expect } from 'vitest';
import { renderPresetToCss } from '../presets/index.js';
import { PRESET_GHATANA_DEFAULT } from '../presets/index.js';

describe('DS Generator Golden Tests', () => {
  it('generates preset CSS matching golden file', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);

    expect(css).toMatchSnapshot('preset-css.golden');
  });

  it('renders CSS from preset matching golden file', () => {
    const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);

    expect(css).toMatchSnapshot('rendered-css.golden');
  });
});
