/**
 * Token Migration Utilities
 *
 * Convert existing tokens from various formats to W3C Design Token format.
 * Supports migration from MUI themes, Tailwind configs, and custom token objects.
 *
 * Core Principles:
 * - Composability: Utilities can be composed for complex migrations
 * - Extensibility: Easy to add custom migration strategies
 * - Type Safety: Full TypeScript support
 * - Validation: Automatic validation of migrated tokens
 */

import { validateTokenCollection } from './validators';

import type {
  TokenCollection,
  ColorToken,
  DimensionToken,
  FontFamilyToken,
  FontWeightToken,
  FontSizeToken,
  LineHeightToken,
  BorderRadiusToken,
  ShadowToken,
} from './token-schema';

/**
 * Existing color palette structure from libs/ui/src/tokens/colors.ts
 */
export interface ExistingColorPalette {
  primary: Record<number, string>;
  secondary: Record<number, string>;
  neutral: Record<number, string>;
  success: { light: string; main: string; dark: string };
  warning: { light: string; main: string; dark: string };
  error: { light: string; main: string; dark: string };
  info: { light: string; main: string; dark: string };
}

/**
 * Existing typography structure from libs/ui/src/tokens/typography.ts
 */
export interface ExistingTypography {
  fontFamily?: string;
  fontSize?: string | number;
  fontWeight?: string | number;
  lineHeight?: string | number;
  letterSpacing?: string | number;
}

/**
 * Migration result with validation
 */
export interface MigrationResult<T> {
  success: boolean;
  data: T | null;
  errors: string[];
  warnings: string[];
}

/**
 * Convert hex color to W3C color token
 */
export function migrateColorToken(
  value: string,
  description?: string
): ColorToken {
  return {
    $type: 'color',
    $value: value,
    ...(description && { $description: description }),
  };
}

/**
 * Migrate color palette to W3C format
 */
export function migrateColorPalette(
  palette: ExistingColorPalette
): MigrationResult<TokenCollection> {
  const errors: string[] = [];
  const warnings: string[] = [];
  const tokens: TokenCollection = {};

  try {
    // Migrate primary colors
    tokens.primary = {};
    for (const [shade, value] of Object.entries(palette.primary)) {
      tokens.primary[shade] = migrateColorToken(
        value,
        `Primary color shade ${shade}`
      );
    }

    // Migrate secondary colors
    tokens.secondary = {};
    for (const [shade, value] of Object.entries(palette.secondary)) {
      tokens.secondary[shade] = migrateColorToken(
        value,
        `Secondary color shade ${shade}`
      );
    }

    // Migrate neutral colors
    tokens.neutral = {};
    for (const [shade, value] of Object.entries(palette.neutral)) {
      tokens.neutral[shade] = migrateColorToken(
        value,
        `Neutral color shade ${shade}`
      );
    }

    // Migrate semantic colors
    const semanticColors = ['success', 'warning', 'error', 'info'] as const;
    for (const semantic of semanticColors) {
      tokens[semantic] = {};
      const colorSet = palette[semantic];
      for (const [variant, value] of Object.entries(colorSet)) {
        tokens[semantic][variant] = migrateColorToken(
          value,
          `${semantic} ${variant} color`
        );
      }
    }

    // Validate the result
    const validation = validateTokenCollection(tokens);
    if (!validation.success) {
      errors.push('Validation failed after migration');
      return { success: false, data: null, errors, warnings };
    }

    return { success: true, data: tokens, errors, warnings };
  } catch (error) {
    errors.push(`Migration failed: ${error instanceof Error ? error.message : String(error)}`);
    return { success: false, data: null, errors, warnings };
  }
}

/**
 * Convert dimension value to W3C format
 */
export function migrateDimensionToken(
  value: string | number,
  description?: string
): DimensionToken {
  const normalizedValue = typeof value === 'number' ? `${value}px` : value;

  return {
    $type: 'dimension',
    $value: normalizedValue,
    ...(description && { $description: description }),
  };
}

/**
 * Migrate spacing scale to W3C format
 */
