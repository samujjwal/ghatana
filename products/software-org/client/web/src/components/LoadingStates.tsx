/**
 * Loading States Component
 *
 * Reusable loading indicators for different scenarios.
 * Provides skeleton loaders and spinner components.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { Box, Card, Spinner } from '@ghatana/ui';

/**
 * Page Loading Spinner
 */
export function PageLoader() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        <Spinner size="large" />
        <p className="mt-4 text-slate-600 dark:text-neutral-400">Loading...</p>
      </div>
    </div>
  );
}

/**
 * Dashboard Skeleton Loader
 */
export function DashboardSkeleton() {
  return (
    <Box className="p-6 space-y-6">
      {/* Header Skeleton */}
      <div className="h-12 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse w-64"></div>

      {/* KPI Cards Skeleton */}
      <div className="grid grid-cols-4 gap-4">
        {[1, 2, 3, 4].map((i) => (
          <Card key={i}>
            <Box className="p-4 space-y-3">
              <div className="h-4 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse w-20"></div>
              <div className="h-8 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse w-16"></div>
              <div className="h-3 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse w-24"></div>
            </Box>
          </Card>
        ))}
      </div>

      {/* Content Skeleton */}
      <div className="grid grid-cols-2 gap-4">
        {[1, 2].map((i) => (
          <Card key={i}>
            <Box className="p-4 space-y-3">
              <div className="h-6 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse w-32"></div>
              <div className="h-32 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse"></div>
            </Box>
          </Card>
        ))}
      </div>
    </Box>
  );
}

/**
 * Table Skeleton Loader
 */
export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-16 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse"></div>
      ))}
    </div>
  );
}

/**
 * List Skeleton Loader
 */
export function ListSkeleton({ items = 5 }: { items?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: items }).map((_, i) => (
        <Card key={i}>
          <Box className="p-4 space-y-2">
            <div className="h-5 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse w-3/4"></div>
            <div className="h-4 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse w-1/2"></div>
          </Box>
        </Card>
      ))}
    </div>
  );
}

/**
 * Inline Spinner
 */
export function InlineLoader({ text = 'Loading...' }: { text?: string }) {
  return (
    <div className="flex items-center gap-2">
      <Spinner size="small" />
      <span className="text-sm text-slate-600 dark:text-neutral-400">{text}</span>
    </div>
  );
}

