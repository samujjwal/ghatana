import type { Feature } from './FeatureGrid';

/**
 * Grid of pinned features for quick access.
 *
 * <p><b>Purpose</b><br>
 * Displays user's pinned features in a grid layout with pin/unpin toggle.
 * Features can be manually reordered by the user (drag-drop in future enhancement).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <PinnedFeaturesGrid
 *     features={pinnedFeatures}
 *     onFeatureClick={handleFeatureClick}
 *     onPin={handlePin}
 *     onUnpin={handleUnpin}
 * />
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Grid layout with feature cards
 * - Pin/unpin toggle button
 * - Quick navigation to features
 * - Dark mode support
 * - Responsive design (1-3 columns)
 * - Empty state with suggestion to pin features
 *
 * <p><b>Future Enhancement</b><br>
 * - Drag-drop reordering with @dnd-kit/core
 *
 * @doc.type component
 * @doc.purpose Pinned features grid for quick access
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

export interface PinnedFeaturesGridProps {
    /** Array of pinned features */
    features: Feature[];
    /** Click handler for feature navigation */
    onFeatureClick?: (feature: Feature) => void;
    /** Handler for pinning a feature */
    onPin?: (featureId: string) => void;
    /** Handler for unpinning a feature (receives feature title) */
    onUnpin?: (featureTitle: string) => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Renders a grid of pinned features.
 */
export function PinnedFeaturesGrid({
    features,
    onFeatureClick,
    onUnpin,
    className = '',
}: PinnedFeaturesGridProps) {
    return (
        <div className={`mb-12 ${className}`}>
            {/* Section Title */}
            <div className="flex items-center justify-between mb-6">
                <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-200">Pinned Features</h2>
                <button className="text-sm text-blue-600 dark:text-indigo-400 hover:underline">Manage pins</button>
            </div>

            {/* Grid */}
            {features.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {features.map((feature) => (
                        <div
                            key={feature.title}
                            className="
                                relative rounded-xl border-2 border-slate-200 dark:border-neutral-600
                                p-6 bg-white dark:bg-slate-900
                                hover:border-blue-400 dark:hover:border-blue-600
                                hover:shadow-md
                                transition-all duration-200 cursor-pointer
                            "
                            onClick={() => onFeatureClick?.(feature)}
                        >
                            {/* Unpin Button */}
                            <button
                                className="
                                    absolute top-3 right-3
                                    w-8 h-8 rounded-full
                                    bg-slate-100 dark:bg-neutral-800
                                    hover:bg-red-100 dark:hover:bg-red-900
                                    text-slate-600 dark:text-neutral-400
                                    hover:text-red-600 dark:hover:text-red-400
                                    transition-colors duration-150
                                    flex items-center justify-center
                                "
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onUnpin?.(feature.title);
                                }}
                                title="Unpin feature"
                            >
                                📌
                            </button>

                            {/* Icon */}
                            <div className={`flex items-center justify-center w-12 h-12 rounded-lg mb-4 ${feature.color}`}>
                                <span className="text-2xl">{feature.icon}</span>
                            </div>

                            {/* Title */}
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-200 mb-2">
                                {feature.title}
                            </h3>

                            {/* Description */}
                            <p className="text-sm text-slate-600 dark:text-neutral-400 line-clamp-2">
                                {feature.description}
                            </p>

                            {/* Badge (if available) */}
                            {feature.badge && (
                                <span className="inline-block mt-3 px-3 py-1 rounded-full text-xs font-medium bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300">
                                    {feature.badge}
                                </span>
                            )}
                        </div>
                    ))}
                </div>
            ) : (
                // Empty State
                <div className="text-center py-12 rounded-xl border-2 border-dashed border-slate-300 dark:border-neutral-600 bg-slate-50 dark:bg-slate-900">
                    <span className="text-6xl mb-4 block">📌</span>
                    <p className="text-lg text-slate-900 dark:text-neutral-200 font-semibold mb-2">
                        No pinned features yet
                    </p>
                    <p className="text-sm text-slate-600 dark:text-neutral-400 mb-4">
                        Pin your most-used features for quick access
                    </p>
                    <button className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium text-sm transition-colors">
                        Browse Features
                    </button>
                </div>
            )}
        </div>
    );
}
