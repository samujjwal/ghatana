/**
 * Design Token Schema Module
 *
 * Comprehensive token schema definitions, validation utilities, and migration tools
 * for building composable, type-safe design systems.
 *
 * @packageDocumentation
 */

// Export token schemas and types
export * from './token-schema';

// Export validation utilities
export * from './validators';

// Export migration utilities
export * from './migrate-tokens';

// Re-export commonly used types for convenience
export type {
  Token,
  TokenCollection,
  TokenFile,
  ColorToken,
  DimensionToken,
  FontFamilyToken,
  FontWeightToken,
  FontSizeToken,
  LineHeightToken,
  LetterSpacingToken,
  BorderRadiusToken,
  BorderWidthToken,
  ShadowToken,
  DurationToken,
  CubicBezierToken,
  ZIndexToken,
  OpacityToken,
  TypographyToken,
  BaseToken,
  TokenTypeValue,
} from './token-schema';

export type {
  ValidationResult,
  TokenCollectionValidation,
} from './validators';

export type {
  MigrationResult,
  MigrationSummary,
  ExistingColorPalette,
  ExistingTypography,
} from './migrate-tokens';

// Export commonly used functions
export {
  // Validation
  validateToken,
  validateTokenCollection,
  validateTokenFile,
  validateTokenByType,
  isTokenReference,
  resolveTokenReference,
  getTokenByPath,
  resolveTokenReferences,
  hasCircularReferences,
  validateTokenName,
  validateTokenNames,
  formatValidationErrors,
  validateTokens,
  validateCollectionIntegrity,
} from './validators';

export {
  // Migration
  migrateColorToken,
  migrateColorPalette,
  migrateDimensionToken,
  migrateSpacingScale,
  migrateTypographyToken,
  migrateBorderRadius,
  migrateShadows,
  generateTokenName,
  normalizeTokenReference,
  batchMigrateTokens,
  createTokenFile,
  exportTokensToJSON,
  generateMigrationSummary,
} from './migrate-tokens';

// Export token type constants
export { TokenType } from './token-schema';
