import React from 'react';
import { Link } from 'react-router';

/**
 * FeatureGrid - Reusable feature card grid component
 *
 * <p><b>Purpose</b><br>
 * Displays a grid of feature cards with links and descriptions.
 * Used for landing pages to showcase all available features.
 *
 * <p><b>Features</b><br>
 * - Responsive grid (1/2/3 columns)
 * - Icon and color support
 * - Hover animations
 * - Dark mode support
 * - Link navigation
 * - Customizable card styling
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <FeatureGrid
 *   features={[
 *     {
 *       icon: '📊',
 *       title: 'Dashboard',
 *       description: 'Real-time metrics...',
 *       href: '/dashboard',
 *       color: 'bg-blue-50'
 *     }
 *   ]}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Feature showcase grid
 * @doc.layer product
 * @doc.pattern Organism
 */

export interface Feature {
    icon: string;
    title: string;
    description: string;
    href: string;
    color: string;
    badge?: string;
}

interface FeatureGridProps {
    features: Feature[];
    columns?: 'auto' | 1 | 2 | 3;
}

const FeatureCard: React.FC<Feature> = ({ icon, title, description, href, color, badge }) => (
    <Link
        to={href}
        className={`group block p-6 rounded-lg border-2 transition-all hover:shadow-lg hover:scale-105 ${color}`}
    >
        <div className="flex items-start justify-between mb-4">
            <div className="flex items-center gap-3">
                <span className="text-4xl">{icon}</span>
                {badge && (
                    <span className="px-2 py-1 text-xs font-semibold bg-amber-500 text-white rounded-full">
                        {badge}
                    </span>
                )}
            </div>
            <span className="text-lg opacity-0 group-hover:opacity-100 transition-opacity">→</span>
        </div>
        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
            {title}
        </h3>
        <p className="text-sm text-slate-600 dark:text-neutral-400">{description}</p>
    </Link>
);

export const FeatureGrid: React.FC<FeatureGridProps> = ({ features, columns = 'auto' }) => {
    const gridClass =
        columns === 1 ? 'grid-cols-1' :
            columns === 2 ? 'grid-cols-1 md:grid-cols-2' :
                columns === 3 ? 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3' :
                    'grid-cols-1 md:grid-cols-2 lg:grid-cols-3';

    return (
        <div className={`grid ${gridClass} gap-6 mb-12`}>
            {features.map((feature) => (
                <FeatureCard key={feature.href} {...feature} />
            ))}
        </div>
    );
};
