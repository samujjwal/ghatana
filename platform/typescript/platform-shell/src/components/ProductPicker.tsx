/**
 * ProductPicker — landing page showing the available Ghatana products.
 *
 * Renders a grid of cards, one per product.  Each card calls `onNavigate`
 * with the product's base path so the host application can update the URL
 * without the shell needing a hard router dependency.
 *
 * @doc.type component
 * @doc.purpose Landing page product selection grid
 * @doc.layer shared
 * @doc.pattern PresentationalComponent
 */
import React from 'react';
import { useAtomValue } from 'jotai';
import { isAuthenticatedAtom } from '../atoms/authAtom';
import { hasRealTenantAtom } from '../atoms/tenantAtom';

/* ─── product catalog ──────────────────────────────────────────────────────── */

interface Product {
  id: string;
  name: string;
  tagline: string;
  description: string;
  path: string;
  color: string;          // Tailwind ring / accent colour
  iconPath: string;       // SVG path data for 24×24 icon
}

const PRODUCTS: Product[] = [
  {
    id: 'aep',
    name: 'AEP',
    tagline: 'Agentic Event Processor',
    description:
      'Build, run, and observe event-driven agent pipelines.  Chain operators, manage HITL review queues, and monitor pattern learning in real time.',
    path: '/aep',
    color: 'indigo',
    iconPath:
      'M13 10V3L4 14h7v7l9-11h-7z',   // lightning bolt
  },
  {
    id: 'data-cloud',
    name: 'Data Cloud',
    tagline: 'Four-tier Event Fabric',
    description:
      'Ingest, route, and replay events across HOT / WARM / COLD / ARCHIVE tiers.  Explore memory planes, browse entities, and design event workflows.',
    path: '/data-cloud',
    color: 'sky',
    iconPath:
      'M3 15a4 4 0 004 4h9a5 5 0 10-.1-9.999 5.002 5.002 0 10-9.78 2.096A4.001 4.001 0 003 15z', // cloud
  },
  {
    id: 'yappc',
    name: 'YAPPC',
    tagline: 'Lifecycle Orchestration',
    description:
      'Design and run complex lifecycle workflows with DAG-based process plans, multi-step approvals, and full audit trails.',
    path: '/yappc',
    color: 'emerald',
    iconPath:
      'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4', // clipboard-check
  },
];

const COLOR_MAP: Record<string, { ring: string; bg: string; text: string; accent: string }> = {
  indigo: {
    ring: 'ring-indigo-200 dark:ring-indigo-800 hover:ring-indigo-400',
    bg: 'bg-indigo-50 dark:bg-indigo-950',
    text: 'text-indigo-600 dark:text-indigo-400',
    accent: 'bg-indigo-600 hover:bg-indigo-700',
  },
  sky: {
    ring: 'ring-sky-200 dark:ring-sky-800 hover:ring-sky-400',
    bg: 'bg-sky-50 dark:bg-sky-950',
    text: 'text-sky-600 dark:text-sky-400',
    accent: 'bg-sky-600 hover:bg-sky-700',
  },
  emerald: {
    ring: 'ring-emerald-200 dark:ring-emerald-800 hover:ring-emerald-400',
    bg: 'bg-emerald-50 dark:bg-emerald-950',
    text: 'text-emerald-600 dark:text-emerald-400',
    accent: 'bg-emerald-600 hover:bg-emerald-700',
  },
};

/* ─── ProductCard ──────────────────────────────────────────────────────────── */

function ProductCard({
  product,
  onNavigate,
}: {
  product: Product;
  onNavigate: (path: string) => void;
}) {
  const c = COLOR_MAP[product.color] ?? COLOR_MAP.indigo;

  return (
    <article
      className={[
        'flex flex-col rounded-xl ring-2 transition-all duration-150 p-6 cursor-pointer',
        c.ring,
        c.bg,
      ].join(' ')}
      onClick={() => onNavigate(product.path)}
      role="link"
      tabIndex={0}
      aria-label={`Open ${product.name} — ${product.tagline}`}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onNavigate(product.path);
      }}
    >
      {/* Icon */}
      <div className={['mb-4 inline-flex h-10 w-10 items-center justify-center rounded-lg', c.accent].join(' ')}>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="h-6 w-6 text-white"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.8}
          aria-hidden
        >
          <path strokeLinecap="round" strokeLinejoin="round" d={product.iconPath} />
        </svg>
      </div>

      <h2 className={['text-xl font-bold mb-0.5', c.text].join(' ')}>{product.name}</h2>
      <p className="text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-3">
        {product.tagline}
      </p>
      <p className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed flex-1">
        {product.description}
      </p>

      <div className={['mt-5 inline-flex items-center text-sm font-semibold', c.text].join(' ')}>
        Open
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="ml-1 h-4 w-4"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
      </div>
    </article>
  );
}

/* ─── ProductPicker ────────────────────────────────────────────────────────── */

export interface ProductPickerProps {
  /**
   * Called when the user selects a product.
   *
   * @param path  Product base path, e.g. `/aep`
   */
  onNavigate: (path: string) => void;
}

/**
 * Full-page product picker.
 *
 * Shown at the root `/` route.  If the user is unauthenticated, displays a
 * prompt instead of the product grid.
 */
export function ProductPicker({ onNavigate }: ProductPickerProps) {
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);
  const hasTenant = useAtomValue(hasRealTenantAtom);

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">
          Welcome to Ghatana
        </h1>
        <p className="text-gray-500 max-w-md">
          Please sign in to access the platform.
        </p>
      </div>
    );
  }

  if (!hasTenant) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center px-4">
        <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-100">
          Select a Tenant
        </h1>
        <p className="text-gray-500 max-w-md">
          Use the tenant selector in the top bar to choose your workspace.
        </p>
      </div>
    );
  }

  return (
    <main className="flex flex-col items-center justify-start px-6 py-12 min-h-full bg-gray-50 dark:bg-gray-950">
      <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
        Ghatana Platform
      </h1>
      <p className="text-gray-500 dark:text-gray-400 mb-10 text-base text-center max-w-xl">
        Choose a product area to get started.
      </p>

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 w-full max-w-5xl">
        {PRODUCTS.map((p) => (
          <ProductCard key={p.id} product={p} onNavigate={onNavigate} />
        ))}
      </div>
    </main>
  );
}
