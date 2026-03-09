import React from 'react';

/**
 * StatsGrid - Reusable statistics display component
 *
 * <p><b>Purpose</b><br>
 * Displays key statistics in a responsive grid layout.
 * Used for landing pages to highlight platform capabilities.
 *
 * <p><b>Features</b><br>
 * - Responsive 3-column grid
 * - Customizable stat values and labels
 * - Icon/emoji support
 * - Dark mode support
 * - Metric highlighting
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <StatsGrid
 *   stats={[
 *     { icon: '9', label: 'Feature Areas' },
 *     { icon: '⚡', label: 'Real-time Data Updates' }
 *   ]}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Statistics grid display
 * @doc.layer product
 * @doc.pattern Molecule
 */

interface Stat {
    icon?: string;
    value?: string | number;
    label: string;
    color?: string;
}

interface StatsGridProps {
    stats: Stat[];
    className?: string;
}

export const StatsGrid: React.FC<StatsGridProps> = ({ stats, className = '' }) => (
    <div className={`grid grid-cols-3 gap-4 md:gap-8 mb-16 ${className}`}>
        {stats.map((stat, idx) => (
            <div key={idx} className="text-center">
                {stat.icon && (
                    <div className={`text-3xl md:text-4xl font-bold mb-2 ${stat.color || 'text-blue-600 dark:text-indigo-400'}`}>
                        {stat.icon}
                    </div>
                )}
                {stat.value && (
                    <div className={`text-3xl md:text-4xl font-bold mb-2 ${stat.color || 'text-blue-600 dark:text-indigo-400'}`}>
                        {stat.value}
                    </div>
                )}
                <p className="text-slate-600 dark:text-neutral-400">{stat.label}</p>
            </div>
        ))}
    </div>
);
