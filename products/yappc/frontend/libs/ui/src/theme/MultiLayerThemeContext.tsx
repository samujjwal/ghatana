/**
 * Multi-Layer Theme Context
 *
 * Implements a composable, multi-layer theming system:
 * - Base Layer: Foundation tokens (colors, typography, spacing)
 * - Brand Layer: Brand-specific customizations
 * - Workspace Layer: Workspace-level preferences
 * - App Layer: Application-specific overrides
 *
 * Each layer can override tokens from previous layers, creating
 * a flexible and maintainable theming hierarchy.
 */

export {
  LayerPriority,
  MultiLayerThemeProvider,
  useAppTheme,
  useBrandTheme,
  useMultiLayerTheme,
  useThemeMode,
  useWorkspaceTheme,
  type MultiLayerThemeContextValue,
  type MultiLayerThemeProviderProps,
  type ThemeLayer,
} from '@yappc/theme';
