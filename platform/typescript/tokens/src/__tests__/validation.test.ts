import { describe, expect, it } from 'vitest';
import {
  assertTokensValid,
  tokenRegistrySchema,
  validateTokens,
  validateTokenRegistryDTCG,
  assertTokenRegistryDTCG,
} from '../validation';
import { tokens, type TokenRegistry } from '../registry';

function cloneTokens(): TokenRegistry {
  return JSON.parse(JSON.stringify(tokens)) as TokenRegistry;
}

describe('design token validation', () => {
  describe('lightweight primitive validation', () => {
    it('accepts the canonical token registry', () => {
      const result = validateTokens(tokens);

      expect(result.success).toBe(true);
      expect(result.errors).toBeUndefined();
      expect(() => assertTokensValid(tokens)).not.toThrow();
    });

    it('rejects registries with missing required values', () => {
      const invalidTokens = cloneTokens();
      delete (invalidTokens as any).colors.palette.primary['500'];

      const result = validateTokens(invalidTokens);

      expect(result.success).toBe(false);
      expect(result.errors).toBeDefined();
      expect(result.errors?.some((message) => message.includes('colors.palette.primary.500'))).toBe(true);
      expect(() => assertTokensValid(invalidTokens)).toThrowError();
    });

    it('rejects registries with incorrect types', () => {
      const invalidTokens = cloneTokens();
      (invalidTokens as any).spacing.semantic.md = 'invalid';

      const result = validateTokens(invalidTokens);

      expect(result.success).toBe(false);
      expect(result.errors?.some((message) => message.includes('spacing.semantic.md'))).toBe(true);
    });
  });

  describe('DTCG-backed Zod schema validation', () => {
    it('tokenRegistrySchema parses canonical registry without throwing', () => {
      expect(() => tokenRegistrySchema.parse(tokens)).not.toThrow();
    });

    it('validateTokenRegistryDTCG returns success for canonical registry', () => {
      const result = validateTokenRegistryDTCG(tokens as unknown as Record<string, unknown>);
      expect(result.success).toBe(true);
      expect(result.errors).toBeUndefined();
    });

    it('assertTokenRegistryDTCG does not throw for canonical registry', () => {
      expect(() =>
        assertTokenRegistryDTCG(tokens as unknown as Record<string, unknown>),
      ).not.toThrow();
    });

    it('validateTokenRegistryDTCG returns error when colors.palette.primary.500 is missing', () => {
      const invalid = cloneTokens() as unknown as Record<string, unknown>;
      delete ((invalid as any).colors.palette.primary as Record<string, unknown>)['500'];

      const result = validateTokenRegistryDTCG(invalid);

      expect(result.success).toBe(false);
      expect(result.errors).toBeDefined();
      expect(result.errors!.length).toBeGreaterThan(0);
    });

    it('assertTokenRegistryDTCG throws when registry is invalid', () => {
      const invalid = cloneTokens() as unknown as Record<string, unknown>;
      delete ((invalid as any).colors as Record<string, unknown>)['palette'];

      expect(() => assertTokenRegistryDTCG(invalid)).toThrow(/DTCG token registry validation failed/);
    });
  });
});
