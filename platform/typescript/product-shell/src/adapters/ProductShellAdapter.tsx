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
import { ProductShell, useProductShellConfig, type ProductShellConfig, type ProductShellProps } from '../index';

type AdapterRoleOrder = readonly string[] | ProductShellConfig['roleOrder'];

function toRoleOrder(roleOrder: AdapterRoleOrder): ProductShellConfig['roleOrder'] {
  if (Array.isArray(roleOrder)) {
    return Object.fromEntries(roleOrder.map((role, index) => [role, index]));
  }

  return roleOrder as ProductShellConfig['roleOrder'];
}

interface ProductShellAdapterProps {
  /** Product name */
  productName: string;
  /** Current role of the user */
  currentRole: string;
  /** Role order for role hierarchy */
  roleOrder: AdapterRoleOrder;
  /** Role labels for display */
  roleLabels: ProductShellConfig['roleLabels'];
  /** Role descriptions for tooltips/help */
  roleDescriptions: ProductShellConfig['roleDescriptions'];
  /** Route manifest for navigation */
  routeManifest: ProductShellConfig['routes'];
  /** Optional custom logo */
  logo?: React.ReactNode;
  /** Optional logout handler */
  onLogout?: () => void;
  /** Optional navigate handler */
  onNavigate?: (path: string) => void;
  /** Optional search handler */
  onSearch?: () => void;
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
  const roleHierarchy = toRoleOrder(roleOrder);
  const shellConfig: ProductShellConfig = {
    productName,
    ...(logo !== undefined ? { logo } : {}),
    currentRole,
    roleOrder: roleHierarchy,
    routes: routeManifest,
    ...(roleLabels !== undefined ? { roleLabels } : {}),
    ...(roleDescriptions !== undefined ? { roleDescriptions } : {}),
    ...(onSearch !== undefined ? { onSearch } : {}),
    ...(activeOperationsCount !== undefined ? { activeOperationsCount } : {}),
    ...(onActiveOperationsClick !== undefined ? { onActiveOperationsClick } : {}),
  };
  const config = useProductShellConfig(shellConfig);
  const shellProps: Omit<ProductShellProps, 'children' | 'config'> = {};

  if (contentClassName !== undefined) {
    shellProps.contentClassName = contentClassName;
  }
  if (mainContentId !== undefined) {
    shellProps.mainContentId = mainContentId;
  }
  if (mainContentTabIndex !== undefined) {
    shellProps.mainContentTabIndex = mainContentTabIndex;
  }
  if (mainContentRole !== undefined) {
    shellProps.mainContentRole = mainContentRole;
  }

  void onLogout;
  void onNavigate;
  void onNotificationClick;

  return (
    <ProductShell
      config={config}
      {...shellProps}
    >
      {children}
    </ProductShell>
  );
}
