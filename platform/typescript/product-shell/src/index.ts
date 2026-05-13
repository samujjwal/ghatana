/**
 * @ghatana/product-shell — public API
 *
 * Shared product shell layout primitives for Ghatana products.
 * See README.md for usage and migration guide.
 *
 * @doc.type module
 * @doc.purpose Public API for @ghatana/product-shell
 * @doc.layer platform
 */

// Core types
export type {
  ProductEntitledAction,
  ProductEntitledCard,
  ProductRouteCapability,
  ProductRouteEntitlement,
  ProductShellConfig,
  ProductNotification,
  UnsupportedSurfaceConfig,
  RouteLifecycle,
} from './types';

// Root shell
export {
  ProductShell,
  ProductShellLayout,
  useProductShellState,
} from './components/ProductShell';
export type {
  ProductShellProps,
  ProductShellLayoutProps,
  ProductShellState,
} from './components/ProductShell';
export { PageHeader } from './components/PageHeader';
export {
  PageContent,
  PageSection,
  ContextPanel,
  SuggestionCard,
} from './components/PageLayout';

export type {
  PageContentProps,
  PageSectionProps,
  ContextPanelProps,
  SuggestionCardProps,
} from './components/PageLayout';

// Sub-components (composable individually)
export { CapabilitySidebar } from './components/CapabilitySidebar';
export { ProductHeader } from './components/ProductHeader';
export { RouteCapabilityNav } from './components/RouteCapabilityNav';
export { ProductViewModeSelector } from './components/ProductViewModeSelector';
export { NotificationCenter } from './components/NotificationCenter';
export { ActiveOperationsBar } from './components/ActiveOperationsBar';
export { UnsupportedSurfaceBoundary } from './components/UnsupportedSurfaceBoundary';
export {
  ProductHeaderUserMenu,
  ProductShellFooter,
  createProductRoleSelectorConfig,
} from './components/ProductShellChrome';
export type {
  ProductHeaderUserMenuProps,
  ProductRoleSelectorConfig,
  ProductShellFooterProps,
} from './components/ProductShellChrome';
export {
  createRoleHierarchy,
  createRouteAccessEvaluator,
  filterDiscoverableRoutes,
  hasMinimumRole,
  hydrateRoutesFromEntitlement,
  isRouteAllowed,
  resolveHighestRole,
} from './access';
export type {
  RoleHierarchy,
  RouteAccessEvaluator,
} from './access';
export {
  createProductShellConfig,
  useProductShellConfig,
} from './shellConfig';
export {
  useProductEntitlements,
  type ProductEntitlementStatus,
  type UseProductEntitlementsOptions,
  type UseProductEntitlementsResult,
} from './useProductEntitlements';

// Composable adapters
export { ProductShellAdapter } from './adapters/ProductShellAdapter';
