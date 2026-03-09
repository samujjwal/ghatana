/**
 * Token Validators
 *
 * Runtime validation utilities for design tokens ensuring integrity,
 * consistency, and compliance with W3C Design Token specifications.
 *
 * Core Principles:
 * - Composability: Validators can be composed for complex validation
 * - Extensibility: Easy to add custom validators
 * - Clarity: Clear, actionable error messages
 * - Performance: Efficient validation with caching
 */

import { z, type ZodError } from 'zod';
import {
  tokenSchema,
  tokenCollectionSchema,
  tokenFileSchema,
  colorTokenSchema,
  dimensionTokenSchema,
  fontFamilyTokenSchema,
  fontWeightTokenSchema,
  fontSizeTokenSchema,
  lineHeightTokenSchema,
  letterSpacingTokenSchema,
  borderRadiusTokenSchema,
  borderWidthTokenSchema,
  shadowTokenSchema,
  durationTokenSchema,
  cubicBezierTokenSchema,
  zIndexTokenSchema,
  opacityTokenSchema,
  typographyTokenSchema,
  type Token,
  type TokenCollection,
  type TokenFile,
  type TokenTypeValue,
} from './token-schema';

type ZodErrorType = z.ZodError<unknown>;

/**
 * Validation result type
 */
export type ValidationResult<T> =
  | { success: true; data: T; errors: null }
  | { success: false; data: null; errors: z.ZodError };

/**
 * Validate a single token
 */
export function validateToken(token: unknown): ValidationResult<Token> {
  const result = tokenSchema.safeParse(token);

  if (result.success) {
    return { success: true, data: result.data, errors: null };
  }

  return { success: false, data: null, errors: result.error };
}

/**
 * Validate a token collection
 */
export function validateTokenCollection(
  collection: unknown
): ValidationResult<TokenCollection> {
  const result = tokenCollectionSchema.safeParse(collection);

  if (result.success) {
    return { success: true, data: result.data, errors: null };
  }

  return { success: false, data: null, errors: result.error };
}

/**
 * Validate a complete token file
 */
export function validateTokenFile(file: unknown): ValidationResult<TokenFile> {
  const result = tokenFileSchema.safeParse(file);

  if (result.success) {
    return { success: true, data: result.data, errors: null };
  }

  return { success: false, data: null, errors: result.error };
}

/**
 * Validate token by type
 */
export function validateTokenByType(
  token: unknown,
  type: TokenTypeValue
): ValidationResult<Token> {
  const schemaMap = {
    color: colorTokenSchema,
    dimension: dimensionTokenSchema,
    fontFamily: fontFamilyTokenSchema,
    fontWeight: fontWeightTokenSchema,
    fontSize: fontSizeTokenSchema,
    lineHeight: lineHeightTokenSchema,
    letterSpacing: letterSpacingTokenSchema,
    borderRadius: borderRadiusTokenSchema,
    borderWidth: borderWidthTokenSchema,
    shadow: shadowTokenSchema,
    duration: durationTokenSchema,
    cubicBezier: cubicBezierTokenSchema,
    zIndex: zIndexTokenSchema,
    opacity: opacityTokenSchema,
    typography: typographyTokenSchema,
  };

  const schema = schemaMap[type];
  if (!schema) {
    return {
      success: false,
      data: null,
      errors: new z.ZodError([
        {
          code: 'custom',
          path: ['$type'],
          message: `Unknown token type: ${type}`,
        },
      ]),
    };
  }

  const result = schema.safeParse(token);

  if (result.success) {
    return { success: true, data: result.data, errors: null };
  }

  return { success: false, data: null, errors: result.error };
}

/**
 * Check if a token value is a reference (e.g., "{colors.primary}")
 */
export function isTokenReference(value: unknown): value is string {
  return (
    typeof value === 'string' && value.startsWith('{') && value.endsWith('}')
  );
}

