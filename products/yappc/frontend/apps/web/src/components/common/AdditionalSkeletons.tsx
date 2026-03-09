/**
 * Additional Skeleton Components
 * 
 * Extended skeleton screens for specific UI patterns including tables,
 * forms, navigation, and complex layouts.
 * 
 * @doc.type component
 * @doc.purpose Specialized loading placeholders
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { Skeleton } from './SkeletonLoaders';

/**
 * Table Skeleton - For data tables
 * Shows header and rows with configurable columns
 */
export function TableSkeleton({
    rows = 10,
    columns = 5,
    showCheckbox = false,
    showActions = false,
}: {
    rows?: number;
    columns?: number;
    showCheckbox?: boolean;
    showActions?: boolean;
}) {
    return (
        <div
            className="w-full bg-bg-paper rounded-lg border border-divider overflow-hidden"
            role="status"
            aria-label="Loading table data"
            aria-busy="true"
        >
            {/* Table Header */}
            <div className="border-b border-divider bg-bg-default">
                <div className="flex items-center gap-4 p-4">
                    {showCheckbox && <Skeleton className="w-4 h-4 rounded" />}
                    {Array.from({ length: columns }).map((_, i) => (
                        <Skeleton
                            key={`header-${i}`}
                            className={`h-4 ${i === 0 ? 'w-40' : 'w-24'}`}
                        />
                    ))}
                    {showActions && <Skeleton className="w-16 h-4 ml-auto" />}
                </div>
            </div>

            {/* Table Rows */}
            <div className="divide-y divide-divider">
                {Array.from({ length: rows }).map((_, rowIndex) => (
                    <div key={`row-${rowIndex}`} className="flex items-center gap-4 p-4">
                        {showCheckbox && <Skeleton className="w-4 h-4 rounded" />}
                        {Array.from({ length: columns }).map((_, colIndex) => (
                            <Skeleton
                                key={`cell-${rowIndex}-${colIndex}`}
                                className={`h-3 ${colIndex === 0 ? 'w-40' : 'w-24'}`}
                            />
                        ))}
                        {showActions && (
                            <div className="flex gap-2 ml-auto">
                                <Skeleton className="w-8 h-8 rounded" />
                                <Skeleton className="w-8 h-8 rounded" />
                            </div>
                        )}
                    </div>
                ))}
            </div>
            <span className="sr-only">Loading table data, please wait</span>
        </div>
    );
}

/**
 * Form Skeleton - For form loading states
 * Shows form fields with labels
 */
export function FormSkeleton({
    fields = 5,
    showButtons = true,
}: {
    fields?: number;
    showButtons?: boolean;
}) {
    return (
        <div
            className="space-y-6"
            role="status"
            aria-label="Loading form"
            aria-busy="true"
        >
            {Array.from({ length: fields }).map((_, i) => (
                <div key={`field-${i}`} className="space-y-2">
                    <Skeleton className="h-4 w-32" />
                    <Skeleton className="h-10 w-full rounded-lg" />
                </div>
            ))}

            {showButtons && (
                <div className="flex gap-3 justify-end pt-4">
                    <Skeleton className="h-10 w-24 rounded-lg" />
                    <Skeleton className="h-10 w-32 rounded-lg" />
                </div>
            )}
            <span className="sr-only">Loading form, please wait</span>
        </div>
    );
}

/**
 * List Item Skeleton - For list views
 */
export function ListItemSkeleton() {
    return (
        <div className="flex items-center gap-4 p-4 rounded-lg border border-divider bg-bg-paper">
            <Skeleton className="w-12 h-12 rounded-lg flex-shrink-0" />
            <div className="flex-1 space-y-2">
                <Skeleton className="h-5 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
            </div>
            <Skeleton className="w-20 h-8 rounded-lg" />
        </div>
    );
}

/**
 * List Skeleton - Multiple list items
 */
