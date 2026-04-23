/**
 * NavBar — operator-cockpit sidebar navigation for AEP.
 *
 * Organises navigation around operator outcomes rather than page nouns:
 *   - Operate  : runs, alerts, review queue
 *   - Build    : pipelines, pipeline builder, patterns
 *   - Learn    : learning episodes, agent memory
 *   - Govern   : policies, compliance, audit
 *   - Catalog  : agents, workflows
 *
 * Contains:
 *  - AEP logo
 *  - Five outcome-grouped navigation sections
 *  - TenantSelector (bottom)
 *  - SseStatus indicator (bottom)
 *
 * @doc.type component
 * @doc.purpose AEP operator-cockpit sidebar — outcome-based navigation
 * @doc.layer frontend
 */
import React from 'react';
import { NavLink, type NavLinkRenderProps, useNavigate } from 'react-router';
import { TenantSelector } from './TenantSelector';
import { SseStatus } from './SseStatus';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@ghatana/design-system';

interface NavItem {
  label: string;
  path: string;
  /** Inline SVG path data (24×24 viewBox). */
  icon: string;
  /** Mark the link active only on exact match (prevents parent paths from staying active). */
  end?: boolean;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

/**
 * Outcome-oriented navigation groups.
 * All existing product surfaces are preserved — they are re-grouped by job-to-be-done.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Operate',
    items: [
      {
        label: 'Runs & Alerts',
        path: '/operate',
        end: true,
        icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z',
      },
      {
        label: 'Review Queue',
        path: '/operate/reviews',
        icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
      },
      {
        label: 'Costs',
        path: '/operate/costs',
        icon: 'M12 8c-2.21 0-4 .895-4 2s1.79 2 4 2 4 .895 4 2-1.79 2-4 2m0-8V4m0 12v4m-7-8h14',
      },
    ],
  },
  {
    label: 'Build',
    items: [
      {
        label: 'Pipelines',
        path: '/build/pipelines',
        end: true,
        icon: 'M4 6h16M4 12h8m-8 6h16',
      },
      {
        label: 'Pipeline Builder',
        path: '/build/pipelines/new',
        icon: 'M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18',
      },
      {
        label: 'Patterns',
        path: '/build/patterns',
        icon: 'M4.871 4A17.926 17.926 0 003 12c0 2.874.673 5.59 1.871 8m14.13 0a17.926 17.926 0 001.87-8c0-2.874-.673-5.59-1.87-8M9 9h1.246a1 1 0 01.961.725l1.586 5.55a1 1 0 00.961.725H15m1-7h-.08a2 2 0 00-1.519.698L9.6 15.302A2 2 0 018.08 16H8',
      },
    ],
  },
  {
    label: 'Learn',
    items: [
      {
        label: 'Episodes',
        path: '/learn/episodes',
        icon: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z',
      },
      {
        label: 'Memory',
        path: '/learn/memory',
        icon: 'M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4',
      },
    ],
  },
  {
    label: 'Govern',
    items: [
      {
        label: 'Governance',
        path: '/govern',
        icon: 'M3 6l3 1m0 0l-3 9a5.002 5.002 0 006.001 0M6 7l3 9M6 7l6-2m6 2l3-1m-3 1l-3 9a5.002 5.002 0 006.001 0M18 7l3 9m-3-9l-6-2m0-2v2m0 16V5m0 16H9m3 0h3',
      },
    ],
  },
  {
    label: 'Catalog',
    items: [
      {
        label: 'Agents',
        path: '/catalog/agents',
        icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z',
      },
      {
        label: 'Workflows',
        path: '/catalog/workflows',
        icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
      },
      {
        label: 'Marketplace',
        path: '/catalog/marketplace',
        icon: 'M3 7l1.664 8.32A2 2 0 006.625 17h10.75a2 2 0 001.96-1.68L21 7M8 7V5a4 4 0 118 0v2M9 11h6',
      },
    ],
  },
];

function navLinkClass({ isActive }: NavLinkRenderProps) {
  return [
    'flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors',
    isActive
      ? 'bg-indigo-100 dark:bg-indigo-900 text-indigo-800 dark:text-indigo-200'
      : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800',
  ].join(' ');
}

/**
 * Sidebar navigation containing outcome-grouped AEP route links, tenant selector, and SSE indicator.
 */
export function NavBar() {
  const navigate = useNavigate();
  const { isAuthenticated, isBootstrappingSession, sessionToken, logout } = useAuth();

  return (
    <nav
      aria-label="AEP navigation"
      className="w-52 flex-shrink-0 border-r border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex flex-col py-4 px-2 overflow-y-auto"
    >
      {/* Logo */}
      <div className="px-3 mb-4 flex items-center gap-2">
        <span className="text-lg font-bold text-indigo-600 dark:text-indigo-400">⚡ AEP</span>
      </div>

      {NAV_GROUPS.map((group, gi) => (
        <div key={group.label} className={gi > 0 ? 'mt-3' : undefined}>
          {/* Section header — visual grouping label, not a link */}
          <p className="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-gray-500 dark:text-gray-400 select-none">
            {group.label}
          </p>
          {group.items.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.end}
              className={navLinkClass}
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="h-4 w-4 flex-shrink-0"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={1.8}
                aria-hidden
              >
                <path strokeLinecap="round" strokeLinejoin="round" d={item.icon} />
              </svg>
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
            {isAuthenticated
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