/**
 * Resolve token reference path
 * @example "{colors.primary.500}" => ["colors", "primary", "500"]
 */
export function resolveTokenReference(reference: string): string[] {
  if (!isTokenReference(reference)) {
    throw new Error(`Invalid token reference: ${reference}`);
  }

  // Remove curly braces and split by dot
  const path = reference.slice(1, -1).split('.');

  if (path.length === 0 || path.some((segment) => segment.length === 0)) {
    throw new Error(`Invalid token reference format: ${reference}`);
  }

  return path;
}

/**
 * Get token value by path from a token collection
 */
export function getTokenByPath(
  collection: TokenCollection,
  path: string[]
): Token | TokenCollection | undefined {
  let current: Token | TokenCollection = collection;

  for (const segment of path) {
    if (typeof current === 'object' && segment in current) {
      current = (current as Record<string, Token | TokenCollection>)[segment];
    } else {
      return undefined;
    }
  }

  return current;
}

/**
 * Resolve all token references in a collection
 * Returns a new collection with all references resolved
 */
export function resolveTokenReferences(
  collection: TokenCollection,
  maxDepth = 10
): TokenCollection {
  const resolved: TokenCollection = {};

  /**
   *
   */
  function resolveValue(
    value: unknown,
    depth = 0
  ): string | number | object | unknown[] {
    if (depth > maxDepth) {
      throw new Error('Maximum reference resolution depth exceeded');
    }

    if (isTokenReference(value)) {
      const path = resolveTokenReference(value);
      const referencedToken = getTokenByPath(collection, path);

      if (!referencedToken) {
        throw new Error(`Token reference not found: ${value}`);
      }

      // If it's a token, resolve its $value
      if (
        typeof referencedToken === 'object' &&
        '$value' in referencedToken
      ) {
        return resolveValue(referencedToken.$value, depth + 1);
      }

      return referencedToken as unknown as string | number | object | unknown[];
    }

    if (Array.isArray(value)) {
      return value.map((item) => resolveValue(item, depth));
    }

    if (typeof value === 'object' && value !== null) {
      const resolvedObj: Record<string, unknown> = {};
      for (const [key, val] of Object.entries(value)) {
        resolvedObj[key] = resolveValue(val, depth);
      }
      return resolvedObj as string | number | object | unknown[];
    }

    return value as TokenTypeValue;
  }

  /**
   *
   */
  function resolveToken(token: Token | TokenCollection): Token | TokenCollection {
    if (typeof token === 'object' && '$type' in token && '$value' in token) {
      // It's a token
      return {
        ...token,
        $value: resolveValue(token.$value),
      } as Token;
    }

    // It's a collection
    const resolvedCollection: TokenCollection = {};
    for (const [key, value] of Object.entries(token)) {
      resolvedCollection[key] = resolveToken(value as Token | TokenCollection);
    }
    return resolvedCollection;
  }

  for (const [key, value] of Object.entries(collection)) {
    resolved[key] = resolveToken(value as Token | TokenCollection);
  }

  return resolved;
}

/**
 * Check for circular references in token collection
 */
export function hasCircularReferences(collection: TokenCollection): boolean {
  const visited = new Set<string>();

  /**
   *
   */
  function checkToken(
    token: Token | TokenCollection,
    path: string[]
  ): boolean {
    const pathKey = path.join('.');

    if (visited.has(pathKey)) {
      return true; // Circular reference detected
    }

    visited.add(pathKey);

    if (typeof token === 'object' && '$value' in token) {
      const value = token.$value;

      if (isTokenReference(value)) {
        const refPath = resolveTokenReference(value);
        const referencedToken = getTokenByPath(collection, refPath);

        if (!referencedToken) {
          return false; // Invalid reference, but not circular
        }

        if (checkToken(referencedToken, refPath)) {
          return true;
        }
      }
    } else if (typeof token === 'object') {
      for (const [key, value] of Object.entries(token)) {
        if (checkToken(value as Token | TokenCollection, [...path, key])) {
          return true;
        }
      }
    }

    visited.delete(pathKey);
    return false;
  }

  for (const [key, value] of Object.entries(collection)) {
    if (checkToken(value as Token | TokenCollection, [key])) {
      return true;
    }
  }

  return false;
}

