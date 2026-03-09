/**
 * Minimal theme palette interface — replaces @mui/material Theme type.
 * This removes the hard dependency on MUI while keeping the same runtime behavior.
 */
interface ThemePalette {
  [key: string]: { main: string } | undefined;
  primary: { main: string };
}

interface Theme {
  palette: ThemePalette;
}

/**
 * Safely resolve a palette key that can be passed into MUI `color` props.
 * Returns a key that is present on theme.palette and exposes `.main` or
 * falls back to the provided fallback (defaults to 'primary').
 */
export function resolveThemeColor(theme: Theme, key: string | undefined, fallback: string = 'primary') {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const p: Record<string, { main?: string } | undefined> = (theme && (theme as unknown).palette) || {};
  if (key && p[key] && typeof p[key].main === 'string') return key;
  if (fallback && p[fallback] && typeof p[fallback].main === 'string') return fallback;
  return 'primary';
}

export default resolveThemeColor;

/**
 * Typed wrapper for MUI `color` props.
 *
 * Many MUI components accept a restricted set of palette keys via
 * OverridableStringUnion. Returning this type from a helper lets callers
 * pass the result directly into component `color` props without `as any`.
 *
 * Note: at runtime this simply resolves to a string key that exists on
 * theme.palette (or falls back to provided fallback). The typing here is
 * what prevents the need for ad-hoc casts at call sites.
 */
export function resolveMuiColor(
  theme: Theme,
  key: string | undefined,
  fallback: string = 'primary',
): string {
  // runtime value is a string; cast to the OverridableStringUnion to satisfy callers
  return resolveThemeColor(theme, key, fallback);
}

/**
 * Safely return the actual CSS color string for a palette key (e.g. theme.palette[key].main).
 *
 * This helper centralizes the unsafe indexed access into theme.palette so we can keep a
 * single, justified suppression for the security/detect-object-injection rule here. Callers
 * should prefer using `resolveMuiColor` when the value will be passed to MUI `color` props
 * (which accept palette keys) and `getPaletteMain` when a concrete CSS color string is required
 * (for inline styles, sx values, or canvas rendering).
 */
export function getPaletteMain(theme: Theme, key: string | undefined, fallback: string = 'primary'): string {
  const paletteKey = resolveThemeColor(theme, key, fallback);
  // We intentionally perform a dynamic lookup here. The security rule flags generic object
  // injection for indexed access; this is acceptable because the source of `paletteKey` is
  // constrained by resolveThemeColor which only returns keys known to exist on theme.palette.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const p: ThemePalette = theme.palette;
  return (p[paletteKey]?.main as string) ?? theme.palette.primary.main;
}
