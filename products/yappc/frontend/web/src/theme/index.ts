/**
 * YAPPC Theme System
 *
 * Centralized theme management exports
 *
 * @doc.type module
 * @doc.purpose Theme system public API
 * @doc.layer infrastructure
 */

// Provider and components
export {
  AppThemeProvider,
  ThemeProvider,
  ThemeToggleButton,
  ThemeStatusIndicator,
  useTheme,
  useThemeMode,
  useResolvedTheme,
  useThemeToggle,
  useSystemTheme,
  useIsDarkMode,
  useIsLightMode,
} from './AppThemeProvider';

export type { ThemeMode, ResolvedTheme } from './AppThemeProvider';
