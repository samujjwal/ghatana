import { z } from 'zod';
import type { ThemeMode } from './types';
import type { DeepPartial, ThemeTokens, ThemeLayer } from './theme';
import { baseThemeTokens } from './theme';
import { palette } from '@ghatana/tokens';

export interface BrandPreset {
  id: string;
  name: string;
  description?: string;
  mode?: Exclude<ThemeMode, 'system'>;
  overrides?: DeepPartial<ThemeTokens>;
  layers?: ThemeLayer[];
  metadata?: Record<string, unknown>;
}

const scalarOverrideSchema = z.union([z.string(), z.number(), z.boolean()]);

const metadataSchema = z.record(z.string(), z.unknown());

const buildOverrideSchema = (sample: unknown): z.ZodTypeAny => {
  if (Array.isArray(sample)) {
    if (sample.length === 0) {
      return z.array(z.union([scalarOverrideSchema, z.record(z.string(), z.unknown())]));
    }

    const itemSchemas = sample.map((item) => buildOverrideSchema(item));
    return z.array(z.union([...itemSchemas, scalarOverrideSchema, z.record(z.string(), z.unknown())]));
  }

  if (sample && typeof sample === 'object') {
    const shape: Record<string, z.ZodTypeAny> = {};
    for (const [key, value] of Object.entries(sample as Record<string, unknown>)) {
      shape[key] = buildOverrideSchema(value).optional();
    }
    return z.object(shape).strict();
  }

  return scalarOverrideSchema;
};

export const themeOverridesSchema = buildOverrideSchema(baseThemeTokens) as z.ZodType<DeepPartial<ThemeTokens>>;

export const themeLayerSchema = z
  .object({
    id: z.string().min(1, 'Theme layer id is required'),
    name: z.string().min(1, 'Theme layer name is required'),
    type: z.union([z.literal('base'), z.literal('brand'), z.literal('workspace'), z.literal('app')]),
    description: z.string().optional(),
    overrides: themeOverridesSchema.optional(),
    metadata: metadataSchema.optional(),
  })
  .strict() as unknown as z.ZodType<ThemeLayer>;

export const brandPresetSchema = z
  .object({
    id: z.string().min(1, 'Brand preset id is required'),
    name: z.string().min(1, 'Brand preset name is required'),
    description: z.string().optional(),
    mode: z.union([z.literal('light'), z.literal('dark')]).optional(),
    overrides: themeOverridesSchema.optional(),
    layers: z.array(themeLayerSchema).optional(),
    metadata: metadataSchema.optional(),
  })
  .strict() as unknown as z.ZodType<BrandPreset>;

export type BrandPresetInput = z.input<typeof brandPresetSchema>;

export const defaultBrandPresets: BrandPreset[] = [
  {
    id: 'ghatana-default',
    name: 'Ghatana Default',
    description: 'Unified palette used across platform experiences.',
    layers: [
      {
        id: 'ghatana-default',
        name: 'Ghatana',
        type: 'brand',
        overrides: {
          palette: {
            primary: {
              500: '#1976d2',
              600: '#1565c0',
              contrastText: '#ffffff',
            },
            secondary: {
              500: '#009688',
              600: '#00897b',
              contrastText: '#ffffff',
            },
          },
        },
      },
    ],
  },
  {
    id: 'night-ops',
    name: 'Night Ops',
    description: 'High contrast preset optimised for dark dashboards.',
    mode: 'dark',
    layers: [
      {
        id: 'night-ops',
        name: 'Night Ops',
        type: 'brand',
        overrides: {
          palette: {
            primary: {
              500: '#1976d2',
              600: '#1565c0',
              contrastText: '#ffffff',
            },
            secondary: {
              500: '#009688',
              600: '#00897b',
              contrastText: '#ffffff',
            },
            neutral: {
              900: '#212121',
            },
          },
          lightColors: {
            background: {
              default: '#FAFAFA',
              paper: '#ffffff',
            },
          },
          darkColors: {
            background: {
              default: '#0d1117',
              paper: '#161b22',
            },
          },
        },
      },
    ],
  },
];
