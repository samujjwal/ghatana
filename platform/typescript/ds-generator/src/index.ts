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
} from "./presets/index.js";

export {
  PresetColorPaletteSchema,
  PresetTypographySchema,
  DesignSystemPresetSchema,
  MaterializedTokensSchema,
  PRESET_GHATANA_DEFAULT,
  PRESET_ENTERPRISE_NEUTRAL,
  PRESET_CREATIVE_BOLD,
  ALL_PRESETS,
  findPreset,
  materializePreset,
  renderPresetToCss,
} from "./presets/index.js";

export type { BrandConfig, BrandedTokens } from "./brand/index.js";

export {
  BrandConfigSchema,
  applyBrand,
  renderBrandToCss,
  parseBrandConfig,
} from "./brand/index.js";

export type {
  GeneratorExtensionPoint,
  GeneratorOutputArtifact,
  DesignSystemGeneratorManifest,
  CreateGeneratorManifestOptions,
} from "./extensions/index.js";

export {
  GeneratorExtensionPointSchema,
  GeneratorOutputArtifactSchema,
  DesignSystemGeneratorManifestSchema,
  createGeneratorManifest,
} from "./extensions/index.js";

// ============================================================================
// Model
// ============================================================================

export type {
  SemanticTokenAlias,
  ComponentState,
  ComponentVariantDefinition,
  DesignSystemDocument,
} from "./model/design-system-document.js";

export {
  DS_DOCUMENT_SCHEMA_VERSION,
  SemanticTokenAliasSchema,
  ComponentStateSchema,
  ComponentVariantDefinitionSchema,
  DesignSystemDocumentSchema,
  createDesignSystemDocument,
} from "./model/design-system-document.js";

// ============================================================================
// Token graph
// ============================================================================

export type {
  TokenGraphError,
  ResolvedAlias,
  TokenGraphResult,
} from "./tokens/token-graph.js";

export {
  buildTokenGraph,
  flattenTokenRecord,
  graphToRecord,
} from "./tokens/token-graph.js";

// ============================================================================
// Validation
// ============================================================================

export type {
  ContrastRequirements,
  ContrastValidationResult,
  ContrastPair,
  ContrastAuditEntry,
  ContrastAuditResult,
} from "./validation/contrast.js";

export {
  hexToRgb,
  calculateRelativeLuminance,
  calculateContrastRatio,
  validateContrast,
  validateContrastBatch,
  passesAA,
  passesAAA,
  passesAALarge,
  passesComponent,
  suggestContrastImprovements,
  auditContrastPairs,
  assertDocumentContrastCompliance,
  deriveColorPairs,
} from "./validation/contrast.js";

// ============================================================================
// Targets
// ============================================================================

export type { CssTargetOptions } from "./targets/css.js";
export { emitCss } from "./targets/css.js";

export type { JsonTargetOptions, JsonTokenOutput, JsonTokenOutputData } from "./targets/json.js";
export { emitJson } from "./targets/json.js";

export type { TailwindTargetOptions } from "./targets/tailwind.js";
export { emitTailwind } from "./targets/tailwind.js";

export type { ReactThemeTargetOptions } from "./targets/react-theme.js";
export { emitReactTheme } from "./targets/react-theme.js";

// ============================================================================
// File emission pipeline
// ============================================================================

export type {
  EmittedFile,
  EmittedFileManifest,
  EmitFilesOptions,
} from "./targets/emit-files.js";
export { emitFiles } from "./targets/emit-files.js";
