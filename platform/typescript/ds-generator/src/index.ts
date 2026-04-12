/**
 * @fileoverview @ghatana/ds-generator barrel export.
 *
 * @doc.type package
 * @doc.purpose Design system preset materialization and brand customization.
 * @doc.layer platform
 */

export type {
  PresetColorPalette,
  PresetTypography,
  DesignSystemPreset,
  MaterializedTokens,
} from './presets/index.js';

export {
  PresetColorPaletteSchema,
  PresetTypographySchema,
  DesignSystemPresetSchema,
  PRESET_GHATANA_DEFAULT,
  PRESET_ENTERPRISE_NEUTRAL,
  PRESET_CREATIVE_BOLD,
  ALL_PRESETS,
  findPreset,
  materializePreset,
  renderPresetToCss,
} from './presets/index.js';

export type { BrandConfig, BrandedTokens } from './brand/index.js';

export {
  BrandConfigSchema,
  applyBrand,
  renderBrandToCss,
  parseBrandConfig,
} from './brand/index.js';