export function ListSkeleton({ items = 5 }: { items?: number }) {
    return (
        <div
            className="space-y-3"
            role="status"
            aria-label={`Loading ${items} items`}
            aria-busy="true"
        >
            {Array.from({ length: items }).map((_, i) => (
                <ListItemSkeleton key={i} />
            ))}
            <span className="sr-only">Loading list, please wait</span>
        </div>
    );
}

/**
 * Navigation Skeleton - For sidebar/menu loading
 */
export function NavigationSkeleton({ items = 8 }: { items?: number }) {
    return (
        <nav
            className="space-y-2 p-4"
            role="status"
            aria-label="Loading navigation"
            aria-busy="true"
        >
            {Array.from({ length: items }).map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                    <Skeleton className="w-5 h-5 rounded" />
                    <Skeleton className="h-4 w-32" />
                </div>
            ))}
            <span className="sr-only">Loading navigation, please wait</span>
        </nav>
    );
}

/**
 * Header Skeleton - For page headers
 */
export function HeaderSkeleton({
    showActions = true,
    showBreadcrumbs = false,
}: {
    showActions?: boolean;
    showBreadcrumbs?: boolean;
}) {
    return (
        <header
            className="space-y-4"
            role="status"
            aria-label="Loading header"
            aria-busy="true"
        >
            {showBreadcrumbs && (
                <div className="flex items-center gap-2">
                    <Skeleton className="h-4 w-16" />
                    <span className="text-text-secondary">/</span>
                    <Skeleton className="h-4 w-20" />
                    <span className="text-text-secondary">/</span>
                    <Skeleton className="h-4 w-24" />
                </div>
            )}

            <div className="flex items-center justify-between">
                <div className="space-y-2">
                    <Skeleton className="h-8 w-64" />
                    <Skeleton className="h-4 w-96" />
                </div>

                {showActions && (
                    <div className="flex gap-3">
                        <Skeleton className="h-10 w-28 rounded-lg" />
                        <Skeleton className="h-10 w-36 rounded-lg" />
                    </div>
                )}
            </div>
            <span className="sr-only">Loading header, please wait</span>
        </header>
    );
}

/**
 * Stats Card Skeleton - For metric/KPI cards
 */
export function StatsCardSkeleton() {
    return (
        <div
            className="p-6 rounded-lg border border-divider bg-bg-paper"
            role="status"
            aria-label="Loading statistics"
            aria-busy="true"
        >
            <div className="flex items-start justify-between mb-4">
                <Skeleton className="w-8 h-8 rounded" />
                <Skeleton className="w-16 h-6 rounded-full" />
            </div>
            <Skeleton className="h-8 w-24 mb-2" />
            <Skeleton className="h-4 w-32" />
            <span className="sr-only">Loading statistics, please wait</span>
        </div>
    );
}

/**
 * Stats Grid Skeleton - Multiple stat cards
 */
export function StatsGridSkeleton({ cards = 4 }: { cards?: number }) {
    return (
        <div
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4"
            role="status"
            aria-label="Loading statistics dashboard"
            aria-busy="true"
        >
            {Array.from({ length: cards }).map((_, i) => (
                <StatsCardSkeleton key={i} />
            ))}
            <span className="sr-only">Loading dashboard statistics, please wait</span>
        </div>
    );
}

/**
 * Profile Skeleton - For user/profile pages
 */
export function ProfileSkeleton() {
    return (
        <div
            className="space-y-6"
            role="status"
            aria-label="Loading profile"
            aria-busy="true"
        >
            {/* Header */}
            <div className="flex items-start gap-6">
                <Skeleton className="w-24 h-24 rounded-full flex-shrink-0" />
                <div className="flex-1 space-y-3">
                    <Skeleton className="h-8 w-48" />
                    <Skeleton className="h-4 w-64" />
                    <div className="flex gap-2 mt-4">
                        <Skeleton className="h-10 w-28 rounded-lg" />
                        <Skeleton className="h-10 w-28 rounded-lg" />
                    </div>
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-3 gap-4">
                {Array.from({ length: 3 }).map((_, i) => (
                    <div key={i} className="text-center space-y-2">
                        <Skeleton className="h-8 w-16 mx-auto" />
                        <Skeleton className="h-4 w-24 mx-auto" />
                    </div>
                ))}
            </div>

            {/* Content sections */}
            <div className="space-y-4">
                <Skeleton className="h-6 w-32" />
                <div className="grid grid-cols-2 gap-4">
                    <Skeleton className="h-32 rounded-lg" />
                    <Skeleton className="h-32 rounded-lg" />
                </div>
            </div>
            <span className="sr-only">Loading profile, please wait</span>
        </div>
    );
}

