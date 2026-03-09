/**
 * Theme Configuration
 *
 * Centralized design tokens and theme utilities for consistent styling
 * across all pages and components in the Software-Org application.
 *
 * @doc.type lib
 * @doc.purpose Design system tokens and utilities
 * @doc.layer product
 */

// ============================================================================
// Color Palette
// ============================================================================

export const colors = {
    // Primary brand colors
    primary: {
        50: 'bg-blue-50 dark:bg-blue-950',
        100: 'bg-blue-100 dark:bg-blue-900',
        500: 'bg-blue-500',
        600: 'bg-blue-600',
        700: 'bg-blue-700',
        text: 'text-blue-600 dark:text-blue-400',
        textHover: 'hover:text-blue-700 dark:hover:text-blue-300',
        border: 'border-blue-200 dark:border-blue-800',
    },
    // Entity type colors for visual distinction
    entity: {
        department: {
            bg: 'bg-blue-50 dark:bg-blue-950/50',
            border: 'border-blue-200 dark:border-blue-800',
            icon: 'bg-blue-500',
            text: 'text-blue-700 dark:text-blue-300',
            badge: 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',
        },
        workflow: {
            bg: 'bg-rose-50 dark:bg-rose-950/50',
            border: 'border-rose-200 dark:border-rose-800',
            icon: 'bg-rose-500',
            text: 'text-rose-700 dark:text-rose-300',
            badge: 'bg-rose-100 dark:bg-rose-900 text-rose-800 dark:text-rose-200',
        },
        stage: {
            bg: 'bg-orange-50 dark:bg-orange-950/50',
            border: 'border-orange-200 dark:border-orange-800',
            icon: 'bg-orange-500',
            text: 'text-orange-700 dark:text-orange-300',
            badge: 'bg-orange-100 dark:bg-orange-900 text-orange-800 dark:text-orange-200',
        },
        kpi: {
            bg: 'bg-violet-50 dark:bg-violet-950/50',
            border: 'border-violet-200 dark:border-violet-800',
            icon: 'bg-violet-500',
            text: 'text-violet-700 dark:text-violet-300',
            badge: 'bg-violet-100 dark:bg-violet-900 text-violet-800 dark:text-violet-200',
        },
        agent: {
            bg: 'bg-emerald-50 dark:bg-emerald-950/50',
            border: 'border-emerald-200 dark:border-emerald-800',
            icon: 'bg-emerald-500',
            text: 'text-emerald-700 dark:text-emerald-300',
            badge: 'bg-emerald-100 dark:bg-emerald-900 text-emerald-800 dark:text-emerald-200',
        },
        persona: {
            bg: 'bg-purple-50 dark:bg-purple-950/50',
            border: 'border-purple-200 dark:border-purple-800',
            icon: 'bg-purple-500',
            text: 'text-purple-700 dark:text-purple-300',
            badge: 'bg-purple-100 dark:bg-purple-900 text-purple-800 dark:text-purple-200',
        },
        phase: {
            bg: 'bg-green-50 dark:bg-green-950/50',
            border: 'border-green-200 dark:border-green-800',
            icon: 'bg-green-500',
            text: 'text-green-700 dark:text-green-300',
            badge: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',
        },
        service: {
            bg: 'bg-cyan-50 dark:bg-cyan-950/50',
            border: 'border-cyan-200 dark:border-cyan-800',
            icon: 'bg-cyan-500',
            text: 'text-cyan-700 dark:text-cyan-300',
            badge: 'bg-cyan-100 dark:bg-cyan-900 text-cyan-800 dark:text-cyan-200',
        },
        integration: {
            bg: 'bg-pink-50 dark:bg-pink-950/50',
            border: 'border-pink-200 dark:border-pink-800',
            icon: 'bg-pink-500',
            text: 'text-pink-700 dark:text-pink-300',
            badge: 'bg-pink-100 dark:bg-pink-900 text-pink-800 dark:text-pink-200',
        },
        operator: {
            bg: 'bg-amber-50 dark:bg-amber-950/50',
            border: 'border-amber-200 dark:border-amber-800',
            icon: 'bg-amber-500',
            text: 'text-amber-700 dark:text-amber-300',
            badge: 'bg-amber-100 dark:bg-amber-900 text-amber-800 dark:text-amber-200',
        },
        flow: {
            bg: 'bg-indigo-50 dark:bg-indigo-950/50',
            border: 'border-indigo-200 dark:border-indigo-800',
            icon: 'bg-indigo-500',
            text: 'text-indigo-700 dark:text-indigo-300',
            badge: 'bg-indigo-100 dark:bg-indigo-900 text-indigo-800 dark:text-indigo-200',
        },
    },
    // Status colors
    status: {
        success: 'bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-200 border-green-200 dark:border-green-800',
        warning: 'bg-amber-100 dark:bg-amber-900/50 text-amber-800 dark:text-amber-200 border-amber-200 dark:border-amber-800',
        error: 'bg-red-100 dark:bg-red-900/50 text-red-800 dark:text-red-200 border-red-200 dark:border-red-800',
        info: 'bg-blue-100 dark:bg-blue-900/50 text-blue-800 dark:text-blue-200 border-blue-200 dark:border-blue-800',
        neutral: 'bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-200 border-gray-200 dark:border-gray-700',
    },
} as const;

// ============================================================================
// Component Styles
// ============================================================================

