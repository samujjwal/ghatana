import { Link, useLocation } from 'react-router';
import { ChevronRight, Home } from 'lucide-react';

/**
 * Breadcrumb Navigation
 *
 * <p><b>Purpose</b><br>
 * Contextual navigation showing current location in the app hierarchy.
 * Automatically generates breadcrumbs based on route structure.
 *
 * <p><b>Features</b><br>
 * - Auto-generated from route path
 * - Clickable navigation to parent pages
 * - Mobile-responsive (collapses on small screens)
 *
 * @doc.type component
 * @doc.purpose Breadcrumb navigation
 * @doc.layer product
 * @doc.pattern Navigation
 */

interface BreadcrumbItem {
    label: string;
    path: string;
}

export function Breadcrumbs() {
    const location = useLocation();
    const pathSegments = location.pathname.split('/').filter(Boolean);

    const breadcrumbs: BreadcrumbItem[] = [
        { label: 'Home', path: '/' },
    ];

    let currentPath = '';
    pathSegments.forEach((segment, index) => {
        currentPath += `/${segment}`;
        
        // Convert segment to readable label
        let label = segment
            .split('-')
            .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');

        // Handle special cases
        if (segment === 'ml-observatory') label = 'ML Observatory';
        if (segment === 'ml-model-detail') label = 'Model Detail';
        if (segment === 'metric-detail') label = 'Metric Detail';
        if (segment === 'report-detail') label = 'Report Detail';
        if (segment === 'incident-detail') label = 'Incident Detail';
        if (segment === 'queue-item-detail') label = 'Queue Item Detail';
        if (segment === 'admin') label = 'Admin';
        if (segment === 'build') label = 'Build';
        if (segment === 'observe') label = 'Observe';
        if (segment === 'operate') label = 'Operate';

        // Skip IDs (typically the last segment if it looks like an ID)
        if (index === pathSegments.length - 1 && segment.match(/^[a-z]+-\d+$/)) {
            return;
        }

        breadcrumbs.push({ label, path: currentPath });
    });

    if (breadcrumbs.length <= 1) {
        return null;
    }

    return (
        <nav aria-label="Breadcrumb" className="mb-4">
            <ol className="flex items-center gap-2 text-sm">
                {breadcrumbs.map((crumb, index) => {
                    const isLast = index === breadcrumbs.length - 1;
                    
                    return (
                        <li key={crumb.path} className="flex items-center gap-2">
                            {index > 0 && (
                                <ChevronRight className="h-4 w-4 text-slate-400 dark:text-neutral-600" />
                            )}
                            {isLast ? (
                                <span className="font-medium text-slate-900 dark:text-neutral-100">
                                    {crumb.label}
                                </span>
                            ) : (
                                <Link
                                    to={crumb.path}
                                    className="text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100 transition-colors"
                                >
                                    {index === 0 ? (
                                        <Home className="h-4 w-4" />
                                    ) : (
                                        crumb.label
                                    )}
                                </Link>
                            )}
                        </li>
                    );
                })}
            </ol>
        </nav>
    );
}
