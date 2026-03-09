import React, { createContext, useContext, useCallback, useMemo } from 'react';
import { useLocation } from 'react-router';

/**
 * Breadcrumb item in navigation path
 *
 * @doc.type type
 * @doc.purpose Breadcrumb navigation item
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface BreadcrumbItem {
    /** Display label for breadcrumb */
    label: string;
    /** Route path */
    path: string;
    /** Optional icon or emoji for visual distinction */
    icon?: string;
}

/**
 * Navigation context for managing breadcrumbs and page navigation
 *
 * <p><b>Purpose</b><br>
 * Provides breadcrumb data, page titles, and navigation helpers
 * to support consistent navigation experience across the application.
 *
 * <p><b>Features</b><br>
 * - Automatic breadcrumb generation from routes
 * - Page title management
 * - Navigation path tracking
 * - Helper functions for route info
 *
 * @doc.type interface
 * @doc.purpose Navigation context interface
 * @doc.layer product
 * @doc.pattern Context
 */
export interface NavigationContextType {
    /** Current page breadcrumbs */
    breadcrumbs: BreadcrumbItem[];
    /** Current page title */
    pageTitle: string;
    /** Current route path */
    currentPath: string;
    /** Check if currently on home page */
    isHomePage: boolean;
    /** Get route display name */
    getPageLabel: (path: string) => string;
}

const NavigationContext = createContext<NavigationContextType | undefined>(undefined);

/**
 * Route to label mapping for breadcrumb display
 *
 * @doc.type constant
 * @doc.purpose Route label mappings
 * @doc.layer product
 * @doc.pattern Configuration
 */
const ROUTE_LABELS: Record<string, { label: string; icon?: string }> = {
    '/': { label: 'Home', icon: '🏠' },
    '/dashboard': { label: 'Control Tower', icon: '📊' },
    '/departments': { label: 'Organization', icon: '🏢' },
    '/workflows': { label: 'Workflows', icon: '🔄' },
    '/hitl': { label: 'HITL Console', icon: '✋' },
    '/simulator': { label: 'Event Simulator', icon: '⚡' },
    '/reports': { label: 'Reports', icon: '📈' },
    '/security': { label: 'Security', icon: '🔒' },
    '/models': { label: 'Model Catalog', icon: '🎓' },
    '/settings': { label: 'Settings', icon: '⚙️' },
    '/help': { label: 'Help Center', icon: '❓' },
    '/export': { label: 'Data Export', icon: '📥' },
    '/realtime-monitor': { label: 'Real-Time Monitor', icon: '⏱️' },
    '/ml-observatory': { label: 'ML Observatory', icon: '🧠' },
    '/automation': { label: 'Automation Engine', icon: '⚙️' },
    '/team/performance-reviews': { label: 'Performance Reviews', icon: '⭐' },
    '/budget/planning': { label: 'Budget Planning', icon: '💰' },
};

/**
 * Generate breadcrumb items from current route path
 *
 * @param path - Current route path
 * @returns Array of breadcrumb items
 *
 * @doc.type function
 * @doc.purpose Generate breadcrumbs from path
 * @doc.layer product
 * @doc.pattern Helper
 */
function generateBreadcrumbs(path: string): BreadcrumbItem[] {
    // Always include home
    const breadcrumbs: BreadcrumbItem[] = [
        {
            label: ROUTE_LABELS['/'].label,
            path: '/',
            icon: ROUTE_LABELS['/'].icon,
        },
    ];

    // Add current route if not home
    if (path !== '/') {
        const routeInfo = ROUTE_LABELS[path];
        if (routeInfo) {
            breadcrumbs.push({
                label: routeInfo.label,
                path,
                icon: routeInfo.icon,
            });
        } else {
            // Fallback for unknown routes - format path
            const label = path
                .split('/')
                .filter(Boolean)
                .map(seg => seg.charAt(0).toUpperCase() + seg.slice(1).replace('-', ' '))
                .join(' > ');
            breadcrumbs.push({
                label,
                path,
            });
        }
    }

    return breadcrumbs;
}

/**
 * Navigation context provider
 *
 * <p><b>Purpose</b><br>
 * Provides navigation context to all child components.
 * Automatically generates breadcrumbs and page title from current route.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <NavigationProvider>
 *   <App />
 * </NavigationProvider>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Navigation context provider
 * @doc.layer product
 * @doc.pattern Provider
 */
export function NavigationProvider({ children }: { children: React.ReactNode }) {
    const location = useLocation();

    const getPageLabel = useCallback((path: string): string => {
        return ROUTE_LABELS[path]?.label || path;
    }, []);

    const value = useMemo<NavigationContextType>(() => {
        const breadcrumbs = generateBreadcrumbs(location.pathname);
        const pageTitle = breadcrumbs[breadcrumbs.length - 1]?.label || 'Software-Org';
        const isHomePage = location.pathname === '/';

        return {
            breadcrumbs,
            pageTitle,
            currentPath: location.pathname,
            isHomePage,
            getPageLabel,
        };
    }, [location.pathname, getPageLabel]);

    return (
        <NavigationContext.Provider value={value}>
            {children}
        </NavigationContext.Provider>
    );
}

/**
 * Hook to access navigation context
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { breadcrumbs, pageTitle, isHomePage } = useNavigation();
 * }</pre>
 *
 * @returns Navigation context value
 * @throws Error if used outside NavigationProvider
 *
 * @doc.type hook
 * @doc.purpose Access navigation context
 * @doc.layer product
 * @doc.pattern Hook
 */
export function useNavigation(): NavigationContextType {
    const context = useContext(NavigationContext);
    if (!context) {
        throw new Error('useNavigation must be used within NavigationProvider');
    }
    return context;
}
