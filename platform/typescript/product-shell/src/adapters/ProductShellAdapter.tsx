/**
 * ProductShellAdapter - composable shell adapter for product-specific ProductShell configuration
 * 
 * Provides a reusable adapter pattern for configuring ProductShell with product-specific
 * role labels, descriptions, and configuration. Products can use this adapter instead of
 * creating their own wrapper components, reducing duplication.
 * 
 * @doc.type component
 * @doc.purpose Composable adapter for ProductShell configuration
 * @doc.layer platform
 * @doc.pattern Adapter
 */
import React from 'react';
import { ProductShell, useStableProductShellConfig, type ProductShellConfig } from '../index';

interface ProductShellAdapterProps {
  /** Product name */
  productName: string;
  /** Current role of the user */
  currentRole: string;
  /** Role order for role hierarchy */
  roleOrder: string[];
  /** Role labels for display */
  roleLabels: ProductShellConfig['roleLabels'];
  /** Role descriptions for tooltips/help */
  roleDescriptions: ProductShellConfig['roleDescriptions'];
  /** Route manifest for navigation */
  routeManifest: ProductShellConfig['routeManifest'];
  /** Optional custom logo */
  logo?: React.ReactNode;
  /** Optional logout handler */
  onLogout?: () => void;
  /** Optional navigate handler */
  onNavigate?: (path: string) => void;
  /** Optional search handler */
  onSearch?: (query: string) => void;
  /** Optional notification handler */
  onNotificationClick?: () => void;
  /** Optional active operations count */
  activeOperationsCount?: number;
  /** Optional active operations click handler */
  onActiveOperationsClick?: () => void;
  /** Product-owned route/content slot */
  children: React.ReactNode;
  /** Additional CSS class applied to the main content area wrapper */
  contentClassName?: string;
  /** Optional id applied to the main content element for skip-link support */
  mainContentId?: string;
  /** Optional tab index applied to the main content element */
  mainContentTabIndex?: number;
  /** Optional ARIA role override for the main content element */
  mainContentRole?: React.AriaRole;
}

/**
 * Composable ProductShell adapter that reduces configuration boilerplate.
 * 
 * @example
 * ```tsx
 * <ProductShellAdapter
 *   productName="My Product"
 *   currentRole={role}
 *   roleOrder={ROLE_ORDER}
 *   roleLabels={roleLabels}
 *   roleDescriptions={roleDescriptions}
 *   routeManifest={routeManifest}
 *   onLogout={logout}
 *   onNavigate={navigate}
 * >
 *   {children}
 * </ProductShellAdapter>
 * ```
 */
export function ProductShellAdapter({
  productName,
  currentRole,
  roleOrder,
  roleLabels,
  roleDescriptions,
  routeManifest,
  logo,
  onLogout,
  onNavigate,
  onSearch,
  onNotificationClick,
  activeOperationsCount,
  onActiveOperationsClick,
  children,
  contentClassName,
  mainContentId,
  mainContentTabIndex,
  mainContentRole,
}: ProductShellAdapterProps): React.ReactElement {
  const config = useStableProductShellConfig((): ProductShellConfig => ({
    productName,
    logo,
    currentRole,
    roleOrder,
    roleLabels,
    roleDescriptions,
    routeManifest,
    onLogout,
    onNavigate,
    onSearch,
    onNotificationClick,
    activeOperationsCount,
    onActiveOperationsClick,
  }), [
    productName,
    logo,
    currentRole,
    roleOrder,
    roleLabels,
    roleDescriptions,
    routeManifest,
    onLogout,
    onNavigate,
    onSearch,
    onNotificationClick,
    activeOperationsCount,
    onActiveOperationsClick,
  ]);

  return (
    <ProductShell
      config={config}
      contentClassName={contentClassName}
      mainContentId={mainContentId}
      mainContentTabIndex={mainContentTabIndex}
      mainContentRole={mainContentRole}
    >
      {children}
    </ProductShell>
  );
}
