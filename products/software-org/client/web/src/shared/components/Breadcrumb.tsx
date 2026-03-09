import React from 'react';
import { Link } from 'react-router';
import { useNavigation } from '@/context/NavigationContext';

/**
 * Breadcrumb navigation component
 *
 * <p><b>Purpose</b><br>
 * Displays navigation path as breadcrumbs with home link.
 * Shows current page at end, all previous pages as clickable links.
 *
 * <p><b>Features</b><br>
 * - Shows full navigation path
 * - Clickable links to parent pages
 * - Icons for visual distinction
 * - Responsive design (hides on mobile if only 2 items)
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <Breadcrumb />
 * }</pre>
 *
 * <p><b>Example Output</b><br>
 * 🏠 Home / 📊 Control Tower / Details
 *
 * @doc.type component
 * @doc.purpose Breadcrumb navigation component
 * @doc.layer product
 * @doc.pattern Navigation
 */
export const Breadcrumb: React.FC = () => {
    const { breadcrumbs, isHomePage } = useNavigation();

    // Don't show breadcrumb on home page
    if (isHomePage) {
        return null;
    }

    return (
        <nav className="hidden sm:flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400 px-4 py-2 bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800">
            {breadcrumbs.map((crumb, index) => {
                const isLast = index === breadcrumbs.length - 1;

                return (
                    <React.Fragment key={crumb.path}>
                        {index > 0 && (
                            <span className="text-slate-400 dark:text-slate-600">/</span>
                        )}
                        {isLast ? (
                            // Current page (not clickable)
                            <span className="font-medium text-slate-900 dark:text-neutral-100 flex items-center gap-1">
                                {crumb.icon && <span className="text-base">{crumb.icon}</span>}
                                {crumb.label}
                            </span>
                        ) : (
                            // Parent page (clickable)
                            <Link
                                to={crumb.path}
                                className="hover:text-slate-900 dark:hover:text-white transition-colors flex items-center gap-1"
                            >
                                {crumb.icon && <span className="text-base">{crumb.icon}</span>}
                                {crumb.label}
                            </Link>
                        )}
                    </React.Fragment>
                );
            })}
        </nav>
    );
};

export default Breadcrumb;
