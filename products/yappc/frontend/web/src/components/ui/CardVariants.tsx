/**
 * Card Components Variants
 * 
 * Specialized card components for common use cases.
 * Extends the base Card component with specific layouts.
 * 
 * @doc.type component
 * @doc.purpose Specialized card variants
 * @doc.layer ui
 * @doc.pattern Composition Components
 */

import React from 'react';
import { Card } from './Card';
import type { ReactNode } from 'react';

// ============================================================================
// Stats Card
// ============================================================================

interface StatsCardProps {
    /** Title */
    title: string;
    /** Value */
    value: string | number;
    /** Change indicator */
    change?: {
        value: string;
        type: 'increase' | 'decrease' | 'neutral';
    };
    /** Icon */
    icon?: ReactNode;
    /** Loading state */
    loading?: boolean;
    /** Additional actions */
    actions?: ReactNode;
}

/**
 * Stats Card for displaying metrics and KPIs
 */
export const StatsCard: React.FC<StatsCardProps> = ({
    title,
    value,
    change,
    icon,
    loading = false,
    actions,
}) => {
    return (
        <Card variant="elevated" size="md" className="relative">
            {/* Header */}
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-3">
                    {icon && (
                        <div className="p-2 bg-primary-light rounded-lg text-primary">
                            {icon}
                        </div>
                    )}
                    <div>
                        <h3 className="text-sm font-medium text-muted">{title}</h3>
                    </div>
                </div>
                {actions && (
                    <div className="flex items-center gap-2">
                        {actions}
                    </div>
                )}
            </div>

            {/* Content */}
            <div className="flex items-end justify-between">
                <div>
                    {loading ? (
                        <div className="h-8 w-24 bg-muted rounded animate-pulse" />
                    ) : (
                        <div className="text-2xl font-bold text-primary">
                            {value}
                        </div>
                    )}
                </div>

                {change && (
                    <div className={`flex items-center gap-1 text-sm ${change.type === 'increase' ? 'text-success' :
                            change.type === 'decrease' ? 'text-error' :
                                'text-muted'
                        }`}>
                        {change.type === 'increase' && (
                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                            </svg>
                        )}
                        {change.type === 'decrease' && (
                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 17h8m0 0V9m0 8l-8-8-4 4-6-6" />
                            </svg>
                        )}
                        <span>{change.value}</span>
                    </div>
                )}
            </div>
        </Card>
    );
};

// ============================================================================
// Feature Card
// ============================================================================

interface FeatureCardProps {
    /** Title */
    title: string;
    /** Description */
    description: string;
    /** Icon */
    icon?: ReactNode;
    /** Clickable */
    clickable?: boolean;
    /** Loading state */
    loading?: boolean;
    /** Additional content */
    children?: ReactNode;
}

/**
 * Feature Card for showcasing features and capabilities
 */
export const FeatureCard: React.FC<FeatureCardProps> = ({
    title,
    description,
    icon,
    clickable = false,
    loading = false,
    children,
}) => {
    return (
        <Card
            variant={clickable ? "elevated" : "default"}
            size="md"
            clickable={clickable}
            className="text-center"
        >
            {/* Icon */}
            {icon && (
                <div className="flex justify-center mb-4">
                    <div className="p-3 bg-primary-light rounded-full text-primary">
                        {icon}
                    </div>
                </div>
            )}

            {/* Content */}
            {loading ? (
                <div className="space-y-3">
                    <div className="h-6 w-3/4 bg-muted rounded animate-pulse mx-auto" />
                    <div className="h-4 w-full bg-muted rounded animate-pulse" />
                    <div className="h-4 w-5/6 bg-muted rounded animate-pulse mx-auto" />
                </div>
            ) : (
                <>
                    <h3 className="text-lg font-semibold text-primary mb-2">
                        {title}
                    </h3>
                    <p className="text-muted mb-4">
                        {description}
                    </p>
                    {children && (
                        <div className="mt-4">
                            {children}
                        </div>
                    )}
                </>
            )}
        </Card>
    );
};