/**
 * Page Skeleton - Full page loading layout
 * Combines header, content, and sidebar patterns
 */
export function PageSkeleton({
    showSidebar = false,
    contentType = 'list',
}: {
    showSidebar?: boolean;
    contentType?: 'list' | 'table' | 'cards' | 'form';
}) {
    return (
        <div
            className="flex h-screen"
            role="status"
            aria-label="Loading page"
            aria-busy="true"
        >
            {showSidebar && (
                <aside className="w-64 border-r border-divider bg-bg-paper">
                    <NavigationSkeleton items={10} />
                </aside>
            )}

            <main className="flex-1 overflow-auto">
                <div className="p-6 space-y-6">
                    <HeaderSkeleton showBreadcrumbs />

                    <div className="space-y-4">
                        {contentType === 'list' && <ListSkeleton items={8} />}
                        {contentType === 'table' && <TableSkeleton />}
                        {contentType === 'cards' && (
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                {Array.from({ length: 6 }).map((_, i) => (
                                    <Skeleton key={i} className="h-48 rounded-lg" />
                                ))}
                            </div>
                        )}
                        {contentType === 'form' && <FormSkeleton />}
                    </div>
                </div>
            </main>
            <span className="sr-only">Loading page content, please wait</span>
        </div>
    );
}

/**
 * Chart Skeleton - For data visualization placeholders
 */
export function ChartSkeleton({
    type = 'bar',
    height = 'h-64'
}: {
    type?: 'bar' | 'line' | 'pie';
    height?: string;
}) {
    return (
        <div
            className={`w-full ${height} rounded-lg border border-divider bg-bg-paper p-6`}
            role="status"
            aria-label="Loading chart"
            aria-busy="true"
        >
            <div className="flex items-center justify-between mb-4">
                <Skeleton className="h-5 w-32" />
                <Skeleton className="h-6 w-20 rounded-full" />
            </div>

            {type === 'bar' && (
                <div className="flex items-end justify-between gap-2 h-full pb-8">
                    {Array.from({ length: 7 }).map((_, i) => (
                        <Skeleton
                            key={i}
                            className="w-full rounded-t"
                            style={{ height: `${Math.random() * 60 + 40}%` }}
                        />
                    ))}
                </div>
            )}

            {type === 'line' && (
                <div className="relative h-full">
                    <svg className="w-full h-full opacity-30">
                        <polyline
                            points="0,100 50,80 100,60 150,75 200,50 250,40 300,30"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            className="text-grey-400"
                        />
                    </svg>
                </div>
            )}

            {type === 'pie' && (
                <div className="flex items-center justify-center h-full">
                    <Skeleton className="w-32 h-32 rounded-full" />
                </div>
            )}
            <span className="sr-only">Loading chart, please wait</span>
        </div>
    );
}

/**
 * Modal/Dialog Content Skeleton
 */
export function DialogContentSkeleton() {
    return (
        <div
            className="space-y-4"
            role="status"
            aria-label="Loading dialog content"
            aria-busy="true"
        >
            <Skeleton className="h-6 w-48" />
            <div className="space-y-3">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-5/6" />
                <Skeleton className="h-4 w-4/6" />
            </div>
            <div className="flex gap-3 justify-end pt-4">
                <Skeleton className="h-10 w-24 rounded-lg" />
                <Skeleton className="h-10 w-32 rounded-lg" />
            </div>
            <span className="sr-only">Loading content, please wait</span>
        </div>
    );
}
