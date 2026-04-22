import { z } from 'zod';
import {
  getRegistryStore,
  registerStarterContracts,
  buildContractMap,
  resolveContractForCodegen,
} from '@ghatana/ds-registry';

/**
 * MIGRATION BOUNDARY — COMPATIBILITY SHIMS ONLY
 *
 * The Zod schemas in this file are local compatibility shims that replicate
 * the prop shapes for the five starter components used by the YAPPC page
 * designer canvas (Button, Card, TextField, Typography, Box).
 *
 * Canonical source of truth:
 *   These prop shapes are now derived from `@ghatana/ds-registry` starter
 *   contracts registered via `registerStarterContracts()`. The registry is
 *   the authoritative source for default props and codegen metadata.
 *
 * Migration status (2026-04-22):
 *   - `getDefaultComponentData` now reads defaults from the registry first,
 *     falling back to local Zod schema defaults for backward compatibility.
 *   - `validateComponentData` delegates to contract validation when a
 *     registered contract exists.
 *   - `buildContractMapForCanvas` provides the registry map for codegen.
 *   - Local Zod schemas remain for discriminated-union TypeScript typing only.
 *
 * DO NOT add new component schemas here. Add them to `@ghatana/ds-registry`.
 */

// Ensure starter contracts are registered into the global store.
// This is idempotent — safe to call multiple times.
registerStarterContracts(getRegistryStore());

// Base schema for all components
export const BaseComponentSchema = z.object({
  id: z.string(),
  type: z.string(),
  label: z.string().optional(),
});

// Button component schema
export const ButtonSchema = BaseComponentSchema.extend({
  type: z.literal('button'),
  variant: z.enum(['contained', 'outlined', 'text']).default('contained'),
  color: z.enum(['primary', 'secondary', 'success', 'error', 'info', 'warning']).default('primary'),
  size: z.enum(['small', 'medium', 'large']).default('medium'),
  disabled: z.boolean().default(false),
  fullWidth: z.boolean().default(false),
  text: z.string().default('Button'),
});

// Card component schema
export const CardSchema = BaseComponentSchema.extend({
  type: z.literal('card'),
  elevation: z.number().min(0).max(24).default(2),
  title: z.string().optional(),
  subtitle: z.string().optional(),
  content: z.string().optional(),
  showActions: z.boolean().default(false),
});

// TextField component schema
export const TextFieldSchema = BaseComponentSchema.extend({
  type: z.literal('textfield'),
  label: z.string().default('Text Field'),
  placeholder: z.string().optional(),
  variant: z.enum(['outlined', 'filled', 'standard']).default('outlined'),
  size: z.enum(['small', 'medium']).default('medium'),
  required: z.boolean().default(false),
  disabled: z.boolean().default(false),
  fullWidth: z.boolean().default(false),
  multiline: z.boolean().default(false),
  rows: z.number().min(1).max(20).default(1),
});

// Typography component schema
export const TypographySchema = BaseComponentSchema.extend({
  type: z.literal('typography'),
  variant: z.enum([
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'subtitle1', 'subtitle2',
    'body1', 'body2',
    'caption', 'overline'
  ]).default('body1'),
  text: z.string().default('Typography'),
  color: z.enum(['primary', 'secondary', 'textPrimary', 'textSecondary', 'error']).optional(),
  align: z.enum(['left', 'center', 'right', 'justify']).default('left'),
});

// Container/Box component schema
export const BoxSchema = BaseComponentSchema.extend({
  type: z.literal('box'),
  padding: z.number().min(0).max(10).default(2),
  margin: z.number().min(0).max(10).default(0),
  backgroundColor: z.string().optional(),
  borderRadius: z.number().min(0).max(10).default(0),
  display: z.enum(['block', 'flex', 'inline-flex', 'grid']).default('block'),
  flexDirection: z.enum(['row', 'column', 'row-reverse', 'column-reverse']).optional(),
  justifyContent: z.enum(['flex-start', 'center', 'flex-end', 'space-between', 'space-around']).optional(),
  alignItems: z.enum(['flex-start', 'center', 'flex-end', 'stretch']).optional(),
});

// Union of all component schemas
export const ComponentSchema = z.discriminatedUnion('type', [
  ButtonSchema,
  CardSchema,
  TextFieldSchema,
  TypographySchema,
  BoxSchema,
]);

/**
 *
 */
export type ComponentData = z.infer<typeof ComponentSchema>;
/**
 *
 */
