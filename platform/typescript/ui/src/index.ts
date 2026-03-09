/**
 * @ghatana/ui
 *
 * Cross-product React component library for the Ghatana platform.
 * Components are built on top of the global design tokens and theme runtime.
 *
 * @package @ghatana/ui
 */

// Atoms
export * from './atoms/Button';
export * from './atoms/IconButton';
export * from './atoms/Input';
export * from './atoms/InputAdornment';
export * from './atoms/TextArea';
export * from './atoms/Badge';
export * from './atoms/Spinner';
export * from './atoms/VisuallyHidden';
export * from './atoms/Checkbox';
export * from './atoms/Radio';
export * from './atoms/Switch';
export * from './atoms/Skeleton';
export * from './atoms/Tooltip';
export * from './atoms/Chip';
export * from './atoms/Select';
export * from './atoms/FormControl';
export * from './atoms/FormControlLabel';
export * from './atoms/InputLabel';
export * from './atoms/Slider';
export * from './atoms/DatePicker';
export * from './atoms/ToggleButton';
export * from './atoms/BottomNavigation';
export * from './atoms/Fab';
export * from './atoms/Collapse';
export * from './atoms/Backdrop';
export * from './atoms/Transitions';
export * from './atoms/ButtonGroup';
export * from './atoms/Icon';

// Command Palette
export { CommandPalette } from './components/CommandPalette';
export type { CommandItem, CommandPaletteProps } from './components/CommandPalette';

// Mobile Shell
export { MobileShell } from './components/MobileShell';
export type { BottomNavItem, FABConfig, MobileShellProps } from './components/MobileShell';

// Collaboration
export * from './collaboration';
export * from './atoms/FileUpload';
export * from './atoms/Avatar';
export * from './atoms/Divider';
export * from './atoms/Progress';
export * from './atoms/LinearProgress';
export * from './atoms/Rating';

// Molecules
export * from './molecules/FormField';
export * from './molecules/Alert';
export * from './molecules/Card';
export * from './molecules/Modal';
export * from './molecules/Dialog';
export * from './molecules/Table';
export * from './molecules/Tabs';
export * from './molecules/Popper';
export * from './molecules/Breadcrumb';
export * from './molecules/Breadcrumbs';
export * from './molecules/Pagination';
export * from './molecules/Toast';
export * from './molecules/Menu';
export * from './molecules/RadioGroup';
export * from './molecules/Stepper';
export * from './molecules/List'; // Exports InteractiveList (renamed to avoid conflict with typography List)
export * from './molecules/AvatarGroup';
export * from './molecules/Timeline';
export * from './molecules/AppBar';
export * from './molecules/ConfirmDialog';
export * from './molecules/ActionSheet';
export * from './molecules/TreeView';
export * from './molecules/Form';
export * from './molecules/NavLink';
export * from './molecules/Sidebar';
export * from './molecules/Drawer';
export * from './molecules/Toolbar';
export * from './molecules/Snackbar';
export * from './molecules/SpeedDial';
export * from './molecules/Autocomplete';
export * from './molecules/CommandPalette';
export * from './molecules/DateRangePicker';
export * from './molecules/AppListItem';
export * from './molecules/UsageStatsCard';
export * from './molecules/PolicyCard';
export * from './molecules/PermissionBanner';

// Organisms
export * from './organisms/DashboardLayout';
export * from './organisms/AppHeader';
export * from './organisms/AppSidebar';
export * from './organisms/ErrorBoundary';
export * from './organisms/ProtectedRoute';
export * from './organisms/DynamicForm';
export * from './organisms/ActivityFeed';
export * from './organisms/DataGrid';
export * from './organisms/StatsDashboard';
export * from './organisms/AppListContainer';

// Data Components (reusable data displays)
export * from './components/data/TreeTable';
export * from './components/data/SplitPane';

// Accessibility Components
export * from './components/LiveRegion';

// Layout
export * from './layout/Box';
export * from './layout/Stack';
export * from './layout/Container';
export * from './layout/Grid';
export * from './layout/Surface';
export * from './layout/Spacer';

// Hooks
export * from './hooks';
export * from './hooks/useTheme';

// Utilities
export { cn } from '@ghatana/utils';
export * from './utils/rtl';
export * from './utils/accessibility';
export * from './utils/testing';
export * from './utils/a11yTesting';
export * from './utils/colorContrast';

// Tokens
export * from './tokens/animations';
export * from './tokens/semanticColors';

// Integration (commented out until tokens module is available)
// export * from './integration/pageBuilder';
// export * from './integration/aiFeatures';
// export * from './integration/collaboration';

// Storybook (commented out - not under rootDir)
// export * from '../.storybook/main';
// export * from '../.storybook/preview';
// export * from './atoms/Button.stories';

// Theme
export * from './theme/darkMode';
// export * from './theme/useDarkMode'; // TODO: Fix when available
export * from './typography';

// Tailwind Theme Styles (reusable CSS class definitions)
export * from './styles/tailwindTheme';

// Compatibility re-exports for consumers still depending on MUI primitives.
export {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  AlertTitle,
  FormGroup,
  ListItemIcon,
  ListItemText,
  TablePagination,
  TableSortLabel,
} from '@mui/material';

// ─── Migrated from @ghatana/design-system ────────────────────────────────

// Molecules (from design-system)
export * from './molecules/Tour/Tour';
export * from './molecules/PageTransition/PageTransition';
export * from './molecules/ResponsiveTable/ResponsiveTable';
export * from './molecules/FormStepper/FormStepper';
export * from './molecules/HoverCard/HoverCard';
export * from './molecules/SwipeableCard/SwipeableCard';
export * from './molecules/PullToRefresh/PullToRefresh';
export * from './molecules/ContextIndicator/ContextIndicator';
export * from './molecules/DataTable';
export * from './molecules/SwipeableDrawer';

// Atoms (from design-system)
export * from './atoms/ResponsiveImage/ResponsiveImage';
export * from './atoms/FocusTrap/FocusTrap';

// Hooks (from design-system)
export * from './hooks/useKeyboardNavigation';
export * from './hooks/useReducedMotion';
export * from './hooks/useSwipeGesture';
export * from './hooks/useFormValidation';
export * from './hooks/useImageOptimization';
export * from './hooks/useOptimisticUpdate';
export * from './hooks/useAccessibleId';
export * from './hooks/useMediaQuery';

// Utils (from design-system)
export * from './utils/colorContrast';
export * from './utils/AccessibilityAuditService';
export * from './utils/DesignPromptService';

// Tokens (from design-system)
export * from './tokens/animations';
export * from './tokens/semanticColors';

// Theme helpers (surface theme exports for consumers)
// TODO: Fix when @ghatana/theme is available
// export {
//   ThemeProvider,
//   useTheme,
//   useThemeMode,
//   useThemeToggle,
//   useResolvedTheme,
//   useSystemTheme,
// } from '@ghatana/theme';
// TODO: Fix when @ghatana/theme is available
// export {
//   useIsDarkMode,
//   useIsLightMode,
//   useThemeDefinition,
//   useThemeTokens,
//   useThemeLayers,
// } from '@ghatana/theme';

// export type {
//   Theme,
//   ThemeLayer,
//   ThemeLayerType,
//   ThemeMode,
//   ResolvedTheme,
//   ThemeComputed,
// } from '@ghatana/theme';
