/**
 * PageHeader Component
 * 
 * Consistent page header with title, description, and optional actions.
 * Use this component at the top of all route pages for a unified look.
 * 
 * @doc.type component
 * @doc.purpose Consistent page header layout
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { ReactNode } from 'react';
import { Link } from 'react-router';
import { ArrowLeft as ArrowBack } from 'lucide-react';

interface PageHeaderProps {
    /** Page title */
    title: string;
    /** Optional description below the title */
    description?: string;
    /** Optional back link */
    backTo?: {
        href: string;
        label: string;
    };
    /** Optional action buttons (right side) */
    actions?: ReactNode;
    /** Optional className for custom styling */
    className?: string;
}

export function PageHeader({ title, description, backTo, actions, className = '' }: PageHeaderProps) {
    return (
        <div className={`mb-8 ${className}`}>
            {backTo && (
                <Link
                    to={backTo.href}
                    className="inline-flex items-center gap-1 text-sm font-medium text-text-secondary hover:text-primary-600 mb-3 transition-colors no-underline"
                >
                    <ArrowBack className="w-4 h-4" />
                    <span>{backTo.label}</span>
                </Link>
            )}

            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-3xl font-bold text-text-primary tracking-tight">
                        {title}
                    </h1>
                    {description && (
                        <p className="mt-2 text-text-secondary text-lg">
                            {description}
                        </p>
                    )}
                </div>

                {actions && (
                    <div className="flex items-center gap-3">
                        {actions}
                    </div>
                )}
            </div>
        </div>
    );
}

export default PageHeader;
