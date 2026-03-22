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
export { AppThemeProvider, ThemeToggleButton, ThemeStatusIndicator } from './AppThemeProvider';

// Re-export @ghatana/theme hooks for convenience
export {
    useTheme,
    useThemeMode,
    useResolvedTheme,
    useThemeToggle,
    useSystemTheme,
    useIsDarkMode,
    useIsLightMode,
} from '@ghatana/theme';

// Re-export types
export type { ThemeMode, ResolvedTheme } from '@ghatana/theme';
