import React from 'react';
import { ProductShell, type ProductShellConfig } from '@ghatana/product-shell';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import {
  DMOS_ROLE_ORDER,
  dmosRouteManifest,
  getHighestDmosRole,
  resolveDmosRoutePath,
} from '@/routeManifest';

interface DmosProductShellProps {
  children: React.ReactNode;
}

export function DmosProductShell({ children }: DmosProductShellProps): React.ReactElement {
  const navigate = useNavigate();
  const { workspaceId: routeWorkspaceId } = useParams<{ workspaceId: string }>();
  const { roles, workspaceId: authWorkspaceId, logout } = useAuth();

  const workspaceId = routeWorkspaceId ?? authWorkspaceId ?? 'workspace';
  const currentRole = getHighestDmosRole(roles);

  const config: ProductShellConfig = {
    productName: 'DMOS',
    logo: <span className="text-lg font-semibold tracking-tight text-sky-700">DM</span>,
    currentRole,
    roleOrder: DMOS_ROLE_ORDER,
    roleLabels: {
      viewer: 'Viewer',
      'brand-manager': 'Brand Manager',
      'marketing-director': 'Marketing Director',
      'exec-sponsor': 'Executive Sponsor',
      admin: 'Admin',
    },
    roleDescriptions: {
      viewer: 'Read-only workspace oversight.',
      'brand-manager': 'Campaign planning and execution workflows.',
      'marketing-director': 'Budget and approval orchestration.',
      'exec-sponsor': 'Executive review and strategic governance.',
      admin: 'Operational administration and platform support.',
    },
    routes: dmosRouteManifest
      .filter((route) => route.lifecycle !== 'boundary' || route.discoverable === true)
      .map((route) => ({
        ...route,
        path: resolveDmosRoutePath(route.path, workspaceId),
      })),
    headerActions: (
      <div className="flex items-center gap-3">
        <span className="hidden text-sm text-slate-600 sm:inline">
          Workspace {workspaceId}
        </span>
        <button
          type="button"
          onClick={logout}
          className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
        >
          Logout
        </button>
      </div>
    ),
    sidebarFooter: (
      <div className="rounded-2xl bg-slate-900/80 p-4 text-sm text-slate-100">
        <p className="eyebrow">Kernel shell</p>
        <p className="m-0">Workspace navigation, role visibility, and route disclosure come from the shared product-shell contract.</p>
      </div>
    ),
    onSearch: () => navigate(resolveDmosRoutePath('/workspaces/:workspaceId/dashboard', workspaceId)),
  };

  return (
    <ProductShell
      config={config}
      contentClassName="pt-20 p-6"
      mainContentId="main-content"
      mainContentTabIndex={-1}
      mainContentRole="main"
    >
      {children}
    </ProductShell>
  );
}
