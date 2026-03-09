/**
 * PageLayout Component
 *
 * Provides consistent page structure wrapping MainLayout.
 * Use this as the top-level wrapper for all application pages.
 *
 * @doc.type component
 * @doc.purpose Page layout wrapper
 * @doc.layer product
 */

import React from 'react';
import { MainLayout } from '@/app/Layout';
import { PAGE_STYLES } from './theme';
import clsx from 'clsx';

export interface PageLayoutProps {
    /** Page title displayed in header */
    title?: string;
    /** Page subtitle/description */
    subtitle?: string;
    /** Main page content */
    children: React.ReactNode;
    /** Additional class names for the content area */
    className?: string;
    /** Whether to use full-width layout (no max-width constraint) */
    fullWidth?: boolean;
    /** Custom background class (overrides default) */
    background?: string;
}

/**
 * PageLayout - Consistent page wrapper component
 *
 * Wraps content in MainLayout with consistent styling.
 *
 * @example
 * ```tsx
 * <PageLayout title="Agents" subtitle="Manage your AI agents">
 *   <PageFilters ... />
 *   <PageContent>
 *     ...
 *   </PageContent>
 * </PageLayout>
 * ```
 */
export function PageLayout({
    title,
    subtitle,
    children,
    className,
    fullWidth = false,
    background,
}: PageLayoutProps) {
    return (
        <MainLayout title={title} subtitle={subtitle}>
            <div className={clsx(
                background || PAGE_STYLES.container,
                className
            )}>
                <div className={clsx(
                    !fullWidth && PAGE_STYLES.content,
                    PAGE_STYLES.section
                )}>
                    {children}
                </div>
            </div>
        </MainLayout>
    );
}
