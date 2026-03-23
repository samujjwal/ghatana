/**
 * @ghatana/yappc-ui - YAPPC-specific UI components
 *
 * This package extends the root @ghatana/ui with YAPPC-specific components.
 * Common components are re-exported from @ghatana/ui for convenience.
 */

// ============================================================================
// MUI COMPONENTS (Re-exported for convenience)
// ============================================================================
export {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  ListItemIcon,
  Chip,
  Typography,
  Divider,
  TextField,
  Button,
  IconButton,
  Menu,
  MenuItem,
  Avatar,
  Tooltip,
  Badge,
  Paper,
  Card,
  CardContent,
  Tabs,
  Tab,
  AppBar,
  Toolbar,
  Drawer,
  Snackbar,
  Alert,
  CircularProgress,
  LinearProgress,
  Select,
  Checkbox,
  Radio,
  Switch,
  FormControl,
  FormControlLabel,
  InputLabel,
  OutlinedInput,
  InputAdornment,
  Autocomplete,
  Stack,
  Grid,
  Container,
  Collapse,
  Breadcrumbs,
  Link,
  Stepper,
  Step,
  StepLabel,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Pagination,
  Slider,
  Modal,
  Fade,
  Grow,
  Backdrop,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
  ToggleButton,
  ToggleButtonGroup,
  ClickAwayListener,
  // Custom alias for InteractiveList (combination of List + interactive props)
  List as InteractiveList,
} from '@mui/material';

// ============================================================================
// SHARED LAYOUT COMPONENTS (Consolidated from route duplications)
// ============================================================================
export { PageHeader, LayoutCard, EntityCard } from './components/Layout';
export type {
  PageHeaderProps,
  LayoutCardProps,
  EntityCardProps,
} from './components/Layout';

// ============================================================================
// SHARED UTILITIES (Consolidated from lib duplications)
// ============================================================================
export {
  hexToRgb,
  rgbToHex,
  parseVersion,
  compareVersions,
  isValidVersion,
  formatNumber,
  truncateText,
  generateId,
  deepClone,
  debounce,
  throttle,
} from './utils/shared';
export type { VersionComponents } from './utils/shared';

// ============================================================================
// YAPPC-SPECIFIC COMPONENTS (Local)
// ============================================================================

// Error Boundary & Error Handling
export * from './error';

// Authentication Components
export {
  LoginForm,
  RegisterForm,
  PasswordResetRequest,
  PasswordResetConfirm,
  ProtectedRoute,
  withProtectedRoute,
  useRouteAccess,
  AuthExamples,
  LoginPage,
  RegisterPage,
  DashboardPage,
} from './components/Auth';
export type {
  LoginFormProps,
  RegisterFormProps,
  PasswordResetRequestProps,
  PasswordResetConfirmProps,
  // Note: PasswordResetRequestData and PasswordResetConfirmData are not exported from Auth
  // Types below are re-exported from zodValidation - see below
  // LoginFormData,
  // RegisterFormData,
  ProtectedRouteProps,
  LoginPageProps,
  RegisterPageProps,
  DashboardPageProps,
} from './components/Auth';

// Toast Notifications
export { ToastProvider, useToast } from './components/Toast';
export type {
  ToastData,
  ToastSeverity as ToastType,
  ToastPosition as ToastPositionType,
  ToastProps as ToastOptions,
  // Note: ToastContextValue and ToastProviderProps are not exported from Toast.tailwind
} from './components/Toast';

// Loading Components
export {
  Spinner,
  InlineSpinner,
  LoadingButton,
  Skeleton,
  SkeletonCard,
  SkeletonTable,
  SkeletonList,
} from './components/Loading';
export type {
  SpinnerProps,
  SpinnerSize,
  SpinnerVariant,
  InlineSpinnerProps,
  LoadingButtonProps,
  SkeletonProps,
  SkeletonVariant,
  SkeletonAnimation,
  SkeletonCardProps,
  SkeletonTableProps,
  SkeletonListProps,
} from './components/Loading';

// Form Validation (Zod-based)
export {
  emailSchema,
  passwordSchema,
  simplePasswordSchema,
  nameSchema,
  usernameSchema,
  phoneSchema,
  urlSchema,
  dateSchema,
  loginSchema,
  registerSchema,
  passwordResetRequestSchema,
  passwordResetConfirmSchema,
  changePasswordSchema,
  updateProfileSchema,
  validate,
  getFieldError,
  hasFieldError,
  getAllErrors,
  calculatePasswordStrength,
  isValidEmail,
  formatPhoneNumber,
  sanitizeString,
} from './utils/zodValidation';
export type {
  LoginFormData,
  RegisterFormData,
  PasswordResetRequestFormData,
  PasswordResetConfirmFormData,
  ChangePasswordFormData,
  UpdateProfileFormData,
} from './utils/zodValidation';

