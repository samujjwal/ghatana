/**
 * SkeletonLoader Component
 *
 * <p><b>Purpose</b><br>
 * Animated skeleton loader for indicating loading states without blocking UI.
 * Provides better perceived performance than spinners.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <SkeletonLoader variant="card" count={3} />
 * <SkeletonLoader variant="text" lines={4} />
 * <SkeletonLoader variant="metric" count={4} />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Skeleton loading states
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { clsx } from 'clsx';

/**
 * Skeleton loader variants
 */
type SkeletonVariant = 'card' | 'text' | 'metric' | 'avatar' | 'button' | 'image';

/**
 * Component props
 */
export interface SkeletonLoaderProps {
    /**
     * Skeleton variant (card, text, metric, avatar, button, image)
     */
    variant?: SkeletonVariant;

    /**
     * Number of skeleton items to render (for card, metric)
     */
    count?: number;

    /**
     * Number of text lines (for text variant)
     */
    lines?: number;

    /**
     * Additional CSS classes
     */
    className?: string;

    /**
     * Width override (CSS value)
     */
    width?: string;

    /**
     * Height override (CSS value)
     */
    height?: string;
}

/**
 * Base skeleton element with shimmer animation
 */
function SkeletonBase({ className, width, height }: { className?: string; width?: string; height?: string }) {
    return (
        <div
            className={clsx(
                'animate-pulse bg-gradient-to-r from-slate-200 via-slate-300 to-slate-200 dark:from-slate-700 dark:via-slate-600 dark:to-slate-700',
                'bg-[length:200%_100%] animate-shimmer rounded',
                className
            )}
            style={{ width, height }}
        />
    );
}

/**
 * Card skeleton (for QuickActionCard, PinnedFeaturesGrid)
 */
function CardSkeleton() {
    return (
        <div className="bg-white dark:bg-neutral-800 rounded-xl p-6 border border-slate-200 dark:border-neutral-600">
            <div className="flex items-start gap-4">
                <SkeletonBase className="w-12 h-12 rounded-lg" />
                <div className="flex-1 space-y-3">
                    <SkeletonBase className="h-5 w-3/4" />
                    <SkeletonBase className="h-4 w-full" />
                    <SkeletonBase className="h-4 w-2/3" />
                </div>
            </div>
        </div>
    );
}

/**
 * Text skeleton (for descriptions, paragraphs)
 */
function TextSkeleton({ lines = 3 }: { lines: number }) {
    return (
        <div className="space-y-2">
            {Array.from({ length: lines }).map((_, i) => (
                <SkeletonBase
                    key={i}
                    className={clsx(
                        'h-4',
                        i === lines - 1 ? 'w-2/3' : 'w-full'
                    )}
                />
            ))}
        </div>
    );
}

/**
 * Metric skeleton (for PersonaMetricsGrid)
 */
function MetricSkeleton() {
    return (
        <div className="bg-white dark:bg-neutral-800 rounded-xl p-6 border border-slate-200 dark:border-neutral-600">
            <div className="space-y-4">
                <div className="flex items-center justify-between">
                    <SkeletonBase className="h-4 w-24" />
                    <SkeletonBase className="h-6 w-6 rounded-full" />
                </div>
                <SkeletonBase className="h-8 w-20" />
                <SkeletonBase className="h-3 w-32" />
            </div>
        </div>
    );
}

/**
 * Avatar skeleton (for PersonaHero)
 */
function AvatarSkeleton() {
    return <SkeletonBase className="w-16 h-16 rounded-full" />;
}

/**
 * Button skeleton
 */
function ButtonSkeleton() {
    return <SkeletonBase className="h-10 w-32 rounded-lg" />;
}

/**
 * Image skeleton
 */
function ImageSkeleton() {
    return <SkeletonBase className="w-full aspect-video rounded-lg" />;
}

/**
 * Main skeleton loader component
 */
export function SkeletonLoader({
    variant = 'card',
    count = 1,
    lines = 3,
    className,
    width,
    height,
}: SkeletonLoaderProps) {
    // Grid layout for multiple items
    const renderMultiple = (Component: React.ComponentType) => {
        if (count === 1) {
            return <Component />;
        }

        return (
            <div className={clsx('grid gap-4', className)}>
                {Array.from({ length: count }).map((_, i) => (
                    <Component key={i} />
                ))}
            </div>
        );
    };

    switch (variant) {
        case 'card':
            return renderMultiple(CardSkeleton);

        case 'text':
            return <TextSkeleton lines={lines} />;

        case 'metric':
            return (
                <div className={clsx('grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4', className)}>
                    {Array.from({ length: count }).map((_, i) => (
                        <MetricSkeleton key={i} />
                    ))}
                </div>
            );

        case 'avatar':
            return <AvatarSkeleton />;

        case 'button':
            return <ButtonSkeleton />;

        case 'image':
            return <ImageSkeleton />;

        default:
            return <SkeletonBase className={className} width={width} height={height} />;
    }
}

/**
 * Skeleton loader for PersonaHero component
 */
export function PersonaHeroSkeleton() {
    return (
        <div className="bg-white dark:bg-neutral-800 rounded-2xl p-8 mb-6 border border-slate-200 dark:border-neutral-600">
            <div className="flex items-center gap-6">
                <AvatarSkeleton />
                <div className="flex-1 space-y-3">
                    <SkeletonBase className="h-8 w-64" />
                    <SkeletonBase className="h-5 w-40" />
                </div>
                <SkeletonBase className="h-12 w-32 rounded-lg" />
            </div>
        </div>
    );
}

/**
 * Skeleton loader for QuickActionsGrid
 */
export function QuickActionsGridSkeleton({ count = 6 }: { count?: number }) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
            {Array.from({ length: count }).map((_, i) => (
                <CardSkeleton key={i} />
            ))}
        </div>
    );
}

/**
 * Skeleton loader for RecentActivitiesTimeline
 */
export function ActivitiesTimelineSkeleton({ count = 5 }: { count?: number }) {
    return (
        <div className="bg-white dark:bg-neutral-800 rounded-2xl p-6 border border-slate-200 dark:border-neutral-600 space-y-4">
            <SkeletonBase className="h-6 w-48 mb-4" />
            {Array.from({ length: count }).map((_, i) => (
                <div key={i} className="flex gap-4">
                    <SkeletonBase className="w-2 h-16 rounded-full" />
                    <div className="flex-1 space-y-2">
                        <SkeletonBase className="h-5 w-3/4" />
                        <SkeletonBase className="h-4 w-1/2" />
                        <SkeletonBase className="h-3 w-24" />
                    </div>
                </div>
            ))}
        </div>
    );
}

/**
 * Skeleton loader for entire persona dashboard
 */
export function PersonaDashboardSkeleton() {
    return (
        <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 p-6">
            <div className="max-w-7xl mx-auto">
                <PersonaHeroSkeleton />
                <QuickActionsGridSkeleton count={6} />
                <SkeletonLoader variant="metric" count={4} className="mb-6" />
                <ActivitiesTimelineSkeleton count={5} />
            </div>
        </div>
    );
}
