/**
 * Theme Constants - Centralized Design Tokens
 *
 * Provides consistent colors, spacing, and styling across all pages.
 * Update these values to change the look and feel globally.
 *
 * @doc.type constants
 * @doc.purpose Centralized design tokens
 * @doc.layer product
 */

// =============================================================================
// Color Palette
// =============================================================================

/** Primary brand colors */
export const COLORS = {
    // Primary actions
    primary: {
        bg: 'bg-blue-600',
        bgHover: 'hover:bg-blue-700',
        text: 'text-blue-600',
        textDark: 'dark:text-blue-400',
        border: 'border-blue-600',
        light: 'bg-blue-100 dark:bg-blue-900/30',
        lightText: 'text-blue-700 dark:text-blue-400',
    },
    // Success/positive states
    success: {
        bg: 'bg-green-600',
        bgHover: 'hover:bg-green-700',
        text: 'text-green-600',
        textDark: 'dark:text-green-400',
        light: 'bg-green-100 dark:bg-green-900/30',
        lightText: 'text-green-700 dark:text-green-400',
    },
    // Warning states
    warning: {
        bg: 'bg-amber-500',
        bgHover: 'hover:bg-amber-600',
        text: 'text-amber-600',
        textDark: 'dark:text-amber-400',
        light: 'bg-amber-100 dark:bg-amber-900/30',
        lightText: 'text-amber-700 dark:text-amber-400',
    },
    // Error/danger states
    danger: {
        bg: 'bg-red-600',
        bgHover: 'hover:bg-red-700',
        text: 'text-red-600',
        textDark: 'dark:text-red-400',
        light: 'bg-red-100 dark:bg-red-900/30',
        lightText: 'text-red-700 dark:text-red-400',
    },
    // Purple accent
    purple: {
        bg: 'bg-purple-600',
        bgHover: 'hover:bg-purple-700',
        text: 'text-purple-600',
        textDark: 'dark:text-purple-400',
        light: 'bg-purple-100 dark:bg-purple-900/30',
        lightText: 'text-purple-700 dark:text-purple-400',
    },
    // Neutral grays
    neutral: {
        bg: 'bg-slate-100 dark:bg-slate-800',
        bgHover: 'hover:bg-slate-200 dark:hover:bg-slate-700',
        text: 'text-slate-600 dark:text-slate-400',
        textStrong: 'text-slate-900 dark:text-white',
        textMuted: 'text-slate-500 dark:text-slate-500',
        border: 'border-slate-200 dark:border-slate-700',
    },
} as const;

// =============================================================================
// Category Color Mappings
// =============================================================================

/** Category-based color assignments for different domains */
export const CATEGORY_STYLES: Record<string, {
    bg: string;
    text: string;
    icon: string;
    badge: string;
}> = {
    // Engineering/Development
    engineering: {
        bg: 'bg-blue-100 dark:bg-blue-900/30',
        text: 'text-blue-700 dark:text-blue-400',
        icon: 'text-blue-600',
        badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    },
    // Quality Assurance
    qa: {
        bg: 'bg-green-100 dark:bg-green-900/30',
        text: 'text-green-700 dark:text-green-400',
        icon: 'text-green-600',
        badge: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    },
    // DevOps/Operations
    devops: {
        bg: 'bg-orange-100 dark:bg-orange-900/30',
        text: 'text-orange-700 dark:text-orange-400',
        icon: 'text-orange-600',
        badge: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    },
    // Security
    security: {
        bg: 'bg-red-100 dark:bg-red-900/30',
        text: 'text-red-700 dark:text-red-400',
        icon: 'text-red-600',
        badge: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    },
    // Compliance
    compliance: {
        bg: 'bg-purple-100 dark:bg-purple-900/30',
        text: 'text-purple-700 dark:text-purple-400',
        icon: 'text-purple-600',
        badge: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    },
    // Data/Analytics
    data: {
        bg: 'bg-cyan-100 dark:bg-cyan-900/30',
        text: 'text-cyan-700 dark:text-cyan-400',
        icon: 'text-cyan-600',
        badge: 'bg-cyan-100 text-cyan-700 dark:bg-cyan-900/30 dark:text-cyan-400',
    },
    // Management
    management: {
        bg: 'bg-indigo-100 dark:bg-indigo-900/30',
        text: 'text-indigo-700 dark:text-indigo-400',
        icon: 'text-indigo-600',
        badge: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
    },
    // Default/Custom
    default: {
        bg: 'bg-slate-100 dark:bg-slate-800',
        text: 'text-slate-700 dark:text-slate-400',
        icon: 'text-slate-600',
        badge: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-400',
    },
};