export function migrateSpacingScale(
  spacing: Record<string, number>
): MigrationResult<TokenCollection> {
  const errors: string[] = [];
  const warnings: string[] = [];
  const tokens: TokenCollection = {};

  try {
    for (const [key, value] of Object.entries(spacing)) {
      tokens[key] = migrateDimensionToken(
        `${value}px`,
        `Spacing value ${key}`
      );
    }

    const validation = validateTokenCollection(tokens);
    if (!validation.success) {
      errors.push('Validation failed after migration');
      return { success: false, data: null, errors, warnings };
    }

    return { success: true, data: tokens, errors, warnings };
  } catch (error) {
    errors.push(`Migration failed: ${error instanceof Error ? error.message : String(error)}`);
    return { success: false, data: null, errors, warnings };
  }
}

/**
 * Migrate typography variant to W3C format
 */
export function migrateTypographyToken(
  typography: ExistingTypography,
  name: string
): MigrationResult<TokenCollection> {
  const errors: string[] = [];
  const warnings: string[] = [];
  const tokens: TokenCollection = {};

  try {
    if (typography.fontFamily) {
      tokens[`${name}-font-family`] = {
        $type: 'fontFamily',
        $value: typography.fontFamily,
        $description: `Font family for ${name}`,
      } as FontFamilyToken;
    }

    if (typography.fontSize) {
      const fontSize =
        typeof typography.fontSize === 'number'
          ? `${typography.fontSize}px`
          : typography.fontSize;

      tokens[`${name}-font-size`] = {
        $type: 'fontSize',
        $value: fontSize,
        $description: `Font size for ${name}`,
      } as FontSizeToken;
    }

    if (typography.fontWeight) {
      tokens[`${name}-font-weight`] = {
        $type: 'fontWeight',
        $value: String(typography.fontWeight),
        $description: `Font weight for ${name}`,
      } as FontWeightToken;
    }

    if (typography.lineHeight) {
      const lineHeight =
        typeof typography.lineHeight === 'number'
          ? String(typography.lineHeight)
          : typography.lineHeight;

      tokens[`${name}-line-height`] = {
        $type: 'lineHeight',
        $value: lineHeight,
        $description: `Line height for ${name}`,
      } as LineHeightToken;
    }

    const validation = validateTokenCollection(tokens);
    if (!validation.success) {
      errors.push('Validation failed after migration');
      return { success: false, data: null, errors, warnings };
    }

    return { success: true, data: tokens, errors, warnings };
  } catch (error) {
    errors.push(`Migration failed: ${error instanceof Error ? error.message : String(error)}`);
    return { success: false, data: null, errors, warnings };
  }
}

/**
 * Migrate border radius values to W3C format
 */
export function migrateBorderRadius(
  radii: Record<string, number | string>
): MigrationResult<TokenCollection> {
  const errors: string[] = [];
  const warnings: string[] = [];
  const tokens: TokenCollection = {};

  try {
    for (const [key, value] of Object.entries(radii)) {
      const normalizedValue =
        typeof value === 'number' ? `${value}px` : value;

      tokens[key] = {
        $type: 'borderRadius',
        $value: normalizedValue,
        $description: `Border radius ${key}`,
      } as BorderRadiusToken;
    }

    const validation = validateTokenCollection(tokens);
    if (!validation.success) {
      errors.push('Validation failed after migration');
      return { success: false, data: null, errors, warnings };
    }

    return { success: true, data: tokens, errors, warnings };
  } catch (error) {
    errors.push(`Migration failed: ${error instanceof Error ? error.message : String(error)}`);
    return { success: false, data: null, errors, warnings };
  }
}

/**
 * Migrate shadow values to W3C format
 */
