import { describe, expect, it } from 'vitest';
import { assertTokensValid, tokenRegistrySchema, validateTokens } from '../validation';
import { tokens, type TokenRegistry } from '../registry';

function cloneTokens(): TokenRegistry {
  return JSON.parse(JSON.stringify(tokens)) as TokenRegistry;
}

describe('design token validation', () => {
  it('accepts the canonical token registry', () => {
    const result = validateTokens(tokens);

    expect(result.success).toBe(true);
    expect(result.errors).toBeUndefined();
    expect(() => assertTokensValid(tokens)).not.toThrow();
    expect(tokenRegistrySchema.parse(tokens)).toBeTruthy();
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
