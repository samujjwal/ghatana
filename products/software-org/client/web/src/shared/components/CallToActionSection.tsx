import React from 'react';
import { Link } from 'react-router';

/**
 * CallToActionSection - Reusable CTA section component
 *
 * <p><b>Purpose</b><br>
 * Displays a prominent call-to-action section with description and buttons.
 * Used for landing pages and feature onboarding.
 *
 * <p><b>Features</b><br>
 * - Heading and description text
 * - Primary and secondary CTAs
 * - Link-based buttons
 * - Dark mode support
 * - Flexible content layout
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <CallToActionSection
 *   title="Get Started"
 *   description="Start by viewing the Control Tower..."
 *   primaryAction={{ label: 'View Dashboard', href: '/dashboard' }}
 *   secondaryAction={{ label: 'Learn More', href: '/help' }}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Call-to-action section
 * @doc.layer product
 * @doc.pattern Molecule
 */

interface CTAAction {
    label: string;
    href: string;
    icon?: string;
}

interface CallToActionSectionProps {
    title: string;
    description?: string;
    primaryAction: CTAAction;
    secondaryAction?: CTAAction;
    children?: React.ReactNode;
}

export const CallToActionSection: React.FC<CallToActionSectionProps> = ({
    title,
    description,
    primaryAction,
    secondaryAction,
    children,
}) => (
    <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-8 text-center border border-slate-200 dark:border-neutral-600">
        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-4">
            {title}
        </h2>
        {description && (
            <p className="text-slate-600 dark:text-neutral-400 mb-6 max-w-2xl mx-auto">
                {description}
            </p>
        )}
        {children && <div className="mb-6">{children}</div>}
        <div className="flex flex-col sm:flex-row justify-center gap-4">
            <Link
                to={primaryAction.href}
                className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-lg transition-colors"
            >
                {primaryAction.icon && <span>{primaryAction.icon}</span>}
                {primaryAction.label}
                <span>→</span>
            </Link>
            {secondaryAction && (
                <Link
                    to={secondaryAction.href}
                    className="inline-flex items-center justify-center gap-2 px-6 py-3 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-900 dark:text-neutral-100 font-semibold rounded-lg transition-colors"
                >
                    {secondaryAction.icon && <span>{secondaryAction.icon}</span>}
                    {secondaryAction.label}
                </Link>
            )}
        </div>
    </div>
);
