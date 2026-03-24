/**
 * Canvas Theming Module
 *
 * Theme management, validation, and plugin system for visual customization.
 *
 * @module canvas/theming
 */

// Theme management
export {
  ThemeManager,
  LIGHT_THEME,
  DARK_THEME,
  THEMES,
  useTheme,
  globalThemeManager,
  type ThemeMode,
} from './themeManager';

// Theme validation
export {
  validateTheme,
  validateThemeJSON,
  checkContrast,
  CanvasThemeSchema,
  type ThemeValidationResult,
} from './themeValidator';

// Plugin system
export {
  PluginManager,
  globalPluginManager,
  type Plugin,
  type PluginMetadata,
  type PluginPermissions,
  type PluginAPI,
  type PluginTool,
  type RenderHook,
} from './pluginSystem';
