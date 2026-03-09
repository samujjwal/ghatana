/**
 * PageContent Component
 *
 * Content area with consistent padding and grid layouts.
 *
 * @doc.type component
 * @doc.purpose Page content container
 * @doc.layer product
 */

import React from 'react';
import clsx from 'clsx';

export interface PageContentProps {
    /** Content to render */
    children: React.ReactNode;
    /** Additional class names */
    className?: string;
}

/**
 * PageContent - Content container with consistent styling
 *
 * @example
 * ```tsx
 * <PageContent>
 *   <div className="grid grid-cols-3 gap-6">
 *     {items.map(item => <Card key={item.id} ... />)}
 *   </div>
 * </PageContent>
 * ```
 */
export function PageContent({ children, className }: PageContentProps) {
    return (
        <div className={clsx('py-2', className)}>
            {children}
        </div>
    );
}

// =============================================================================
// Grid Layout Components
// =============================================================================

export interface PageGridProps {
    /** Grid items */
    children: React.ReactNode;
    /** Number of columns at different breakpoints */
    cols?: {
        sm?: 1 | 2 | 3 | 4;
        md?: 1 | 2 | 3 | 4;
        lg?: 1 | 2 | 3 | 4;
    };
    /** Gap between items */
    gap?: 'sm' | 'md' | 'lg';
    /** Additional class names */
    className?: string;
}

const GRID_COLS = {
    1: 'grid-cols-1',
    2: 'grid-cols-2',
    3: 'grid-cols-3',
    4: 'grid-cols-4',
} as const;

const GRID_GAPS = {
    sm: 'gap-4',
    md: 'gap-6',
    lg: 'gap-8',
} as const;

/**
 * PageGrid - Responsive grid layout
 *
 * @example
 * ```tsx
 * <PageGrid cols={{ sm: 1, md: 2, lg: 3 }} gap="md">
 *   {items.map(item => <Card key={item.id} ... />)}
 * </PageGrid>
 * ```
 */
export function PageGrid({
    children,
    cols = { sm: 1, md: 2, lg: 3 },
    gap = 'md',
    className,
}: PageGridProps) {
    return (
        <div className={clsx(
            'grid',
            cols.sm && GRID_COLS[cols.sm],
            cols.md && `md:${GRID_COLS[cols.md]}`,
            cols.lg && `lg:${GRID_COLS[cols.lg]}`,
            GRID_GAPS[gap],
            className
        )}>
            {children}
        </div>
    );
}

// =============================================================================
// Section Component
// =============================================================================

export interface PageSectionProps {
    /** Section title */
    title?: string;
    /** Section subtitle */
    subtitle?: string;
    /** Section content */
    children: React.ReactNode;
    /** Actions for the section header */
    actions?: React.ReactNode;
    /** Additional class names */
    className?: string;
}

/**
 * PageSection - Titled content section
 *
 * @example
 * ```tsx
 * <PageSection title="Recent Activity" subtitle="Last 24 hours">
 *   <ActivityList items={activities} />
 * </PageSection>
 * ```
 */
export function PageSection({
    title,
    subtitle,
    children,
    actions,
    className,
}: PageSectionProps) {
    return (
        <section className={clsx('space-y-4', className)}>
            {(title || actions) && (
                <div className="flex items-center justify-between">
                    <div>
                        {title && (
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {title}
                            </h2>
                        )}
                        {subtitle && (
                            <p className="text-sm text-slate-600 dark:text-slate-400 mt-0.5">
                                {subtitle}
                            </p>
                        )}
                    </div>
                    {actions}
                </div>
            )}
            {children}
        </section>
    );
}