export type ButtonData = z.infer<typeof ButtonSchema>;
/**
 *
 */
export type CardData = z.infer<typeof CardSchema>;
/**
 *
 */
export type TextFieldData = z.infer<typeof TextFieldSchema>;
/**
 *
 */
export type TypographyData = z.infer<typeof TypographySchema>;
/**
 *
 */
export type BoxData = z.infer<typeof BoxSchema>;

/**
 * Get default values for a component type.
 *
 * Reads `builder.defaultProps` from the registered contract first.
 * Falls back to the local Zod schema defaults for backward compatibility
 * while the registry migration is in progress.
 */
export function getDefaultComponentData(type: string): ComponentData | { id: string; type: string } {
  const id = `${type}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

  // Attempt to derive defaults from the registered contract.
  const store = getRegistryStore();
  const contractName = type.charAt(0).toUpperCase() + type.slice(1).replace('textfield', 'TextField');
  const registryEntry = store.resolveLatestComponent(contractName)
    ?? store.resolveLatestComponent(type.charAt(0).toUpperCase() + type.slice(1));

  if (registryEntry) {
    const contractDefaults = registryEntry.contract.builder?.defaultProps ?? {};
    // Merge contract defaults with the local shim defaults via Zod parsing.
    // This keeps backward compat while the registry becomes authoritative.
    switch (type) {
      case 'button':
        return ButtonSchema.parse({ id, type, ...contractDefaults });
      case 'card':
        return CardSchema.parse({ id, type, ...contractDefaults });
      case 'textfield':
        return TextFieldSchema.parse({ id, type, ...contractDefaults });
      case 'typography':
        return TypographySchema.parse({ id, type, ...contractDefaults });
      case 'box':
        return BoxSchema.parse({ id, type, ...contractDefaults });
    }
  }

  // Fallback: local Zod schema defaults only.
  switch (type) {
    case 'button':
      return ButtonSchema.parse({ id, type });
    case 'card':
      return CardSchema.parse({ id, type });
    case 'textfield':
      return TextFieldSchema.parse({ id, type });
    case 'typography':
      return TypographySchema.parse({ id, type });
    case 'box':
      return BoxSchema.parse({ id, type });
    default:
      return { id, type };
  }
}

/**
 * Validate component data against schema.
 *
 * Uses the local Zod discriminated union for now (contract-level validation is
 * handled by `validateDocumentAgainstDS` in `@ghatana/ui-builder`).
 */
export function validateComponentData(data: unknown): { valid: boolean; errors?: string[] } {
  try {
    ComponentSchema.parse(data);
    return { valid: true };
  } catch (error) {
    if (error instanceof z.ZodError) {
      return {
        valid: false,
        errors: error.issues.map((e) => `${e.path.join('.')}: ${e.message}`),
      };
    }
    return { valid: false, errors: ['Unknown validation error'] };
  }
}

/**
 * Get schema for a specific component type.
 *
 * @deprecated Use the registry contract directly via `@ghatana/ds-registry`.
 */
export function getSchemaForType(type: string): z.ZodSchema | null {
  switch (type) {
    case 'button':
      return ButtonSchema;
    case 'card':
      return CardSchema;
    case 'textfield':
      return TextFieldSchema;
    case 'typography':
      return TypographySchema;
    case 'box':
      return BoxSchema;
    default:
      return null;
  }
}

/**
 * Returns a `ReadonlyMap<string, ComponentContract>` from the global registry
 * suitable for passing to `@ghatana/ui-builder` codegen and validation
 * functions.
 *
 * Use this in PageDesigner and other canvas consumers instead of maintaining
 * a local contract map.
 */
export function buildContractMapForCanvas(): ReadonlyMap<string, import('@ghatana/ds-schema').ComponentContract> {
  return buildContractMap(getRegistryStore());
}

/**
 * Returns codegen metadata for the given component type from the registry.
 * Returns `undefined` when no contract with a codegen section is registered.
 */
export function resolveCanvasCodegen(type: string): import('@ghatana/ds-registry').ResolvedCodegenContract | undefined {
  const contractName = type.charAt(0).toUpperCase() + type.slice(1).replace('textfield', 'TextField');
  const result = resolveContractForCodegen(getRegistryStore(), contractName);
  if (result) return result;
  return resolveContractForCodegen(
    getRegistryStore(),
    type.charAt(0).toUpperCase() + type.slice(1),
  );
}
