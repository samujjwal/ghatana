/**
 * Centralized Form Validation Patterns
 *
 * Provides Zod schemas and React Hook Form resolver helpers for consistent
 * form validation across the data-cloud UI. Addresses FINDING-DC-UI-M3.
 *
 * UI components (FieldError, FormField) live in `./FormField.tsx`.
 *
 * @doc.type utility
 * @doc.purpose Centralised form validation schemas and helpers
 * @doc.layer frontend
 * @doc.pattern Form Validation
 */

import { zodResolver } from "@hookform/resolvers/zod";
import {
  useForm,
  type DefaultValues,
  type FieldValues,
  type Resolver,
  type UseFormProps,
} from "react-hook-form";
import { z } from "zod";

// ─────────────────────────────────────────────────────────────────────────────
// Common field constraints (avoid magic numbers throughout forms)
// ─────────────────────────────────────────────────────────────────────────────

export const FIELD_LIMITS = {
  NAME_MIN: 1,
  NAME_MAX: 128,
  DESCRIPTION_MAX: 512,
  SLUG_MAX: 64,
  TAG_MAX_COUNT: 20,
  TAG_ITEM_MAX: 64,
  /** ISO 8601 regex — basic validation. */
  ISO8601_REGEX:
    /^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2})?(Z|[+-]\d{2}:\d{2})?)?$/,
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Reusable base schemas
// ─────────────────────────────────────────────────────────────────────────────

/** Trimmed, non-empty name string. */
export const nameSchema = z
  .string()
  .min(FIELD_LIMITS.NAME_MIN, "Name is required")
  .max(
    FIELD_LIMITS.NAME_MAX,
    `Name must be at most ${FIELD_LIMITS.NAME_MAX} characters`,
  )
  .transform((v) => v.trim());

/** Optional plain-text description. */
export const descriptionSchema = z
  .string()
  .max(
    FIELD_LIMITS.DESCRIPTION_MAX,
    `Description must be at most ${FIELD_LIMITS.DESCRIPTION_MAX} characters`,
  )
  .optional();

/** URL-safe slug: lowercase letters, digits, hyphens. */
export const slugSchema = z
  .string()
  .regex(
    /^[a-z0-9-]+$/,
    "Slug may only contain lowercase letters, digits, and hyphens",
  )
  .max(
    FIELD_LIMITS.SLUG_MAX,
    `Slug must be at most ${FIELD_LIMITS.SLUG_MAX} characters`,
  )
  .optional();

/** Array of string tags. */
export const tagsSchema = z
  .array(z.string().max(FIELD_LIMITS.TAG_ITEM_MAX))
  .max(
    FIELD_LIMITS.TAG_MAX_COUNT,
    `Maximum ${FIELD_LIMITS.TAG_MAX_COUNT} tags allowed`,
  )
  .optional();

// ─────────────────────────────────────────────────────────────────────────────
// Domain schemas
// ─────────────────────────────────────────────────────────────────────────────

/** Collection creation / update form. */
export const collectionSchema = z.object({
  name: nameSchema,
  description: descriptionSchema,
  slug: slugSchema,
  tags: tagsSchema,
  isPublic: z.boolean().default(false),
});

export type CollectionFormValues = z.infer<typeof collectionSchema>;

/** Alert rule form. */
export const alertRuleSchema = z.object({
  name: nameSchema,
  description: descriptionSchema,
  severity: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
  enabled: z.boolean().default(true),
  condition: z.string().min(1, "Condition expression is required"),
  notificationChannels: z
    .array(z.string())
    .min(1, "At least one notification channel is required"),
});

export type AlertRuleFormValues = z.infer<typeof alertRuleSchema>;

/** Workflow form. */
export const workflowSchema = z.object({
  name: nameSchema,
  description: descriptionSchema,
  tags: tagsSchema,
  schedule: z.string().optional(),
  timeoutSeconds: z
    .number()
    .int()
    .min(1, "Timeout must be at least 1 second")
    .max(86_400, "Timeout cannot exceed 24 hours")
    .optional(),
});

export type WorkflowFormValues = z.infer<typeof workflowSchema>;

/** Settings form. */
export const settingsSchema = z.object({
  displayName: nameSchema,
  email: z.string().email("Invalid email address"),
  notificationsEnabled: z.boolean().default(true),
  theme: z.enum(["system", "light", "dark"]).default("system"),
  defaultPageSize: z.number().int().min(10).max(200).default(50),
});

export type SettingsFormValues = z.infer<typeof settingsSchema>;

// ─────────────────────────────────────────────────────────────────────────────
// Hook helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thin wrapper over `useForm` that automatically wires up the Zod resolver.
 *
 * @example
 * ```tsx
 * const form = useFormWithValidation(collectionSchema, { defaultValues: { isPublic: false } });
 * ```
 */
export function useFormWithValidation<T extends FieldValues>(
  schema: z.ZodType<T>,
  options?: Omit<UseFormProps<T>, "resolver"> & {
    defaultValues?: DefaultValues<T>;
  },
) {
  return useForm<T>({
    ...options,

    resolver: zodResolver(schema as any) as unknown as Resolver<T>,
  });
}