export function migrateShadows(
  shadows: string[]
): MigrationResult<TokenCollection> {
  const errors: string[] = [];
  const warnings: string[] = [];
  const tokens: TokenCollection = {};

  try {
    shadows.forEach((shadow, index) => {
      if (shadow && shadow !== 'none') {
        tokens[`elevation-${index}`] = {
          $type: 'shadow',
          $value: shadow,
          $description: `Shadow elevation level ${index}`,
        } as ShadowToken;
      }
    });

    const validation = validateTokenCollection(tokens);
    if (!validation.success) {
      errors.push('Validation failed after migration');
      return { success: false, data: null, errors, warnings };
    }

    return { success: true, data: tokens, errors, warnings };
  } catch (error) {
    errors.push(`Migration failed: ${error instanceof Error ? error.message : String(error)}`);
    return { success: false, data: null, errors, warnings };
  }
}

/**
 * Generate token name from path
 * Converts camelCase or PascalCase to kebab-case
 */
export function generateTokenName(path: string[]): string {
  return path
    .map((segment) =>
      segment
        .replace(/([a-z])([A-Z])/g, '$1-$2')
        .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
        .toLowerCase()
    )
    .join('-');
}

/**
 * Convert token references to W3C format
 * Converts "{colors.primary.500}" style references
 */
export function normalizeTokenReference(ref: string): string {
  // Already in correct format
  if (ref.startsWith('{') && ref.endsWith('}')) {
    return ref;
  }

  // Add curly braces if missing
  return `{${ref}}`;
}

/**
 * Batch migrate multiple token collections
 */
export function batchMigrateTokens(
  collections: Array<{
    name: string;
    data: unknown;
    migrator: (data: unknown) => MigrationResult<TokenCollection>;
  }>
): {
  success: boolean;
  tokens: Record<string, TokenCollection>;
  errors: Record<string, string[]>;
  warnings: Record<string, string[]>;
} {
  const tokens: Record<string, TokenCollection> = {};
  const errors: Record<string, string[]> = {};
  const warnings: Record<string, string[]> = {};
  let overallSuccess = true;

  for (const { name, data, migrator } of collections) {
    const result = migrator(data);

    if (result.success && result.data) {
      tokens[name] = result.data;
    } else {
      overallSuccess = false;
    }

    if (result.errors.length > 0) {
      errors[name] = result.errors;
    }

    if (result.warnings.length > 0) {
      warnings[name] = result.warnings;
    }
  }

  return { success: overallSuccess, tokens, errors, warnings };
}

/**
 * Create a complete token file from migrated tokens
 */
export function createTokenFile(
  collections: Record<string, TokenCollection>,
  metadata?: {
    version?: string;
    author?: string;
    description?: string;
  }
): TokenCollection {
  const tokenFile: TokenCollection = {};

  // Add metadata as extensions if provided
  if (metadata) {
    (tokenFile as Record<string, unknown>)['$extensions'] = {
      'com.yappc.metadata': metadata,
    };
  }

  // Merge all collections
  for (const [name, collection] of Object.entries(collections)) {
    tokenFile[name] = collection;
  }

  return tokenFile;
}

/**
 * Export tokens to JSON string
 */
export function exportTokensToJSON(
  tokens: TokenCollection,
  pretty = true
): string {
  return JSON.stringify(tokens, null, pretty ? 2 : 0);
}

/**
 * Migration summary for reporting
 */
export interface MigrationSummary {
  totalTokens: number;
  byType: Record<string, number>;
  errors: number;
  warnings: number;
}

/**
 * Generate migration summary
 */
export function generateMigrationSummary(
  tokens: TokenCollection,
  errors: Record<string, string[]>,
  warnings: Record<string, string[]>
): MigrationSummary {
  const summary: MigrationSummary = {
    totalTokens: 0,
    byType: {},
    errors: Object.values(errors).flat().length,
    warnings: Object.values(warnings).flat().length,
  };

  /**
   *
   */
  function countTokens(collection: TokenCollection): void {
    for (const value of Object.values(collection)) {
      if (typeof value === 'object' && value !== null && '$type' in value && '$value' in value) {
        summary.totalTokens++;
        const type = value.$type as string;
        summary.byType[type as keyof typeof summary.byType] = ((summary.byType[type as keyof typeof summary.byType] as number) || 0) + 1;
      } else if (typeof value === 'object' && value !== null) {
        countTokens(value as TokenCollection);
      }
    }
  }

  countTokens(tokens);
  return summary;
}
