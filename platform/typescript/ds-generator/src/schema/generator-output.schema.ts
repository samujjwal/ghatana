/**
 * @fileoverview Design system generator output schema.
 *
 * Provides Zod schema for design system generator output including CSS variables,
 * Tailwind config, React theme, JSON tokens, and other target artifacts.
 * Ensures output validation and serialization compatibility.
 *
 * @doc.type module
 * @doc.purpose Design system generator output schema validation
 * @doc.layer ds-generator
 */

import { z } from 'zod';

// ============================================================================
// CSS Variables Output Schema
// ============================================================================

export const CSSVariableSchema = z.object({
  name: z.string(),
  value: z.string(),
  category: z.string().optional(),
});

export const CSSVariablesOutputSchema = z.object({
  type: z.literal('css-variables'),
  format: z.enum(['css', 'scss', 'less']),
  variables: z.array(CSSVariableSchema),
  content: z.string(),
  metadata: z.object({
    generatedAt: z.string(),
    generatorVersion: z.string(),
  }),
});

export type CSSVariablesOutput = z.infer<typeof CSSVariablesOutputSchema>;

// ============================================================================
// Tailwind Config Output Schema
// ============================================================================

export const TailwindColorSchema = z.record(z.string(), z.union([z.string(), z.record(z.string(), z.string())]));

export const TailwindConfigOutputSchema = z.object({
  type: z.literal('tailwind-config'),
  theme: z.object({
    colors: TailwindColorSchema.optional(),
    fontFamily: z.record(z.string(), z.array(z.string())).optional(),
    fontSize: z.record(z.string(), z.union([z.string(), z.array(z.string())])).optional(),
    spacing: z.record(z.string(), z.union([z.string(), z.array(z.string())])).optional(),
    borderRadius: z.record(z.string(), z.union([z.string(), z.array(z.string())])).optional(),
    boxShadow: z.record(z.string(), z.union([z.string(), z.array(z.string())])).optional(),
    extend: z.record(z.unknown()).optional(),
  }),
  content: z.string(),
  metadata: z.object({
    generatedAt: z.string(),
    generatorVersion: z.string(),
  }),
});

export type TailwindConfigOutput = z.infer<typeof TailwindConfigOutputSchema>;

// ============================================================================
// React Theme Output Schema
// ============================================================================

export const ReactThemeOutputSchema = z.object({
  type: z.literal('react-theme'),
  themeName: z.string(),
  theme: z.object({
    colors: z.record(z.string(), z.string()).optional(),
    typography: z.record(z.string(), z.object({
      fontFamily: z.string(),
      fontSize: z.string(),
      fontWeight: z.union([z.number(), z.string()]),
      lineHeight: z.union([z.number(), z.string()]),
    })).optional(),
    spacing: z.record(z.string(), z.union([z.string(), z.number()])).optional(),
    borderRadius: z.record(z.string(), z.union([z.string(), z.number()])).optional(),
    shadows: z.record(z.string(), z.string()).optional(),
    transitions: z.record(z.string(), z.string()).optional(),
    zIndex: z.record(z.string(), z.number()).optional(),
  }),
  content: z.string(),
  metadata: z.object({
    generatedAt: z.string(),
    generatorVersion: z.string(),
  }),
});

export type ReactThemeOutput = z.infer<typeof ReactThemeOutputSchema>;

// ============================================================================
// JSON Tokens Output Schema
// ============================================================================

export const JSONTokenSchema = z.object({
  type: z.string(),
  name: z.string(),
  value: z.union([z.string(), z.number(), z.boolean(), z.array(z.unknown()), z.record(z.string(), z.unknown())]),
  category: z.string().optional(),
});

export const JSONTokensOutputSchema = z.object({
  type: z.literal('json-tokens'),
  format: z.enum(['json', 'json5']),
  tokens: z.array(JSONTokenSchema),
  content: z.string(),
  metadata: z.object({
    generatedAt: z.string(),
    generatorVersion: z.string(),
  }),
});

export type JSONTokensOutput = z.infer<typeof JSONTokensOutputSchema>;

// ============================================================================
// Generator Output Union Schema
// ============================================================================

export const GeneratorOutputSchema = z.discriminatedUnion('type', [
  CSSVariablesOutputSchema,
  TailwindConfigOutputSchema,
  ReactThemeOutputSchema,
  JSONTokensOutputSchema,
]);

export type GeneratorOutput = z.infer<typeof GeneratorOutputSchema>;

// ============================================================================
// Complete Generation Result Schema
// ============================================================================

export const GenerationResultSchema = z.object({
  schemaVersion: z.string().default('1.0.0'),
  inputPreset: z.string(),
  inputBrand: z.string().optional(),
  outputs: z.array(GeneratorOutputSchema),
  metadata: z.object({
    generatedAt: z.string(),
    generatorVersion: z.string(),
    checksum: z.string(),
  }),
});

export type GenerationResult = z.infer<typeof GenerationResultSchema>;

// ============================================================================
// Validation Functions
// ============================================================================

export interface ValidationResult<T> {
  valid: boolean;
  errors: string[];
  data?: T;
}

export function validateGeneratorOutput(output: unknown): ValidationResult<GeneratorOutput> {
  const result = GeneratorOutputSchema.safeParse(output);
  
  if (!result.success) {
    return {
      valid: false,
      errors: result.error.errors.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  return {
    valid: true,
    errors: [],
    data: result.data,
  };
}

export function validateGenerationResult(result: unknown): ValidationResult<GenerationResult> {
  const parsed = GenerationResultSchema.safeParse(result);
  
  if (!parsed.success) {
    return {
      valid: false,
      errors: parsed.error.errors.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  // Validate each output
  const outputErrors: string[] = [];
  for (const output of parsed.data.outputs) {
    const outputResult = validateGeneratorOutput(output);
    if (!outputResult.valid) {
      outputErrors.push(...outputResult.errors);
    }
  }

  if (outputErrors.length > 0) {
    return {
      valid: false,
      errors: outputErrors,
    };
  }

  return {
    valid: true,
    errors: [],
    data: parsed.data,
  };
}