// DevSecOps components (YAPPC-specific)
export * as DevSecOps from './components/DevSecOps';

// YAPPC-specific theme extensions
export { ThemeProvider, ThemeContext, useThemeContext } from '@yappc/theme';
export { ThemeProvider as defaultThemeProvider } from '@yappc/theme';
export { theme, lightTheme, darkTheme } from '@yappc/theme';
export type { ThemeContextType, ThemeMode, ThemeProviderProps } from '@yappc/theme';

// Command Palette
export { CommandPalette, useCommandPalette, ShortcutHelper, useKeyboardShortcuts, shortcutRegistry } from '@yappc/shortcuts';
export { CommandPalette as defaultCommandPalette } from '@yappc/shortcuts';
export type { Command, CommandPaletteProps, ShortcutHelperProps, KeyboardShortcut, ShortcutContext, UseKeyboardShortcutsOptions, UseKeyboardShortcutsReturn } from '@yappc/shortcuts';
export { Breadcrumb, TabNavigation, StageNavigation, stageNavigationStyles } from '@yappc/navigation-ui';
export type {
  BreadcrumbItemType,
  BreadcrumbProps,
  TabNavigationItem,
  TabNavigationProps,
  StageNavigationProps,
} from '@yappc/navigation-ui';

// Error Boundary
export { ErrorBoundary } from './components/ErrorBoundary';
export type { ErrorBoundaryProps } from './components/ErrorBoundary';

// Loading States
export { LoadingState } from './components/LoadingState';
export type { LoadingStateProps } from './components/LoadingState';

// Empty States
export { EmptyState } from './components/EmptyState';
export type { EmptyStateProps } from './components/EmptyState';

// Patterns
export { PageIntegrationExample } from './patterns/PageIntegrationExample';
export type { PageIntegrationExampleProps } from './patterns/PageIntegrationExample';

export {
  ResponsiveLayout,
  ResponsiveGrid,
  useMediaQuery,
  useIsMobile,
  useIsTablet,
  useIsDesktop,
} from './patterns/ResponsiveLayout';
export type {
  ResponsiveLayoutProps,
  ResponsiveGridProps,
} from './patterns/ResponsiveLayout';

// Select Components
export {
  SelectTailwind,
  SelectOption,
  SelectGroup,
} from '@yappc/base-ui';
export type { SelectProps as SelectTailwindProps, SelectOptionProps, SelectGroupProps } from '@yappc/base-ui';
export { Popover, PopoverTrigger, PopoverClose } from '@yappc/base-ui';
export type { PopoverProps, PopoverPlacement } from '@yappc/base-ui';
export { StoryCard, VelocityChart, BurndownChart } from '@yappc/development-ui';
export type {
  Story,
  StoryType,
  StoryStatus,
  StoryPriority,
  StoryAssignee,
  StoryTask,
  StoryPullRequest,
  StoryCardProps,
  SprintVelocityData,
  VelocityChartProps,
  BurndownDataPoint,
  BurndownChartProps,
} from '@yappc/development-ui';
export { PresetCard, ResourcesList } from '@yappc/initialization-ui';
export type {
  PresetCategory,
  TechStack,
  ResourceEstimate,
  InitializationPreset,
  PresetCardProps,
  ResourceType,
  ResourceStatus,
  ResourceProvider,
  ResourceConfig,
  ResourceCost,
  Resource,
  ResourceAction,
  ResourcesListProps,
} from '@yappc/initialization-ui';

// Action Components
export { BulkActionBar } from './components/Actions/BulkActionBar';
export type {
  BulkAction,
  BulkOperationProgress,
  BulkActionBarProps,
} from './components/Actions/BulkActionBar';

// Table Components
export { SelectableTable } from './components/Table/SelectableTable';
export type {
  TableColumn,
  SelectableTableProps,
} from './components/Table/SelectableTable';

// Search Components
export {
  SearchProvider,
  SearchInterface,
  SearchResults,
  useSearch,
} from './components/Search';
export type {
  SearchResult,
  SearchContextValue,
  SearchProviderProps,
  SearchInterfaceProps,
  SearchResultsProps,
} from './components/Search';

