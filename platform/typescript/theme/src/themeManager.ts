import { z } from 'zod';
import { createTheme, type Theme, type ThemeLayer } from './theme';
import type { ThemeMode } from './types';
import {
  brandPresetSchema,
  defaultBrandPresets,
  themeLayerSchema,
  type BrandPreset,
  type BrandPresetInput,
} from './brandPresets';

export type BrandPresetSource = BrandPresetInput | BrandPresetInput[];
export type BrandPresetLoader = () => Promise<BrandPresetSource> | BrandPresetSource;

const brandPresetMap = new Map<string, BrandPreset>();

let loader: BrandPresetLoader | null = null;

const themeLayerArraySchema = z.array(themeLayerSchema);

function normalisePreset(input: BrandPresetInput): BrandPreset {
  const parsed = brandPresetSchema.parse(input) as BrandPreset;
  return {
    ...parsed,
    layers:
      parsed.layers?.map((layer) => ({
        ...themeLayerSchema.parse(layer),
        type: 'brand',
      })) ??
      (parsed.overrides
        ? [
            themeLayerSchema.parse({
              id: `${parsed.id}-brand`,
              name: parsed.name,
              type: 'brand' as const,
              description: parsed.description,
              overrides: parsed.overrides,
            }),
          ]
        : []),
  };
}

function registerPresetInternal(preset: BrandPresetInput) {
  const normalised = normalisePreset(preset);
  brandPresetMap.set(normalised.id, normalised);
}

defaultBrandPresets.forEach(registerPresetInternal);

export function registerBrandPreset(preset: BrandPresetInput): void {
  registerPresetInternal(preset);
}

export function registerBrandPresets(presets: BrandPresetInput[]): void {
  presets.forEach(registerPresetInternal);
}

export function getBrandPresets(): BrandPreset[] {
  return Array.from(brandPresetMap.values());
}

export function getBrandPreset(id: string): BrandPreset | undefined {
  return brandPresetMap.get(id);
}

export function setBrandPresetLoader(customLoader: BrandPresetLoader): void {
  loader = customLoader;
}

export async function loadBrandPresets(): Promise<BrandPreset[]> {
  if (loader) {
    const loaded = await loader();
    const list = Array.isArray(loaded) ? loaded : [loaded];
    list.forEach(registerPresetInternal);
  }
  return getBrandPresets();
}

export interface ApplyBrandPresetOptions {
  mode?: ThemeMode;
  extraLayers?: ThemeLayer[];
}

export function applyBrandPreset(id: string, options: ApplyBrandPresetOptions = {}): Theme {
  const preset = brandPresetMap.get(id);
  if (!preset) {
    throw new Error(`Brand preset '${id}' is not registered`);
  }

  const validatedExtraLayers = options.extraLayers
    ? themeLayerArraySchema.parse(options.extraLayers)
    : [];

  const layers: ThemeLayer[] = [...(preset.layers ?? []), ...validatedExtraLayers];

  const modePreference = options.mode ?? preset.mode ?? 'light';
  const mode = modePreference === 'system' ? (preset.mode ?? 'light') : modePreference;

  return createTheme(mode, layers);
}

export async function loadAndApplyBrandPreset(
  id: string,
  options: ApplyBrandPresetOptions = {}
): Promise<Theme> {
  await loadBrandPresets();
  return applyBrandPreset(id, options);
}
