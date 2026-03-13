/**
 * NavBar — collapsible sidebar navigation for all AEP pages.
 *
 * Contains:
 *  - AEP logo
 *  - Navigation links to all top-level pages
 *  - TenantSelector (bottom)
 *  - SseStatus indicator (bottom)
 *
 * @doc.type component
 * @doc.purpose AEP sidebar navigation with tenant and SSE controls
 * @doc.layer frontend
 */
import React from 'react';
import { NavLink, type NavLinkRenderProps } from 'react-router';
import { TenantSelector } from './TenantSelector';
import { SseStatus } from './SseStatus';

interface NavItem {
  label: string;
  path: string;
  /** Inline SVG path data (24×24 viewBox). */
  icon: string;
}

export const NAV_ITEMS: NavItem[] = [
  {
    label: 'Pipelines',
    path: '/pipelines/list',
    icon: 'M4 6h16M4 12h8m-8 6h16',
  },
  {
    label: 'Builder',
    path: '/pipelines',
    icon: 'M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18',
  },
  {
    label: 'Agents',
    path: '/agents',
    icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z',
  },
  {
    label: 'Monitoring',
    path: '/monitoring',
    icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z',
  },
  {
    label: 'Patterns',
    path: '/patterns',
    icon: 'M4.871 4A17.926 17.926 0 003 12c0 2.874.673 5.59 1.871 8m14.13 0a17.926 17.926 0 001.87-8c0-2.874-.673-5.59-1.87-8M9 9h1.246a1 1 0 01.961.725l1.586 5.55a1 1 0 00.961.725H15m1-7h-.08a2 2 0 00-1.519.698L9.6 15.302A2 2 0 018.08 16H8',
  },
  {
    label: 'HITL',
    path: '/hitl',
    icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  {
    label: 'Learning',
    path: '/learning',
    icon: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z',
  },
  {
    label: 'Workflows',
    path: '/workflows',
    icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
  },
  {
    label: 'Memory',
    path: '/memory',
    icon: 'M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18',
  },
];

function navLinkClass({ isActive }: NavLinkRenderProps) {
  return [
    'flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors',
    isActive
      ? 'bg-indigo-100 dark:bg-indigo-900 text-indigo-700 dark:text-indigo-300'
      : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800',
  ].join(' ');
}

/**
 * Sidebar navigation containing all AEP route links, tenant selector, and SSE indicator.
 */
export function NavBar() {
  return (
    <nav
      aria-label="AEP navigation"
      className="w-52 flex-shrink-0 border-r border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 flex flex-col py-4 gap-1 px-2"
    >
      {/* Logo */}
      <div className="px-3 mb-4 flex items-center gap-2">
        <span className="text-lg font-bold text-indigo-600 dark:text-indigo-400">⚡ AEP</span>
      </div>

      {NAV_ITEMS.map((item) => (
        <NavLink key={item.path} to={item.path} className={navLinkClass}>
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

      {/* Bottom section: tenant selector + SSE indicator */}
      <div className="mt-auto flex flex-col gap-1 pb-2">
        <TenantSelector />
        <SseStatus />
      </div>
    </nav>
  );
}