// ============================================================================
// Action Card
// ============================================================================

interface ActionCardProps {
    /** Title */
    title: string;
    /** Description */
    description?: string;
    /** Primary action */
    primaryAction?: ReactNode;
    /** Secondary actions */
    secondaryActions?: ReactNode;
    /** Status */
    status?: 'default' | 'warning' | 'error' | 'success';
    /** Loading state */
    loading?: boolean;
}

/**
 * Action Card for user interactions and workflows
 */
export const ActionCard: React.FC<ActionCardProps> = ({
    title,
    description,
    primaryAction,
    secondaryActions,
    status = 'default',
    loading = false,
}) => {
    const statusColors = {
        default: 'border-surface',
        warning: 'border-warning',
        error: 'border-error',
        success: 'border-success',
    };

    return (
        <Card
            variant="default"
            size="md"
            className={`border-l-4 ${statusColors[status]}`}
        >
            {/* Content */}
            {loading ? (
                <div className="space-y-3">
                    <div className="h-6 w-3/4 bg-muted rounded animate-pulse" />
                    <div className="h-4 w-full bg-muted rounded animate-pulse" />
                    <div className="h-10 w-32 bg-muted rounded animate-pulse" />
                </div>
            ) : (
                <>
                    <div className="mb-4">
                        <h3 className="text-lg font-semibold text-primary mb-2">
                            {title}
                        </h3>
                        {description && (
                            <p className="text-muted">
                                {description}
                            </p>
                        )}
                    </div>

                    {/* Actions */}
                    <div className="flex items-center justify-between gap-4">
                        {secondaryActions && (
                            <div className="flex items-center gap-2">
                                {secondaryActions}
                            </div>
                        )}
                        {primaryAction && (
                            <div className="flex-shrink-0">
                                {primaryAction}
                            </div>
                        )}
                    </div>
                </>
            )}
        </Card>
    );
};

// ============================================================================
// Media Card
// ============================================================================

interface MediaCardProps {
    /** Title */
    title: string;
    /** Description */
    description?: string;
    /** Media content (image, video, etc.) */
    media: ReactNode;
    /** Media position */
    mediaPosition?: 'top' | 'left' | 'right';
    /** Actions */
    actions?: ReactNode;
    /** Loading state */
    loading?: boolean;
}

/**
 * Media Card for content with images or other media
 */
export const MediaCard: React.FC<MediaCardProps> = ({
    title,
    description,
    media,
    mediaPosition = 'top',
    actions,
    loading = false,
}) => {
    const mediaLayout = mediaPosition === 'left' ? 'flex-row' : mediaPosition === 'right' ? 'flex-row-reverse' : 'flex-col';

    return (
        <Card variant="elevated" size="md" clickable className="overflow-hidden">
            <div className={`flex ${mediaLayout} gap-4`}>
                {/* Media */}
                <div className={mediaPosition === 'top' ? 'w-full' : 'w-1/3'}>
                    {loading ? (
                        <div className="aspect-video bg-muted rounded animate-pulse" />
                    ) : (
                        media
                    )}
                </div>

                {/* Content */}
                <div className="flex-1 min-w-0">
                    {loading ? (
                        <div className="space-y-3">
                            <div className="h-6 w-3/4 bg-muted rounded animate-pulse" />
                            <div className="h-4 w-full bg-muted rounded animate-pulse" />
                            <div className="h-4 w-5/6 bg-muted rounded animate-pulse" />
                        </div>
                    ) : (
                        <>
                            <h3 className="text-lg font-semibold text-primary mb-2 truncate">
                                {title}
                            </h3>
                            {description && (
                                <p className="text-muted text-sm mb-3 line-clamp-3">
                                    {description}
                                </p>
                            )}
                            {actions && (
                                <div className="flex items-center gap-2">
                                    {actions}
                                </div>
                            )}
                        </>
                    )}
                </div>
            </div>
        </Card>
    );
};

export default {
    StatsCard,
    FeatureCard,
    ActionCard,
    MediaCard,
};