// State management
export {
  useGlobalStateValue,
  useSetGlobalState,
  useResetGlobalState,
  useToggleGlobalState,
  useCounterGlobalState,
  useArrayGlobalState,
  useObjectGlobalState,
  useBatchGlobalStateUpdate,
  useGlobalStateKeys,
  useGlobalStateStatistics,
} from '@yappc/state';
export { StateManager } from '@yappc/state';
export type {
  AtomKey,
  AtomMetadata,
  StateManagerConfig,
} from '@yappc/state';
export {
  configQueryKeys,
  usePersonas,
  usePersona,
  useDomains,
  useDomain,
  useTemplates,
  useTemplate,
  useWorkflows,
  useWorkflow,
  useTasks,
  useTask,
} from '@yappc/config-hooks';
export type {
  PersonaConfig,
  TaskDomain,
  TaskTemplate,
  TaskData,
  WorkflowConfig,
} from '@yappc/config-hooks';

export {
  EnhancedThemeProvider,
  useMultiLayerTheme,
  useThemeMode,
  useBrandTheme,
  useWorkspaceTheme,
  useAppTheme,
  LayerPriority,
} from '@yappc/theme';
export type {
  ThemeLayer,
  MultiLayerThemeContextValue,
} from '@yappc/theme';

// YAPPC-specific utilities
export { PlatformWrapper } from './utils/PlatformWrapper';
export { detectPlatform, isDesktop, isMobile, isWeb } from './utils/platform';
export type { Platform } from './utils/platform';
export {
  useResponsive,
  usePlatformResponsive,
  getPlatformStyles,
  getResponsiveStyles,
} from './utils/responsive';

// YAPPC design tokens (extends root tokens)
export * from '@yappc/theme';

// YAPPC-specific hooks
export * from './hooks';
export type { SelectionItem } from './hooks/useSelection';

// YAPPC-specific interactions
export {
  useDialog,
  showAlert,
  showConfirm,
  showDeleteConfirm,
  useTooltip,
  useTooltipState,
  useTooltipGroup,
  useDelayedTooltip,
  useModal,
  useModalStack,
  useModalRegistration,
  useModalKeyboard,
  useModalZIndex,
  ModalManager,
} from './interactions';

// YAPPC-specific utils
export * from './utils';
export { cn } from './utils/cn';

// ============================================================================
// PREVIOUSLY MUI-SOURCED COMPONENTS — now re-exported from @mui/material at top of file
// Consumers should import from @ghatana/yappc-ui directly.
// ============================================================================
export { useTheme as useMuiTheme } from '@yappc/theme';

// ============================================================================
// LEGACY MUI TYPES — consumers should remove these
// Provided temporarily for backward compatibility
// ============================================================================

/** @deprecated Use React.CSSProperties or Tailwind classes instead */
export type SxProps<T = unknown> = Record<string, unknown>;
/** @deprecated Use React.CSSProperties or Tailwind classes instead */
export type SystemSxProps = Record<string, unknown>;

// Theme-related types (local replacements)
export type { PaletteMode, ThemeOptions, PaletteOptions } from '@yappc/theme';

/**
 * @deprecated — Color alpha utility. Use Tailwind opacity utilities instead.
 * e.g. bg-blue-500/50 for 50% opacity
 */
export function alpha(color: string, opacity: number): string {
  // Simple hex/rgb alpha implementation
  if (color.startsWith('#')) {
    const hex = color.slice(1);
    const r = parseInt(hex.slice(0, 2), 16);
    const g = parseInt(hex.slice(2, 4), 16);
    const b = parseInt(hex.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${opacity})`;
  }
  if (color.startsWith('rgb(')) {
    return color.replace('rgb(', 'rgba(').replace(')', `, ${opacity})`);
  }
  return color;
}

// ============================================================================
// UTILITY FUNCTIONS (YAPPC-specific)
// ============================================================================

// Safe palette color resolution utilities
export { resolveMuiColor, getPaletteMain } from '@yappc/theme';

// ============================================================================
// CONFIGURATION SYSTEM
// ============================================================================

// Configuration hooks, components, and state management
export * from './config';

// ============================================================================
// CONSOLIDATED LIBRARIES
// The following are re-exported from their source packages for convenience.
// Direct imports from the source packages still work but are deprecated.
// ============================================================================

// REMOVED: deprecated @ghatana/yappc-design-tokens - package doesn't exist
// export * from '../../design-tokens/src';

// REMOVED: deprecated @ghatana/yappc-layout - package doesn't exist
// export * from '../../layout/src';

// REMOVED: deprecated @ghatana/yappc-form-generator - package doesn't exist
// export * from '../../form-generator/src';

// REMOVED: deprecated @ghatana/yappc-accessibility - package doesn't exist
// export * from '../../accessibility/src';

// ============================================================================
// DEPRECATION WARNING
// ============================================================================
// eslint-disable-next-line no-console
console.warn(
  '[DEPRECATED] @ghatana/yappc-ui is deprecated. Use @yappc/ui instead. ' +
    'See: docs/NAMING_CONVENTIONS.md'
);
