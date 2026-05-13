import React from 'react';
import {
  ProductHeaderUserMenu,
  ProductShell,
  ProductShellFooter,
  createProductRoleSelectorConfig,
  useProductEntitlements,
  useProductShellConfig,
  type ProductShellConfig,
} from '@ghatana/product-shell';
import { useAtomValue } from 'jotai';
import { useNavigate } from 'react-router-dom';
import { authTokenAtom, currentUserAtom } from '../store/atoms';
import { useLogout } from '../hooks/use-api';
import { API_BASE_URL } from '../lib/api-client';
import { flashitRouteManifest } from '../routeManifest';
import { FLASHIT_ROLE_ORDER, resolveFlashitRole } from '../routeAccess';

interface FlashitProductShellProps {
  children: React.ReactNode;
}

const roleLabels: ProductShellConfig['roleLabels'] = {
  guest: 'Guest',
  member: 'Member',
  premium: 'Premium',
  admin: 'Admin',
};

const roleDescriptions: ProductShellConfig['roleDescriptions'] = {
  guest: 'Unauthenticated preview state.',
  member: 'Core capture and reflection workspace.',
  premium: 'Expanded insight and collaboration workspace.',
  admin: 'Operational governance and support workspace.',
};

const sidebarFooter = (
  <ProductShellFooter description="Route visibility, search entry, and layout behavior come from shared product-shell contracts." />
);

const roleSelectorConfig = createProductRoleSelectorConfig({
  roleOrder: FLASHIT_ROLE_ORDER,
  roleLabels,
  roleDescriptions,
});

export function FlashitProductShell({ children }: FlashitProductShellProps): React.ReactElement {
  const currentUser = useAtomValue(currentUserAtom);
  const authToken = useAtomValue(authTokenAtom);
  const logout = useLogout();
  const navigate = useNavigate();
  const currentRole = resolveFlashitRole(currentUser);
  const entitlementEndpoint = React.useMemo(() => {
    const params = new URLSearchParams({
      role: currentRole,
      principalId: currentUser?.id ?? 'anonymous',
    });
    return `${API_BASE_URL}/api/entitlements/route-entitlements?${params.toString()}`;
  }, [currentRole, currentUser?.id]);
  const entitlementRequestInit = React.useMemo<RequestInit>(
    () => ({
      headers: {
        Accept: 'application/json',
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      },
      credentials: 'include',
    }),
    [authToken],
  );
  const entitlements = useProductEntitlements({
    endpoint: entitlementEndpoint,
    fallbackRoutes: flashitRouteManifest,
    requestInit: entitlementRequestInit,
  });

  const config = useProductShellConfig({
    productName: 'FlashIt',
    logo: <span className="text-lg font-semibold tracking-tight text-sky-700">FI</span>,
    currentRole,
    ...roleSelectorConfig,
    routes: entitlements.routes,
    onSearch: () => navigate('/search'),
    headerActions: (
      <ProductHeaderUserMenu
        displayName={currentUser?.displayName}
        email={currentUser?.email}
        onLogout={() => logout()}
      />
    ),
    sidebarFooter,
  });

  return (
    <ProductShell
      config={config}
      contentClassName="pt-20 px-4 py-8 sm:px-6 lg:px-8"
      mainContentId="main-content"
      mainContentTabIndex={-1}
      mainContentRole="main"
    >
      {children}
    </ProductShell>
  );
}
