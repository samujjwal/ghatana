import {
  palette,
  lightColors,
  darkColors,
  getColor,
  getSemanticColor,
} from './colors';
import {
  spacing,
  semanticSpacing,
  density,
  getSpacing,
  getSpacingCSS,
  getSemanticSpacing,
  getSemanticSpacingCSS,
  getDensitySpacing,
} from './spacing';
import {
  fontFamily,
  fontSize,
  fontWeight,
  lineHeight,
  letterSpacing,
  typography,
} from './typography';
import { lightShadows, darkShadows, elevationLevels } from './shadows';
import { borderRadius, borderWidth, componentRadius, shapeVariants } from './borders';
import { breakpoints, semanticBreakpoints, mediaQueries, containerMaxWidths, touchTargets } from './breakpoints';
import { transitions, animations, reducedMotion, durations, easings, properties as transitionProperties } from './transitions';
import { zIndex, semanticZIndex, componentZIndex } from './z-index';

/**
 * Aggregated view of all exported design tokens.
 *
 * Backward-compatible object that exposes both structured and flat token access.
 * Provides aliases for UI components that expect flat property access patterns.
 */
export const tokens = {
  colors: {
    palette,
    light: lightColors,
    dark: darkColors,
    // Flat aliases for legacy code
    ...palette,
    white: '#ffffff' as const,
    black: '#000000' as const,
  },
  spacing: {
    scale: spacing,
    semantic: semanticSpacing,
    density,
    ...spacing,
  },
  typography: {
    fontFamily,
    fontSize,
    fontWeight: {
      ...fontWeight,
      normal: fontWeight.regular ?? 400,
    } as typeof fontWeight & { readonly normal: number },
    lineHeight,
    letterSpacing,
    typography,
  },
  shadows: {
    light: lightShadows,
    dark: darkShadows,
    elevationLevels,
    ...lightShadows,
    // Ensure named shadows are accessible
    sm: lightShadows[1],
    md: lightShadows[2],
    lg: lightShadows[4],
    xl: lightShadows[6],
  } as const & Record<string, string>,
  // Expose borderRadius and borderWidth at top level for UI component access
  borderRadius: {
    ...borderRadius,
  } as typeof borderRadius,
  borderWidth: {
    ...borderWidth,
    // Add numeric indexes for backward compat with tokens.borderWidth[1]
    0: borderWidth.none,
    1: borderWidth.thin,
    2: borderWidth.medium,
    4: borderWidth.thick,
  } as typeof borderWidth & Record<number, number | string>,
  componentRadius,
  shapeVariants,
  transitions: {
    transitions,
    animations,
    reducedMotion,
    durations,
    easings,
    properties: transitionProperties,
    // Legacy aliases
    duration: durations,
    easing: easings,
  } as const,
  zIndex: {
    ...zIndex,
    // Add common zIndex variants
    appBar: 1200,
  } as typeof zIndex & Record<string, number>,
  semanticZIndex,
  componentZIndex,
  breakpoints: {
    breakpoints,
    semanticBreakpoints,
    mediaQueries,
    containerMaxWidths,
    touchTargets,
  },
} as const;

export type TokenRegistry = typeof tokens;

/**
 * Convenience helpers are re-exported so automation scripts can access them from one place.
 */
export const helpers = {
  spacing: {
    getSpacing,
    getSpacingCSS,
    getSemanticSpacing,
    getSemanticSpacingCSS,
    getDensitySpacing,
  },
  colors: {
    getColor,
    getSemanticColor,
  },
};

export type TokenHelperRegistry = typeof helpers;
