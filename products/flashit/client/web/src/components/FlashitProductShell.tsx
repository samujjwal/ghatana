import React from 'react';
import { Button } from '@ghatana/design-system';
import {
  ProductShell,
  useStableProductShellConfig,
  type ProductShellConfig,
} from '@ghatana/product-shell';
import { useAtomValue } from 'jotai';
import { useNavigate } from 'react-router-dom';
import { currentUserAtom } from '../store/atoms';
import { useLogout } from '../hooks/use-api';
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
  <div className="rounded-lg bg-slate-900/80 p-4 text-sm text-slate-100">
    <p className="eyebrow">Kernel shell</p>
    <p className="m-0">Route visibility, search entry, and layout behavior come from shared product-shell contracts.</p>
  </div>
);

export function FlashitProductShell({ children }: FlashitProductShellProps): React.ReactElement {
  const currentUser = useAtomValue(currentUserAtom);
  const logout = useLogout();
  const navigate = useNavigate();
  const currentRole = resolveFlashitRole(currentUser);

  const config = useStableProductShellConfig((): ProductShellConfig => ({
    productName: 'FlashIt',
    logo: <span className="text-lg font-semibold tracking-tight text-sky-700">FI</span>,
    currentRole,
    roleOrder: FLASHIT_ROLE_ORDER,
    roleLabels,
    roleDescriptions,
    routes: flashitRouteManifest,
    onSearch: () => navigate('/search'),
    headerActions: (
      <div className="flex items-center gap-3">
        <span className="hidden text-sm text-slate-600 sm:inline">
          {currentUser?.displayName || currentUser?.email || 'Signed in'}
        </span>
        <Button onClick={() => logout()}>Logout</Button>
      </div>
    ),
    sidebarFooter,
  }), [currentRole, currentUser?.displayName, currentUser?.email, logout, navigate]);

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
