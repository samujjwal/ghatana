/**
 * NavBar — operator-cockpit sidebar navigation for AEP.
 *
 * Navigation is now generated from the RouteCapabilityRegistry so that
 * nav items, roles, and lifecycle state stay in sync with canonical routes.
 * Uses shared design-system primitives for styling.
 *
 * Contains:
 *  - AEP logo
 *  - Outcome-grouped navigation sections (from registry)
 *  - TenantSelector (bottom)
 *  - SseStatus indicator (bottom)
 *
 * @doc.type component
 * @doc.purpose AEP operator-cockpit sidebar — capability-based navigation
 * @doc.layer frontend
 */
import React from 'react';
import { NavLink, type NavLinkRenderProps } from 'react-router';
import {
  BarChart3,
  FileText,
  Database,
  Shield,
  Settings,
  type LucideIcon,
} from 'lucide-react';
import { TenantSelector } from './TenantSelector';
import { SseStatus } from './SseStatus';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@ghatana/design-system';
import { useNavigate } from 'react-router';
import {
  getDiscoverableRoutes,
  type RouteCapability,
  type UserRole,
} from '@/lib/routing/RouteCapabilityRegistry';

/**
 * Map registry iconName strings to Lucide components.
 * Extend this map when new icon names are added to the registry.
 */
const iconMap: Record<string, LucideIcon> = {
  BarChart3,
  FileText,
  Database,
  Shield,
  Settings,
};

function navLinkClass({ isActive }: NavLinkRenderProps) {
  return [
    'flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors',
    isActive
      ? 'bg-indigo-100 dark:bg-indigo-900 text-indigo-800 dark:text-indigo-200'
      : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800',
  ].join(' ');
}

function RegistryNavIcon({ iconName }: { iconName?: string }) {
  const Icon = iconName ? iconMap[iconName] : undefined;
  if (!Icon) return null;
  return <Icon className="h-4 w-4 flex-shrink-0" aria-hidden />;
}

function groupRoutesByOutcome(
  routes: RouteCapability[]
): Record<string, RouteCapability[]> {
  const groups: Record<string, RouteCapability[]> = {};
  for (const route of routes) {
    const key = route.group ?? 'Other';
    groups[key] = groups[key] ?? [];
    groups[key].push(route);
  }
  return groups;
}

const groupLabelMap: Record<string, string> = {
  operate: 'Operate',
  build: 'Build',
  learn: 'Learn',
  govern: 'Govern',
  catalog: 'Catalog',
};

/**
 * Sidebar navigation containing capability-based AEP route links,
 * tenant selector, and SSE indicator.
 */
export function NavBar() {
  const navigate = useNavigate();
  const {
    isAuthenticated,
    isBootstrappingSession,
    isVerifyingAuth,
    sessionToken,
    logout,
    roles,
    hasAnyRole,
  } = useAuth();

  // Determine effective role from auth context
  const effectiveRole: UserRole = (() => {
    if (hasAnyRole(['admin'])) return 'admin';
    if (hasAnyRole(['operator'])) return 'operator';
    return 'viewer';
  })();

  const discoverable = getDiscoverableRoutes(effectiveRole);
  const grouped = groupRoutesByOutcome(discoverable);
  const groupKeys = Object.keys(grouped);

  return (
    <nav
      aria-label="AEP navigation"
      className="w-52 flex-shrink-0 border-r border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex flex-col py-4 px-2 overflow-y-auto"
    >
      {/* Logo */}
      <div className="px-3 mb-4 flex items-center gap-2">
        <span className="text-lg font-bold text-indigo-600 dark:text-indigo-400">
          ⚡ AEP
        </span>
      </div>

      {groupKeys.map((groupKey, gi) => (
        <div key={groupKey} className={gi > 0 ? 'mt-3' : undefined}>
          {/* Section header — visual grouping label, not a link */}
          <p className="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-gray-500 dark:text-gray-400 select-none">
            {groupLabelMap[groupKey] ?? groupKey}
          </p>
          {grouped[groupKey].map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/operate'}
              className={navLinkClass}
            >
              <RegistryNavIcon iconName={item.iconName} />
              {item.label}
            </NavLink>
          ))}
        </div>
      ))}

      {/* Bottom section: tenant selector + SSE indicator */}
      <div className="mt-auto flex flex-col gap-1 pb-2 pt-4">
        <div className="mx-3 mb-2 rounded-xl border border-gray-200 bg-gray-50 px-3 py-2 dark:border-gray-800 dark:bg-gray-900">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-500 dark:text-gray-400">
            Access
          </p>
          <p className="mt-1 text-xs text-gray-600 dark:text-gray-300">
            {isVerifyingAuth
              ? 'Verifying access…'
              : isAuthenticated
                ? sessionToken
                  ? 'Session active'
                  : isBootstrappingSession
                    ? 'Starting session…'
                    : 'Token only'
                : 'Signed out'}
          </p>
          <Button
            type="button"
            onClick={() => {
              if (isAuthenticated) {
                logout();
              }
              navigate('/login', { replace: !isAuthenticated });
            }}
            variant="primary"
            className="mt-2 inline-flex w-full items-center justify-center rounded-md px-3 py-2 text-xs font-medium bg-gray-900 text-white hover:bg-gray-700 dark:bg-gray-100 dark:text-gray-950 dark:hover:bg-white"
          >
            {isAuthenticated ? 'Sign out' : 'Sign in'}
          </Button>
        </div>
        <TenantSelector />
        <SseStatus />
      </div>
    </nav>
  );
}
