/**
 * PageCard Component
 *
 * Consistent card component for list/grid items.
 *
 * @doc.type component
 * @doc.purpose Page card component
 * @doc.layer product
 */

import React from 'react';
import { Link } from 'react-router';
import { LucideIcon } from 'lucide-react';
import clsx from 'clsx';
import { CARD_STYLES, ICON_BOX_STYLES, COLORS, getCategoryStyle } from './theme';

export interface PageCardProps {
    /** Card title */
    title: string;
    /** Card subtitle/description */
    subtitle?: string;
    /** Link destination (makes card clickable) */
    to?: string;
    /** Icon component */
    icon?: LucideIcon;
    /** Category for icon coloring */
    category?: string;
    /** Custom icon background class */
    iconBg?: string;
    /** Custom icon color class */
    iconColor?: string;
    /** Badge to show in header */
    badge?: React.ReactNode;
    /** Meta information (e.g., counts, status) */
    meta?: React.ReactNode;
    /** Card body content */
    children?: React.ReactNode;
    /** Footer content */
    footer?: React.ReactNode;
    /** Additional class names */
    className?: string;
    /** Card size variant */
    size?: 'sm' | 'md' | 'lg';
}

/**
 * PageCard - Consistent card for list/grid items
 *
 * @example
 * ```tsx
 * <PageCard
 *   title="Code Review Agent"
 *   subtitle="Automated code review and analysis"
 *   to="/agents/code-review"
 *   icon={Bot}
 *   category="engineering"
 *   meta={<span>5 capabilities</span>}
 * >
 *   <CapabilityBadges items={agent.capabilities} />
 * </PageCard>
 * ```
 */
export function PageCard({
    title,
    subtitle,
    to,
    icon: Icon,
    category,
    iconBg,
    iconColor,
    badge,
    meta,
    children,
    footer,
    className,
    size = 'md',
}: PageCardProps) {
    const categoryStyle = getCategoryStyle(category);
    const resolvedIconBg = iconBg || categoryStyle.bg;
    const resolvedIconColor = iconColor || categoryStyle.icon;

    const iconSize = {
        sm: ICON_BOX_STYLES.sm,
        md: ICON_BOX_STYLES.md,
        lg: ICON_BOX_STYLES.lg,
    }[size];

    const padding = {
        sm: CARD_STYLES.padding.sm,
        md: CARD_STYLES.padding.md,
        lg: CARD_STYLES.padding.lg,
    }[size];

    const content = (
        <>
            {/* Header */}
            <div className="flex items-start gap-3 mb-3">
                {Icon && (
                    <div className={clsx(iconSize, resolvedIconBg, 'flex-shrink-0')}>
                        <Icon className={clsx(
                            size === 'sm' ? 'h-4 w-4' : 'h-5 w-5',
                            resolvedIconColor
                        )} />
                    </div>
                )}
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                        <h3 className={clsx(
                            'font-semibold truncate',
                            COLORS.neutral.textStrong,
                            size === 'sm' ? 'text-sm' : 'text-base'
                        )}>
                            {title}
                        </h3>
                        {badge}
                    </div>
                    {subtitle && (
                        <p className={clsx(
                            'mt-1 line-clamp-2',
                            COLORS.neutral.text,
                            size === 'sm' ? 'text-xs' : 'text-sm'
                        )}>
                            {subtitle}
                        </p>
                    )}
                </div>
            </div>

            {/* Meta info */}
            {meta && (
                <div className={clsx('mb-3', COLORS.neutral.textMuted, 'text-sm')}>
                    {meta}
                </div>
            )}

            {/* Body */}
            {children && (
                <div className="mb-3">
                    {children}
                </div>
            )}

            {/* Footer */}
            {footer && (
                <div className={clsx(
                    'pt-3 mt-auto border-t',
                    COLORS.neutral.border
                )}>
                    {footer}
                </div>
            )}
        </>
    );

    const cardClasses = clsx(
        CARD_STYLES.base,
        padding,
        to && CARD_STYLES.hover,
        'flex flex-col',
        className
    );

    if (to) {
        return (
            <Link to={to} className={clsx(cardClasses, 'block')}>
                {content}
            </Link>
        );
    }

    return (
        <div className={cardClasses}>
            {content}
        </div>
    );
}

// =============================================================================
// Badge Components for Cards
// =============================================================================

export interface TagBadgesProps {
    /** Array of tag strings */
    tags: string[];
    /** Maximum tags to show */
    max?: number;
    /** Badge variant */
    variant?: 'default' | 'category';
    /** Category for coloring (when variant is 'category') */
    category?: string;
}

/**
 * TagBadges - Display array of tags with overflow
 */
export function TagBadges({
    tags,
    max = 3,
    variant = 'default',
    category,
}: TagBadgesProps) {
    if (!tags || tags.length === 0) return null;

    const visibleTags = tags.slice(0, max);
    const remainingCount = tags.length - max;
    const categoryStyle = getCategoryStyle(category);

    const badgeClasses = variant === 'category'
        ? categoryStyle.badge
        : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300';

    return (
        <div className="flex flex-wrap gap-1">
            {visibleTags.map((tag, idx) => (
                <span
                    key={idx}
                    className={clsx(
                        'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium',
                        badgeClasses
                    )}
                >
                    {tag}
                </span>
            ))}
            {remainingCount > 0 && (
                <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-500">
                    +{remainingCount} more
                </span>
            )}
        </div>
    );
}
