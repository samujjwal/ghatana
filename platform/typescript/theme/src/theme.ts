import {
  palette,
  lightColors,
  darkColors,
  spacing,
  semanticSpacing,
  density,
  fontFamily,
  fontSize,
  fontWeight,
  lineHeight,
  letterSpacing,
  typography,
  borderRadius,
  borderWidth,
  componentRadius,
  shapeVariants,
  breakpoints,
  semanticBreakpoints,
  mediaQueries,
  containerMaxWidths,
  touchTargets,
  durations,
  easings,
  properties as transitionProperties,
  transitions,
  lightShadows,
  darkShadows,
  elevationLevels,
  zIndex,
  semanticZIndex,
  componentZIndex,
} from '@ghatana/tokens';

import type { ResolvedTheme } from './types';

export const baseThemeTokens = {
  palette,
  lightColors,
  darkColors,
  spacing,
  semanticSpacing,
  density,
  fontFamily,
  fontSize,
  fontWeight,
  lineHeight,
  letterSpacing,
  typography,
  borderRadius,
  borderWidth,
  componentRadius,
  shapeVariants,
  breakpoints,
  semanticBreakpoints,
  mediaQueries,
  containerMaxWidths,
  touchTargets,
  durations,
  easings,
  transitionProperties,
  transitions,
  lightShadows,
  darkShadows,
  elevationLevels,
  zIndex,
  semanticZIndex,
  componentZIndex,
} as const;

export type ThemeTokens = typeof baseThemeTokens;

export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends Array<infer U>
    ? U[] // arrays are replaced entirely
    : T[P] extends object
    ? DeepPartial<T[P]>
    : T[P];
};

export type ThemeLayerType = 'base' | 'brand' | 'workspace' | 'app';

export interface ThemeLayer {
  id: string;
  name: string;
  type: ThemeLayerType;
  description?: string;
  overrides?: DeepPartial<ThemeTokens>;
  metadata?: Record<string, unknown>;
}

export interface ThemeComputed extends Omit<ThemeTokens, 'lightColors' | 'darkColors'> {
  colors: {
    palette: typeof palette;
    light: typeof lightColors;
    dark: typeof darkColors;
    // Resolved color groups (convenience properties expected by consumers)
    // Accept either light or dark shaped objects to allow merged/resolved outputs
    background: typeof lightColors.background | typeof darkColors.background;
    text: typeof lightColors.text | typeof darkColors.text;
    divider: typeof lightColors.divider | typeof darkColors.divider;
    border: typeof lightColors.border | typeof darkColors.border;
    action: typeof lightColors.action | typeof darkColors.action;
  };
  mode: ResolvedTheme;
}

export interface Theme {
  mode: ResolvedTheme;
  layers: ThemeLayer[];
  computed: ThemeComputed;
}

/**
 * Create a deep clone of the base theme tokens to avoid accidental mutation.
 */
function cloneTokens(): ThemeTokens {
  return cloneDeep(baseThemeTokens);
}

function cloneDeep<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map((item) => cloneDeep(item)) as unknown as T;
  }

  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {};
    for (const key of Object.keys(value as object)) {
      result[key] = cloneDeep((value as Record<string, unknown>)[key]);
    }
    return result as T;
  }

  return value;
}

function mergeTokens<T extends object>(target: T, source: DeepPartial<T>): T {
  // Handle array case
  if (Array.isArray(target)) {
    if (!Array.isArray(source)) {
      return target;
    }
    return [...target, ...source] as unknown as T;
  }

  // Create a shallow copy of the target
  const result = { ...target } as Record<string, unknown>;

  // Only proceed if source is a non-array object
  if (source && typeof source === 'object' && !Array.isArray(source)) {
    for (const key in source) {
      if (Object.prototype.hasOwnProperty.call(source, key)) {
        const sourceValue = (source as Record<string, unknown>)[key];
        const targetValue = (target as Record<string, unknown>)[key];

        if (
          sourceValue &&
          typeof sourceValue === 'object' &&
          targetValue &&
          typeof targetValue === 'object' &&
          !Array.isArray(sourceValue) &&
          !Array.isArray(targetValue)
        ) {
          result[key] = mergeTokens(
            targetValue as Record<string, unknown>,
            sourceValue as Record<string, unknown>
          );
        } else {
          result[key] = sourceValue;
        }
      }
    }
  }
  return result as T;
}

const LAYER_ORDER: Record<ThemeLayerType, number> = {
  base: 0,
  brand: 1,
  workspace: 2,
  app: 3,
};

/**
 * Apply theme layers to the base tokens.
 */
export function applyThemeLayers(layers: ThemeLayer[]): ThemeTokens {
  const sortedLayers = [...layers].sort(
    (a, b) => LAYER_ORDER[a.type] - LAYER_ORDER[b.type]
  );

  return sortedLayers.reduce((tokens, layer) => {
    if (!layer.overrides) {
      return tokens;
    }
    return mergeTokens(tokens, layer.overrides);
  }, cloneTokens());
}

/**
 * Resolve colors depending on the active theme mode.
 */
export function resolveThemeColors(tokens: ThemeTokens, mode: ResolvedTheme) {
  const light = tokens.lightColors;
  const dark = tokens.darkColors;

  if (mode === 'dark') {
    return {
      palette: tokens.palette,
      light,
      dark,
      background: dark.background,
      text: dark.text,
      divider: dark.divider,
      border: dark.border,
      action: dark.action,
    } as ThemeComputed['colors'];
  }

  return {
    palette: tokens.palette,
    light,
    dark,
    background: light.background,
    text: light.text,
    divider: light.divider,
    border: light.border,
    action: light.action,
  } as ThemeComputed['colors'];
}

/**
 * Create a full theme configuration.
 */
export function createTheme(
  mode: ResolvedTheme = 'light',
  layers: ThemeLayer[] = []
): Theme {
  const computedTokens = applyThemeLayers(layers);
  return {
    mode,
    layers: [...layers],
    computed: {
      ...computedTokens,
      colors: resolveThemeColors(computedTokens, mode),
      mode, // Add mode to computed object
    },
  };
}

/**
 * Convert theme tokens into flat CSS custom properties.
 * This helper keeps naming predictable (kebab-case with --gh prefix).
 */
export function themeToCSSVariables(theme: Theme): Record<string, string> {
  const vars: Record<string, string> = {};

  function toKebab(value: string): string {
    return value
      .replace(/([a-z])([A-Z])/g, '$1-$2')
      .replace(/[\s_]+/g, '-')
      .toLowerCase();
  }

  function traverse(input: unknown, path: string[] = []) {
    if (!input) return;

    if (Array.isArray(input)) {
      input.forEach((item, index) => {
        traverse(item, [...path, String(index)]);
      });
      return;
    }

    if (typeof input === 'object') {
      for (const [key, value] of Object.entries(input)) {
        traverse(value, [...path, toKebab(key)]);
      }
      return;
    }

    const variableName = ['--gh', ...path].join('-');
    if (typeof input === 'number') {
      vars[variableName] = String(input);
    } else if (typeof input === 'string' || typeof input === 'boolean') {
      vars[variableName] = String(input);
    }
  }

  traverse(theme.computed.colors, ['color']);

  return vars;
}