export const components = {
    // Page container
    page: {
        wrapper: 'min-h-screen bg-gray-50 dark:bg-slate-900',
        container: 'max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8',
        header: 'mb-8',
    },
    // Cards
    card: {
        base: 'bg-white dark:bg-slate-900 rounded-xl border border-gray-200 dark:border-slate-700 shadow-sm',
        hover: 'hover:shadow-md hover:border-gray-300 dark:hover:border-slate-600 transition-all duration-200 cursor-pointer',
        padding: 'p-6',
        header: 'border-b border-gray-200 dark:border-slate-700 pb-4 mb-4',
    },
    // Buttons
    button: {
        primary: 'inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
        secondary: 'inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-white dark:bg-slate-800 text-gray-700 dark:text-gray-200 border border-gray-300 dark:border-slate-600 hover:bg-gray-50 dark:hover:bg-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 transition-colors',
        ghost: 'inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 transition-colors',
        danger: 'inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-red-600 text-white hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 transition-colors',
        icon: 'inline-flex items-center justify-center p-2 rounded-lg text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-colors',
    },
    // Form elements
    input: {
        base: 'w-full px-4 py-2 rounded-lg border border-gray-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors',
        label: 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5',
        error: 'border-red-500 focus:ring-red-500 focus:border-red-500',
        helper: 'mt-1.5 text-sm text-gray-500 dark:text-gray-400',
    },
    // Badges
    badge: {
        base: 'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
        sm: 'px-2 py-0.5 text-xs',
        md: 'px-3 py-1 text-sm',
    },
    // Tables
    table: {
        wrapper: 'overflow-hidden rounded-xl border border-gray-200 dark:border-slate-700',
        table: 'min-w-full divide-y divide-gray-200 dark:divide-slate-700',
        thead: 'bg-gray-50 dark:bg-slate-800',
        th: 'px-6 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-300 uppercase tracking-wider',
        tbody: 'bg-white dark:bg-slate-900 divide-y divide-gray-200 dark:divide-slate-700',
        td: 'px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100',
        row: 'hover:bg-gray-50 dark:hover:bg-slate-800 transition-colors cursor-pointer',
    },
    // Section headers
    section: {
        title: 'text-lg font-semibold text-gray-900 dark:text-gray-100',
        subtitle: 'text-sm text-gray-500 dark:text-gray-400 mt-1',
        divider: 'border-t border-gray-200 dark:border-slate-700 my-6',
    },
    // Empty states
    empty: {
        wrapper: 'flex flex-col items-center justify-center py-12 px-4',
        icon: 'w-16 h-16 text-gray-300 dark:text-gray-600 mb-4',
        title: 'text-lg font-medium text-gray-900 dark:text-gray-100 mb-2',
        description: 'text-sm text-gray-500 dark:text-gray-400 text-center max-w-sm',
    },
    // Tabs
    tabs: {
        list: 'flex border-b border-gray-200 dark:border-slate-700 gap-8',
        tab: 'py-3 px-1 text-sm font-medium border-b-2 transition-colors',
        tabActive: 'border-blue-600 dark:border-blue-400 text-blue-600 dark:text-blue-400',
        tabInactive: 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
    },
} as const;

// ============================================================================
// Typography
// ============================================================================

export const typography = {
    h1: 'text-3xl font-bold text-gray-900 dark:text-gray-100',
    h2: 'text-2xl font-semibold text-gray-900 dark:text-gray-100',
    h3: 'text-xl font-semibold text-gray-900 dark:text-gray-100',
    h4: 'text-lg font-medium text-gray-900 dark:text-gray-100',
    body: 'text-base text-gray-700 dark:text-gray-300',
    small: 'text-sm text-gray-600 dark:text-gray-400',
    tiny: 'text-xs text-gray-500 dark:text-gray-500',
    mono: 'font-mono text-sm',
    link: 'text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 hover:underline',
} as const;

// ============================================================================
// Layout
// ============================================================================

export const layout = {
    // Grid layouts
    grid: {
        cols2: 'grid grid-cols-1 md:grid-cols-2 gap-6',
        cols3: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6',
        cols4: 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6',
    },
    // Flex layouts
    flex: {
        between: 'flex items-center justify-between',
        center: 'flex items-center justify-center',
        start: 'flex items-center justify-start',
        col: 'flex flex-col',
        row: 'flex flex-row',
        wrap: 'flex flex-wrap',
        gap2: 'gap-2',
        gap4: 'gap-4',
        gap6: 'gap-6',
    },
    // Spacing
    spacing: {
        section: 'mb-8',
        card: 'mb-6',
        element: 'mb-4',
    },
} as const;

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get entity color scheme by type
 */
export function getEntityColors(entityType: string) {
    const type = entityType.toLowerCase().replace(/s$/, '') as keyof typeof colors.entity;
    return colors.entity[type] || colors.entity.department;
}

/**
 * Get status color classes
 */
export function getStatusColors(status: string) {
    const statusMap: Record<string, keyof typeof colors.status> = {
        healthy: 'success',
        active: 'success',
        running: 'info',
        degraded: 'warning',
        warning: 'warning',
        error: 'error',
        failed: 'error',
        inactive: 'neutral',
        pending: 'neutral',
    };
    return colors.status[statusMap[status.toLowerCase()] || 'neutral'];
}

/**
 * Combine class names
 */
export function cn(...classes: (string | undefined | null | false)[]): string {
    return classes.filter(Boolean).join(' ');
}

export default {
    colors,
    components,
    typography,
    layout,
    getEntityColors,
    getStatusColors,
    cn,
};
