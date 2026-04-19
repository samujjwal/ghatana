import { z } from 'zod';

import { DataClassificationSchema } from './contract';

export const BuilderPlatformTargetSchema = z.enum([
  'react',
  'html',
  'web-components',
  'react-native',
  'swiftui',
  'jetpack-compose',
  'figma',
]);

export type BuilderPlatformTarget = z.infer<typeof BuilderPlatformTargetSchema>;

export const BuilderComponentCapabilitySchema = z.object({
  interactive: z.boolean().default(false),
  collection: z.boolean().default(false),
  virtualizable: z.boolean().default(false),
  async: z.boolean().default(false),
  privacy: DataClassificationSchema.default('public'),
  optimizedFor: z.array(z.string()).default([]),
});

export type BuilderComponentCapability = z.infer<typeof BuilderComponentCapabilitySchema>;

export const BuilderComponentSemanticsSchema = z.object({
  role: z.string().optional(),
  eventNames: z.array(z.string()).default([]),
});

export type BuilderComponentSemantics = z.infer<typeof BuilderComponentSemanticsSchema>;

export const BuilderSlotExposureSchema = z.enum([
  'children',
  'prop',
]);

export type BuilderSlotExposure = z.infer<typeof BuilderSlotExposureSchema>;

export const BuilderComponentSlotManifestSchema = z.object({
  name: z.string().min(1),
  allowsMultiple: z.boolean().default(true),
  required: z.boolean().default(false),
  features: z.array(z.string()).default([]),
  exposure: BuilderSlotExposureSchema.default('prop'),
  semantics: BuilderComponentSemanticsSchema.default({
    eventNames: [],
  }),
});

export type BuilderComponentSlotManifest = z.infer<typeof BuilderComponentSlotManifestSchema>;

export const BuilderComponentManifestSchema = z.object({
  name: z.string().min(1),
  version: z.string().default('1.0.0'),
  targets: z.array(BuilderPlatformTargetSchema).default(['react']),
  features: z.array(z.string()).default([]),
  semantics: BuilderComponentSemanticsSchema.default({
    eventNames: [],
  }),
  slots: z.array(BuilderComponentSlotManifestSchema).default([]),
  capabilities: BuilderComponentCapabilitySchema.default({
    interactive: false,
    collection: false,
    virtualizable: false,
    async: false,
    privacy: 'public',
    optimizedFor: [],
  }),
  dataClassification: DataClassificationSchema.optional(),
  reviewRequired: z.boolean().default(false),
});

export type BuilderComponentManifest = z.infer<typeof BuilderComponentManifestSchema>;

export function validateBuilderComponentManifest(data: unknown):
  | { success: true; data: BuilderComponentManifest }
  | { success: false; errors: z.ZodError } {
  const result = BuilderComponentManifestSchema.safeParse(data);
  return result.success
    ? { success: true, data: result.data }
    : { success: false, errors: result.error };
}