/** Get category style with fallback */
export function getCategoryStyle(category?: string) {
    if (!category) return CATEGORY_STYLES.default;
    return CATEGORY_STYLES[category.toLowerCase()] || CATEGORY_STYLES.default;
}

// =============================================================================
// Status Color Mappings
// =============================================================================

/** Status-based color assignments */
export const STATUS_STYLES: Record<string, {
    bg: string;
    text: string;
    dot: string;
}> = {
    active: {
        bg: 'bg-green-100 dark:bg-green-900/30',
        text: 'text-green-700 dark:text-green-400',
        dot: 'bg-green-500',
    },
    pending: {
        bg: 'bg-amber-100 dark:bg-amber-900/30',
        text: 'text-amber-700 dark:text-amber-400',
        dot: 'bg-amber-500',
    },
    inactive: {
        bg: 'bg-slate-100 dark:bg-slate-800',
        text: 'text-slate-600 dark:text-slate-400',
        dot: 'bg-slate-400',
    },
    error: {
        bg: 'bg-red-100 dark:bg-red-900/30',
        text: 'text-red-700 dark:text-red-400',
        dot: 'bg-red-500',
    },
    blocked: {
        bg: 'bg-red-100 dark:bg-red-900/30',
        text: 'text-red-700 dark:text-red-400',
        dot: 'bg-red-500',
    },
    'on-track': {
        bg: 'bg-green-100 dark:bg-green-900/30',
        text: 'text-green-700 dark:text-green-400',
        dot: 'bg-green-500',
    },
    'at-risk': {
        bg: 'bg-amber-100 dark:bg-amber-900/30',
        text: 'text-amber-700 dark:text-amber-400',
        dot: 'bg-amber-500',
    },
};

/** Get status style with fallback */
export function getStatusStyle(status?: string) {
    if (!status) return STATUS_STYLES.inactive;
    return STATUS_STYLES[status.toLowerCase()] || STATUS_STYLES.inactive;
}

// =============================================================================
// Common Style Classes
// =============================================================================

/** Base card styles */
export const CARD_STYLES = {
    base: 'bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg',
    hover: 'hover:shadow-lg hover:border-slate-300 dark:hover:border-slate-600 transition-all',
    padding: {
        sm: 'p-3',
        md: 'p-4',
        lg: 'p-6',
    },
} as const;

/** Base input styles */
export const INPUT_STYLES = {
    base: 'bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-sm',
    focus: 'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent',
    placeholder: 'placeholder:text-slate-400 dark:placeholder:text-slate-500',
} as const;

/** Page container styles */
export const PAGE_STYLES = {
    container: 'min-h-screen bg-slate-50 dark:bg-slate-900',
    content: 'max-w-7xl mx-auto px-4 sm:px-6 lg:px-8',
    section: 'py-6',
} as const;

/** Filter pill styles */
export const FILTER_PILL_STYLES = {
    container: 'flex items-center gap-1 bg-slate-100 dark:bg-slate-800 rounded-lg p-1',
    active: 'bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm',
    inactive: 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white',
    base: 'px-3 py-1.5 rounded-md text-sm font-medium transition-colors',
} as const;

/** Icon box styles for cards */
export const ICON_BOX_STYLES = {
    sm: 'w-8 h-8 rounded-lg flex items-center justify-center',
    md: 'w-10 h-10 rounded-lg flex items-center justify-center',
    lg: 'w-12 h-12 rounded-lg flex items-center justify-center',
} as const;
