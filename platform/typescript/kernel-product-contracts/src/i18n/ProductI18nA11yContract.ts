/**
 * K-009: Product i18n/a11y contract.
 * Route/action/card labels and accessibility metadata are validated.
 */

import { z } from "zod";

export type I18nKey = string;

export type I18nEntry = {
  key: I18nKey;
  defaultValue: string;
  parameters?: string[];
  context?: string;
};

export type A11yLabel = {
  id: string;
  label: string;
  description?: string;
  role?: 'button' | 'link' | 'input' | 'heading' | 'region' | 'navigation' | 'main' | 'complementary';
  liveRegion?: 'polite' | 'assertive';
};

export type A11yRequirement = {
  keyboardNavigable: boolean;
  screenReaderLabel: boolean;
  focusIndicator: boolean;
  colorContrast?: 'AA' | 'AAA';
  touchTargetSize?: number;
};

export type ProductI18nA11yContract = {
  version: string;
  locales: string[];
  entries: I18nEntry[];
  a11yLabels: A11yLabel[];
  a11yRequirements: A11yRequirement;
};

export const I18nKeySchema = z.string().trim().min(1).refine(
  (value) => !/\s/.test(value),
  "i18n keys must not contain whitespace"
);

export const I18nEntrySchema = z
  .object({
    key: I18nKeySchema,
    defaultValue: z.string().trim().min(1),
    parameters: z.array(z.string().trim().min(1)).optional(),
    context: z.string().trim().min(1).optional(),
  })
  .strict();

export const A11yLabelSchema = z
  .object({
    id: z.string().trim().min(1),
    label: z.string().trim().min(1),
    description: z.string().trim().min(1).optional(),
    role: z.enum([
      "button",
      "link",
      "input",
      "heading",
      "region",
      "navigation",
      "main",
      "complementary",
    ]).optional(),
    liveRegion: z.enum(["polite", "assertive"]).optional(),
  })
  .strict();

export const A11yRequirementSchema = z
  .object({
    keyboardNavigable: z.boolean(),
    screenReaderLabel: z.boolean(),
    focusIndicator: z.boolean(),
    colorContrast: z.enum(["AA", "AAA"]).optional(),
    touchTargetSize: z.number().positive().optional(),
  })
  .strict();

export const ProductI18nA11yContractSchema = z
  .object({
    version: z.string().trim().min(1),
    locales: z.array(z.string().trim().min(1)).min(1),
    entries: z.array(I18nEntrySchema),
    a11yLabels: z.array(A11yLabelSchema),
    a11yRequirements: A11yRequirementSchema,
  })
  .strict();

export function createI18nEntry(
  key: I18nKey,
  defaultValue: string,
  options?: Partial<Omit<I18nEntry, 'key' | 'defaultValue'>>
): I18nEntry {
  const result: I18nEntry = { key, defaultValue };
  
  if (options?.parameters) result.parameters = options.parameters;
  if (options?.context) result.context = options.context;
  
  return result;
}

export function createA11yLabel(
  id: string,
  label: string,
  options?: Partial<Omit<A11yLabel, 'id' | 'label'>>
): A11yLabel {
  const result: A11yLabel = { id, label };
  
  if (options?.description) result.description = options.description;
  if (options?.role) result.role = options.role;
  if (options?.liveRegion) result.liveRegion = options.liveRegion;
  
  return result;
}

export function createA11yRequirement(
  options?: Partial<A11yRequirement>
): A11yRequirement {
  const result: A11yRequirement = {
    keyboardNavigable: options?.keyboardNavigable ?? true,
    screenReaderLabel: options?.screenReaderLabel ?? true,
    focusIndicator: options?.focusIndicator ?? true,
  };
  
  if (options?.colorContrast) result.colorContrast = options.colorContrast;
  if (options?.touchTargetSize !== undefined) result.touchTargetSize = options.touchTargetSize;
  
  return result;
}

export function validateI18nEntry(entry: I18nEntry): boolean {
  return I18nEntrySchema.safeParse(entry).success;
}

export function validateA11yLabel(label: A11yLabel): boolean {
  return A11yLabelSchema.safeParse(label).success;
}

export function validateI18nKey(value: unknown): value is I18nKey {
  return I18nKeySchema.safeParse(value).success;
}

export function validateA11yRequirement(
  value: unknown
): value is A11yRequirement {
  return A11yRequirementSchema.safeParse(value).success;
}

export function validateProductI18nA11yContract(
  value: unknown
): value is ProductI18nA11yContract {
  return ProductI18nA11yContractSchema.safeParse(value).success;
}

export function hasI18nKey(contract: ProductI18nA11yContract, key: I18nKey): boolean {
  return contract.entries.some(e => e.key === key);
}

export function getI18nEntry(contract: ProductI18nA11yContract, key: I18nKey): I18nEntry | undefined {
  return contract.entries.find(e => e.key === key);
}
