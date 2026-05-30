/**
 * Product route surface gate for i18n and accessibility.
 *
 * This module provides shared gate functions that validate whether a route
 * is ready to be surfaced to users based on i18n and accessibility requirements.
 * Routes must have proper translations and accessibility support before being
 * discoverable in production.
 *
 * @doc.type module
 * @doc.purpose Shared gate for i18n and a11y route surface validation
 * @doc.layer platform
 */

import type { ProductRouteCapability } from './types';

/**
 * I18n requirement levels for route surfacing.
 */
export type I18nRequirement = 'none' | 'basic' | 'full';

/**
 * Accessibility requirement levels for route surfacing.
 */
export type A11yRequirement = 'none' | 'basic' | 'wcag-aa';

/**
 * Gate result indicating whether a route can be surfaced.
 */
export interface GateResult {
  readonly canSurface: boolean;
  readonly reason?: string;
  readonly missingI18nKeys?: string[];
  readonly missingA11yAttributes?: string[];
}

/**
 * Gate configuration for i18n and a11y requirements.
 */
export interface GateConfig {
  readonly i18nRequirement: I18nRequirement;
  readonly a11yRequirement: A11yRequirement;
  readonly supportedLocales: readonly string[];
  readonly pseudoLocaleEnabled: boolean;
}

/**
 * Default gate configuration for production.
 */
export const DEFAULT_GATE_CONFIG: GateConfig = {
  i18nRequirement: 'full',
  a11yRequirement: 'wcag-aa',
  supportedLocales: ['en', 'ne'],
  pseudoLocaleEnabled: true,
};

/**
 * Validates that a route has sufficient i18n support for the given requirement level.
 *
 * @param route - The route to validate
 * @param config - Gate configuration
 * @param availableTranslations - Map of available translation keys per locale
 * @returns Gate result for i18n validation
 */
export function validateI18nSupport(
  route: ProductRouteCapability,
  config: GateConfig,
  availableTranslations: Map<string, Set<string>>
): GateResult {
  if (config.i18nRequirement === 'none') {
    return { canSurface: true };
  }

  const missingKeys: string[] = [];
  const requiredKeys = getRequiredI18nKeys(route, config.i18nRequirement);

  for (const locale of config.supportedLocales) {
    const localeKeys = availableTranslations.get(locale) || new Set();
    
    for (const key of requiredKeys) {
      if (!localeKeys.has(key)) {
        missingKeys.push(`${locale}:${key}`);
      }
    }
  }

  // Check pseudo-locale if enabled
  if (config.pseudoLocaleEnabled) {
    const pseudoKeys = availableTranslations.get('en-XA') || new Set();
    for (const key of requiredKeys) {
      if (!pseudoKeys.has(key)) {
        missingKeys.push(`en-XA:${key}`);
      }
    }
  }

  if (missingKeys.length > 0) {
    return {
      canSurface: false,
      reason: `Missing i18n translations for ${missingKeys.length} keys`,
      missingI18nKeys: missingKeys,
    };
  }

  return { canSurface: true };
}

/**
 * Validates that a route has sufficient accessibility support for the given requirement level.
 *
 * @param route - The route to validate
 * @param config - Gate configuration
 * @returns Gate result for accessibility validation
 */
export function validateA11ySupport(
  route: ProductRouteCapability,
  config: GateConfig
): GateResult {
  if (config.a11yRequirement === 'none') {
    return { canSurface: true };
  }

  // If accessibility metadata is not present, skip validation (for KER-T06 compatibility)
  if (!route.accessibility) {
    return { canSurface: true };
  }

  const missingAttributes: string[] = [];
  const requiredAttributes = getRequiredA11yAttributes(route, config.a11yRequirement);

  for (const attr of requiredAttributes) {
    if (!route.accessibility[attr as keyof typeof route.accessibility]) {
      missingAttributes.push(attr);
    }
  }

  if (missingAttributes.length > 0) {
    return {
      canSurface: false,
      reason: `Missing accessibility attributes: ${missingAttributes.join(', ')}`,
      missingA11yAttributes: missingAttributes,
    };
  }

  return { canSurface: true };
}

/**
 * Combined gate that validates both i18n and a11y support.
 *
 * @param route - The route to validate
 * @param config - Gate configuration
 * @param availableTranslations - Map of available translation keys per locale
 * @returns Combined gate result
 */
export function validateRouteSurface(
  route: ProductRouteCapability,
  config: GateConfig = DEFAULT_GATE_CONFIG,
  availableTranslations: Map<string, Set<string>> = new Map()
): GateResult {
  const i18nResult = validateI18nSupport(route, config, availableTranslations);
  if (!i18nResult.canSurface) {
    return i18nResult;
  }

  const a11yResult = validateA11ySupport(route, config);
  if (!a11yResult.canSurface) {
    return a11yResult;
  }

  return { canSurface: true };
}

/**
 * Filters a list of routes to only those that pass the surface gate.
 *
 * @param routes - Routes to filter
 * @param config - Gate configuration
 * @param availableTranslations - Map of available translation keys per locale
 * @returns Filtered routes that pass the gate
 */
export function filterSurfaceableRoutes(
  routes: readonly ProductRouteCapability[],
  config: GateConfig = DEFAULT_GATE_CONFIG,
  availableTranslations: Map<string, Set<string>> = new Map()
): readonly ProductRouteCapability[] {
  return routes.filter(route => {
    const result = validateRouteSurface(route, config, availableTranslations);
    return result.canSurface;
  });
}

/**
 * Gets required i18n keys for a route based on requirement level.
 */
function getRequiredI18nKeys(route: ProductRouteCapability, level: I18nRequirement): string[] {
  const baseKey = route.group || 'common';
  const keys: string[] = [];

  if (level === 'basic' || level === 'full') {
    keys.push(`${baseKey}.${route.label.toLowerCase().replace(/\s+/g, '-')}`);
  }

  if (level === 'full') {
    keys.push(`${baseKey}.description`);
    keys.push(`${baseKey}.aria-label`);
    keys.push('error.unknown');
    keys.push('validation.required');
  }

  return keys;
}

/**
 * Gets required accessibility attributes for a route based on requirement level.
 */
function getRequiredA11yAttributes(route: ProductRouteCapability, level: A11yRequirement): string[] {
  const attributes: string[] = [];

  if (level === 'basic' || level === 'wcag-aa') {
    attributes.push('titleKey');
    attributes.push('descriptionKey');
  }

  if (level === 'wcag-aa') {
    attributes.push('ariaLabelKey');
    attributes.push('keyboardNavigable');
    attributes.push('screenReaderAnnounce');
  }

  return attributes;
}