/**
 * Validate token naming convention
 * Token names should be kebab-case
 */
export function validateTokenName(name: string): boolean {
  const kebabCaseRegex = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/;
  return kebabCaseRegex.test(name);
}

/**
 * Validate all token names in a collection
 */
export function validateTokenNames(
  collection: TokenCollection,
  path: string[] = []
): { valid: boolean; invalidNames: string[] } {
  const invalidNames: string[] = [];

  for (const [key, value] of Object.entries(collection)) {
    const currentPath = [...path, key];

    if (!validateTokenName(key)) {
      invalidNames.push(currentPath.join('.'));
    }

    if (
      typeof value === 'object' &&
      value !== null &&
      !('$type' in value && '$value' in value)
    ) {
      const result = validateTokenNames(
        value as TokenCollection,
        currentPath
      );
      invalidNames.push(...result.invalidNames);
    }
  }

  return {
    valid: invalidNames.length === 0,
    invalidNames,
  };
}

/**
 * Get human-readable error messages from Zod errors
 */
interface ZodIssue {
  path: (string | number)[];
  message: string;
  code: string;
}

export function formatValidationErrors(error: unknown): string[] {
  if (error && typeof error === 'object' && 'errors' in error) {
    const zodError = error as { errors: ZodIssue[] };
    return zodError.errors.map((err: ZodIssue) => {
      const path = err.path.join('.');
      return path ? `${path}: ${err.message}` : err.message;
    });
  }
  return [String(error)];
}

/**
 * Batch validate multiple tokens
 */
export function validateTokens(
  tokens: unknown[]
): { valid: Token[]; invalid: Array<{ token: unknown; errors: ZodErrorType }> } {
  const valid: Token[] = [];
  const invalid: Array<{ token: unknown; errors: ZodErrorType }> = [];

  for (const token of tokens) {
    const result = validateToken(token);
    if (result.success) {
      valid.push(result.data);
    } else {
      invalid.push({ token, errors: result.errors });
    }
  }

  return { valid, invalid };
}

/**
 * Check if a color token has sufficient contrast
 * Useful for accessibility validation
 */
export function validateColorContrast(
  _foreground: string,
  _background: string,
  level: 'AA' | 'AAA' = 'AA'
): { valid: boolean; ratio: number; required: number } {
  // This is a placeholder - actual implementation would use a color contrast library
  // TODO: Implement using a library like 'color' or 'wcag-contrast'

  const requiredRatio = level === 'AAA' ? 7 : 4.5;

  return {
    valid: false,
    ratio: 0,
    required: requiredRatio,
  };
}

/**
 * Validate token collection for common issues
 */
export interface TokenCollectionValidation {
  isValid: boolean;
  errors: string[];
  warnings: string[];
}

/**
 *
 */
export function validateCollectionIntegrity(
  collection: TokenCollection
): TokenCollectionValidation {
  const errors: string[] = [];
  const warnings: string[] = [];

  // Check for circular references
  if (hasCircularReferences(collection)) {
    errors.push('Circular references detected in token collection');
  }

  // Validate token names
  const nameValidation = validateTokenNames(collection);
  if (!nameValidation.valid) {
    warnings.push(
      `Invalid token names (should be kebab-case): ${nameValidation.invalidNames.join(', ')}`
    );
  }

  // Validate the collection structure
  const collectionResult = validateTokenCollection(collection);
  if (!collectionResult.success) {
    errors.push(...formatValidationErrors(collectionResult.errors));
  }

  return {
    isValid: errors.length === 0,
    errors,
    warnings,
  };
}
