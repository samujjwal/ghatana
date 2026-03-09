/**
 * PageHeader Component
 *
 * Consistent page header with title, back navigation, and action buttons.
 *
 * @doc.type component
 * @doc.purpose Page header with navigation
 * @doc.layer product
 */

import React from 'react';
import { Link } from 'react-router';
import { ArrowLeft, LucideIcon } from 'lucide-react';
import clsx from 'clsx';
import { COLORS, ICON_BOX_STYLES } from './theme';

export interface PageHeaderProps {
    /** Page title */
    title: string;
    /** Page subtitle/description */
    subtitle?: string;
    /** Icon component to display */
    icon?: LucideIcon;
    /** Icon color class */
    iconColor?: string;
    /** Icon background class */
    iconBg?: string;
    /** Back navigation link */
    backLink?: string;
    /** Back link label (for accessibility) */
    backLabel?: string;
    /** Action buttons to display on the right */
    actions?: React.ReactNode;
    /** Additional badge/status indicator */
    badge?: React.ReactNode;
    /** Additional class names */
    className?: string;
}

/**
 * PageHeader - Consistent header component
 *
 * @example
 * ```tsx
 * <PageHeader
 *   title="Agents"
 *   subtitle="Manage your AI agents"
 *   icon={Bot}
 *   iconColor="text-blue-600"
 *   iconBg="bg-blue-100 dark:bg-blue-900/30"
 *   backLink="/config"
 *   actions={
 *     <Button>Add Agent</Button>
 *   }
 * />
 * ```
 */
export function PageHeader({
    title,
    subtitle,
    icon: Icon,
    iconColor = 'text-blue-600',
    iconBg = 'bg-blue-100 dark:bg-blue-900/30',
    backLink,
    backLabel = 'Go back',
    actions,
    badge,
    className,
}: PageHeaderProps) {
    return (
        <div className={clsx(
            'bg-white dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700',
            className
        )}>
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                <div className="flex items-center justify-between">
                    {/* Left section: back link + title */}
                    <div className="flex items-center gap-4">
                        {backLink && (
                            <Link
                                to={backLink}
                                className={clsx(
                                    'p-2 -ml-2 rounded-lg',
                                    COLORS.neutral.bgHover,
                                    COLORS.neutral.text
                                )}
                                aria-label={backLabel}
                            >
                                <ArrowLeft className="h-5 w-5" />
                            </Link>
                        )}

                        <div className="flex items-center gap-3">
                            {Icon && (
                                <div className={clsx(ICON_BOX_STYLES.lg, iconBg)}>
                                    <Icon className={clsx('h-6 w-6', iconColor)} />
                                </div>
                            )}
                            <div>
                                <div className="flex items-center gap-2">
                                    <h1 className={clsx(
                                        'text-2xl font-bold',
                                        COLORS.neutral.textStrong
                                    )}>
                                        {title}
                                    </h1>
                                    {badge}
                                </div>
                                {subtitle && (
                                    <p className={clsx('text-sm mt-1', COLORS.neutral.text)}>
                                        {subtitle}
                                    </p>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Right section: actions */}
                    {actions && (
                        <div className="flex items-center gap-2">
                            {actions}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
