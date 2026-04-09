/**
 * @dcmaar/ui — agent sub-module
 *
 * Consolidated from the former @dcmaar/agent-ui package.
 *
 * @doc.type module
 * @doc.purpose Agent UI components, hooks, and theme utilities for DCMAAR apps
 * @doc.layer product
 * @doc.pattern Facade
 */

// Components
export * from './Button/Button';
export * from './components/ThemeToggle/ThemeToggle';
export * from './components/Card/Card';
export * from './components/Modal/Modal';
export * from './components/QueryPane/QueryPane';

// Theme
export * from './theme/ThemeProvider';
export { useTheme } from './theme/ThemeProvider';

// Utils
export * from './utils/cn';

// Types
export * from './types';

// Hooks
export * from './hooks';
