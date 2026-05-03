/**
 * RouteCapabilityNav — navigation links generated from route capability registry.
 *
 * Renders grouped navigation links from the list of `ProductRouteCapability`
 * entries that pass the current role filter. Routes with `lifecycle: 'boundary'`
 * are excluded. Routes with `discoverable: false` are excluded.
 *
 * Navigation state (active, hover, focus) is handled via react-router's
 * `NavLink` with its built-in `isActive` detection.
 *
 * @doc.type component
 * @doc.purpose Registry-driven navigation links for the product shell
 * @doc.layer platform
 * @doc.pattern Organism
 */
import React from 'react';
import { NavLink } from 'react-router';
import type { ProductRouteCapability, ProductShellConfig } from '../types';

interface RouteCapabilityNavProps {
  routes: readonly ProductRouteCapability[];
  config: Pick<ProductShellConfig, 'currentRole' | 'roleOrder'>;
  /** When true, renders icon only (collapsed sidebar). */
  collapsed?: boolean;
  /** Additional class applied to each group heading. */
  groupHeadingClassName?: string;
  /** Additional class applied to each nav link. */
  navLinkClassName?: string | ((props: { isActive: boolean }) => string);
}

function roleAtLeast(
  currentRole: string,
  minimumRole: string | undefined,
  roleOrder: Readonly<Record<string, number>>
): boolean {
  if (!minimumRole) return true;
  const current = roleOrder[currentRole] ?? 0;
  const minimum = roleOrder[minimumRole] ?? 0;
  return current >= minimum;
}

function groupRoutes(routes: readonly ProductRouteCapability[]): Map<string, ProductRouteCapability[]> {
  const groups = new Map<string, ProductRouteCapability[]>();
  for (const route of routes) {
    const group = route.group ?? '';
    const existing = groups.get(group);
    if (existing) {
      existing.push(route);
    } else {
      groups.set(group, [route]);
    }
  }
  return groups;
}

const defaultNavLinkClass = ({ isActive }: { isActive: boolean }): string =>
  [
    'flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors select-none',
    isActive
      ? 'bg-indigo-50 text-indigo-700 font-medium dark:bg-indigo-900/40 dark:text-indigo-300'
      : 'text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700',
  ].join(' ');

/**
 * Registry-driven navigation links grouped by `route.group`.
 *
 * Filters out:
 * - Routes with `lifecycle === 'boundary'`
 * - Routes with `discoverable === false`
 * - Routes whose `minimumRole` exceeds the current role
 */
export function RouteCapabilityNav({
  routes,
  config,
  collapsed = false,
  groupHeadingClassName,
  navLinkClassName,
}: RouteCapabilityNavProps): React.ReactElement {
  const visibleRoutes = routes.filter(
    (r) =>
      r.lifecycle !== 'boundary' &&
      r.discoverable !== false &&
      roleAtLeast(config.currentRole, r.minimumRole, config.roleOrder)
  );

  const groups = groupRoutes(visibleRoutes);

  const linkClass =
    typeof navLinkClassName === 'function'
      ? navLinkClassName
      : navLinkClassName
        ? ({ isActive }: { isActive: boolean }) =>
            `${defaultNavLinkClass({ isActive })} ${navLinkClassName}`
        : defaultNavLinkClass;

  return (
    <nav aria-label="Product navigation">
      {Array.from(groups.entries()).map(([group, groupRoutes], gi) => (
        <div key={group || '__ungrouped'} className={gi > 0 ? 'mt-4' : undefined}>
          {group && !collapsed && (
            <p
              className={
                groupHeadingClassName ??
                'px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-gray-500 dark:text-gray-400 select-none'
              }
            >
              {group}
            </p>
          )}
          {groupRoutes.map((route) => (
            <NavLink
              key={route.path}
              to={route.path}
              end={route.path === '/'}
              className={linkClass}
              title={collapsed ? route.label : undefined}
              aria-label={route.label}
            >
              {/* Icon placeholder — products supply icon resolution via CSS or override */}
              <span
                className="flex h-4 w-4 shrink-0 items-center justify-center"
                data-icon={route.iconName ?? 'circle'}
                aria-hidden="true"
              />
              {!collapsed && <span className="truncate">{route.label}</span>}
              {!collapsed && route.lifecycle === 'preview' && (
                <span className="ml-auto rounded-full bg-amber-100 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-amber-700 dark:bg-amber-900/40 dark:text-amber-400">
                  Preview
                </span>
              )}
            </NavLink>
          ))}
        </div>
      ))}
    </nav>
  );
}
