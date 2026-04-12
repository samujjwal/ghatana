/**
 * @ghatana/ds-generator brand customization test suite
 * Tests for brand customization features
 */

import { describe, it, expect } from 'vitest';
import {
  materializePreset,
  renderPresetToCss,
  PRESET_GHATANA_DEFAULT,
} from '../index';

describe('@ghatana/ds-generator - Brand Customization', () => {
  describe('Preset Materialization', () => {
    it('should materialize default preset', () => {
      const materialized = materializePreset(PRESET_GHATANA_DEFAULT);
      expect(materialized).toBeDefined();
      expect(materialized.colors).toBeDefined();
    });

    it('should generate CSS from preset', () => {
      const css = renderPresetToCss(PRESET_GHATANA_DEFAULT);
      expect(css).toBeDefined();
      expect(typeof css).toBe('string');
      expect(css.length).toBeGreaterThan(0);
    });
  });
});
