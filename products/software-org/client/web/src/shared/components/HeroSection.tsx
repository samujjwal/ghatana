import React from 'react';

/**
 * HeroSection - Reusable landing page hero component
 *
 * <p><b>Purpose</b><br>
 * Displays a prominent hero section with title, subtitle, and description.
 * Used for landing pages and feature introductions.
 *
 * <p><b>Features</b><br>
 * - Responsive title and subtitle
 * - Optional description text
 * - Dark mode support
 * - Gradient background
 * - Customizable layout
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <HeroSection
 *   title="Software Organization Platform"
 *   subtitle="AI-First DevSecOps Control Center"
 *   description="Unified platform for orchestrating..."
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Landing page hero section
 * @doc.layer product
 * @doc.pattern Molecule
 */

interface HeroSectionProps {
    title: string;
    subtitle?: string;
    description?: string;
    children?: React.ReactNode;
}

export const HeroSection: React.FC<HeroSectionProps> = ({
    title,
    subtitle,
    description,
    children,
}) => (
    <div className="text-center mb-16">
        <h1 className="text-5xl md:text-6xl font-bold text-slate-900 dark:text-neutral-100 mb-6">
            {title}
        </h1>
        {subtitle && (
            <p className="text-xl md:text-2xl text-slate-600 dark:text-neutral-400 mb-8 max-w-3xl mx-auto">
                {subtitle}
            </p>
        )}
        {description && (
            <p className="text-lg text-slate-500 dark:text-slate-500 max-w-2xl mx-auto">
                {description}
            </p>
        )}
        {children}
    </div>
);
