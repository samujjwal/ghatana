/**
 * @ghatana/yappc-ui - YAPPC-specific UI components
 * 
 * This package extends the root @ghatana/ui with YAPPC-specific components.
 * Common components are re-exported from @ghatana/ui for convenience.
 */

// ============================================================================
// SHARED LAYOUT COMPONENTS (Consolidated from route duplications)
// ============================================================================
export {
  PageHeader,
  LayoutCard,
  EntityCard,
} from './components/Layout';
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
  LoginFormData,
  RegisterFormData,
  PasswordResetRequestData,
  PasswordResetConfirmData,
  ProtectedRouteProps,
  LoginPageProps,
  RegisterPageProps,
  DashboardPageProps,
} from './components/Auth';

// Toast Notifications
export { ToastProvider, useToast } from './components/Toast';
export type {
  ToastData,
  ToastType,
  ToastPositionType,
  ToastOptions,
  ToastContextValue,
  ToastProviderProps,
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
export { default as ThemeProvider } from './theme/ThemeProvider';
export { theme, lightTheme, darkTheme } from './theme/theme';

// Command Palette
export { default as CommandPalette } from './components/CommandPalette';
export type { Command } from './components/CommandPalette';
export { ShortcutHelper } from './components/Shortcuts/ShortcutHelper';
export type { ShortcutHelperProps } from './components/Shortcuts/ShortcutHelper';
export { Breadcrumb } from './components/Breadcrumb';
export type { BreadcrumbItemType, BreadcrumbProps } from './components/Breadcrumb';

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

export { ResponsiveLayout, ResponsiveGrid, useMediaQuery, useIsMobile, useIsTablet, useIsDesktop } from './patterns/ResponsiveLayout';
export type { ResponsiveLayoutProps, ResponsiveGridProps } from './patterns/ResponsiveLayout';

// Select Components
export { SelectTailwind, SelectOption } from './components/Select/Select.tailwind';
export { Popover, PopoverTrigger, PopoverClose } from './components/Popover';
export type { PopoverProps, PopoverPlacement } from './components/Popover';

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
export { SearchProvider, SearchInterface, SearchResults, useSearch } from './components/Search';
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
  useGlobalStateStatistics
} from './state/useGlobalState';
export { StateManager } from './state/StateManager';
export type { AtomKey, AtomMetadata, StateManagerConfig } from './state/StateManager';

export {
  EnhancedThemeProvider,
  useMultiLayerTheme,
  useThemeMode,
  useBrandTheme,
  useWorkspaceTheme,
  useAppTheme,
  LayerPriority,
} from './theme/EnhancedThemeProvider';
export type {
  ThemeLayer,
  MultiLayerThemeContextValue,
} from './theme/EnhancedThemeProvider';

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
export * from './tokens';

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
// PREVIOUSLY MUI-SOURCED COMPONENTS — now re-exported from @ghatana/ui
// Consumers should import from @ghatana/ui directly. These re-exports
// exist for backward compatibility during the migration period.
// ============================================================================

export {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Autocomplete,
  Backdrop,
  Breadcrumbs,
  ButtonGroup,
  CardMedia,
  Collapse,
  Drawer,
  Fade,
  Grow,
  Icon,
  Link,
  ListItemAvatar,
  ListItemButton,
  ListItemSecondaryAction,
  MenuItem,
  Slide,
  Snackbar,
  SpeedDial,
  SpeedDialAction,
  SpeedDialIcon,
  SwipeableDrawer,
  TextField,
  ToggleButtonGroup,
  Zoom,
} from '@ghatana/ui';
export { useTheme as useMuiTheme } from '@mui/material/styles';

// ============================================================================
// LEGACY MUI TYPES — consumers should remove these
// Provided temporarily for backward compatibility
// ============================================================================

/** @deprecated Use React.CSSProperties or Tailwind classes instead */
export type SxProps<T = unknown> = Record<string, unknown>;
/** @deprecated Use React.CSSProperties or Tailwind classes instead */
export type SystemSxProps = Record<string, unknown>;

// Theme-related types (local replacements)
export type { PaletteMode, ThemeOptions, PaletteOptions } from './theme/types';

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
export { resolveMuiColor, getPaletteMain } from './utils/safePalette';

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

// REMOVED: deprecated @ghatana/yappc-design-tokens
// REMOVED: deprecated @ghatana/yappc-design-tokens
// // // Design tokens (from @ghatana/yappc-design-tokens)
export * from '../../design-tokens/src';

// REMOVED: deprecated @ghatana/yappc-layout
// REMOVED: deprecated @ghatana/yappc-layout
// // // Layout (from @ghatana/yappc-layout)
export * from '../../layout/src';

// REMOVED: deprecated @ghatana/yappc-form-generator
// REMOVED: deprecated @ghatana/yappc-form-generator
// // // Form generator (from @ghatana/yappc-form-generator)
export * from '../../form-generator/src';

// REMOVED: deprecated @ghatana/yappc-accessibility
// REMOVED: deprecated @ghatana/yappc-accessibility
// // // Accessibility (from @ghatana/yappc-accessibility)
export * from '../../accessibility/src';
