/**
 * Centralized Tailwind Theme Styles
 * 
 * Reusable CSS class definitions for consistent dark/light mode styling
 * across all Ghatana platform products. Import and use these classes
 * instead of hardcoding Tailwind classes directly in components.
 * 
 * @doc.type utility
 * @doc.purpose Centralized Tailwind theme management
 * @doc.layer ui
 * @doc.pattern Design System
 * 
 * @example
 * ```tsx
 * import { cardStyles, textStyles, cn } from '@ghatana/ui';
 * 
 * function MyCard() {
 *   return (
 *     <div className={cn(cardStyles.base, cardStyles.padded)}>
 *       <h2 className={textStyles.h2}>Title</h2>
 *     </div>
 *   );
 * }
 * ```
 */

// =============================================================================
// CARD STYLES
// =============================================================================

export const cardStyles = {
    /** Base card with proper dark mode support */
    base: 'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm',

    /** Interactive card (clickable) */
    interactive: 'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm cursor-pointer hover:border-gray-300 dark:hover:border-gray-600 hover:shadow-md transition-all',

    /** Selected/active card state */
    selected: 'bg-blue-50 dark:bg-blue-900/30 border-blue-500 dark:border-blue-400',

    /** Card with padding */
    padded: 'p-4',

    /** Card header section */
    header: 'border-b border-gray-200 dark:border-gray-700 p-4',

    /** Card body section */
    body: 'p-4',

    /** Card footer section */
    footer: 'border-t border-gray-200 dark:border-gray-700 p-4',
} as const;

// =============================================================================
// TEXT STYLES
// =============================================================================

export const textStyles = {
    /** Primary heading (h1) */
    h1: 'text-2xl font-bold text-gray-900 dark:text-white',

    /** Secondary heading (h2) */
    h2: 'text-xl font-semibold text-gray-900 dark:text-white',

    /** Tertiary heading (h3) */
    h3: 'text-lg font-semibold text-gray-900 dark:text-white',

    /** Small heading (h4) */
    h4: 'text-sm font-medium text-gray-900 dark:text-white',

    /** Primary body text */
    body: 'text-gray-700 dark:text-gray-300',

    /** Secondary/muted text */
    muted: 'text-gray-500 dark:text-gray-400',

    /** Small text */
    small: 'text-sm text-gray-600 dark:text-gray-400',

    /** Extra small text */
    xs: 'text-xs text-gray-500 dark:text-gray-400',

    /** Label text */
    label: 'text-sm font-medium text-gray-700 dark:text-gray-300',

    /** Link text */
    link: 'text-blue-600 dark:text-blue-400 hover:underline',

    /** Monospace/code text */
    mono: 'font-mono text-sm text-gray-900 dark:text-white',
} as const;

// =============================================================================
// BACKGROUND STYLES
// =============================================================================

export const bgStyles = {
    /** Page background */
    page: 'bg-gray-50 dark:bg-gray-900',

    /** Primary surface (cards, panels) */
    surface: 'bg-white dark:bg-gray-800',

    /** Secondary surface (nested elements) */
    surfaceSecondary: 'bg-gray-50 dark:bg-gray-700',

    /** Tertiary surface (deeply nested) */
    surfaceTertiary: 'bg-gray-100 dark:bg-gray-600',

    /** Code/pre block background */
    code: 'bg-gray-100 dark:bg-gray-700',
} as const;

// =============================================================================
// BORDER STYLES
// =============================================================================

export const borderStyles = {
    /** Default border */
    default: 'border border-gray-200 dark:border-gray-700',

    /** Subtle border */
    subtle: 'border border-gray-100 dark:border-gray-800',

    /** Divider line */
    divider: 'border-gray-200 dark:border-gray-700',

    /** Focus ring */
    focus: 'focus:ring-2 focus:ring-blue-500 focus:border-blue-500 dark:focus:ring-blue-400',
} as const;

// =============================================================================
// INPUT STYLES
// =============================================================================

export const inputStyles = {
    /** Base input field */
    base: 'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500',

    /** Select dropdown */
    select: 'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500',

    /** Textarea */
    textarea: 'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-blue-500',

    /** Search input */
    search: 'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500',
} as const;

// =============================================================================
// BUTTON STYLES
// =============================================================================

export const buttonStyles = {
    /** Primary button */
    primary: 'px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors',

    /** Secondary button */
    secondary: 'px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-200 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors',

    /** Danger button */
    danger: 'px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors',

    /** Success button */
    success: 'px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors',

    /** Warning button */
    warning: 'px-4 py-2 bg-yellow-600 text-white rounded-lg hover:bg-yellow-700 transition-colors',

    /** Ghost/text button */
    ghost: 'px-4 py-2 text-gray-700 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors',

    /** Small button modifier */
    sm: 'px-3 py-1.5 text-sm',

    /** Large button modifier */
    lg: 'px-6 py-3 text-lg',
} as const;

// =============================================================================
// BADGE/TAG STYLES
// =============================================================================

export const badgeStyles = {
    /** Default badge */
    default: 'px-2 py-1 text-xs font-medium rounded bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300',

    /** Success badge */
    success: 'px-2 py-1 text-xs font-medium rounded bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',

    /** Warning badge */
    warning: 'px-2 py-1 text-xs font-medium rounded bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200',

    /** Danger badge */
    danger: 'px-2 py-1 text-xs font-medium rounded bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200',

    /** Info badge */
    info: 'px-2 py-1 text-xs font-medium rounded bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',

    /** Purple badge */
    purple: 'px-2 py-1 text-xs font-medium rounded bg-purple-100 dark:bg-purple-900 text-purple-800 dark:text-purple-200',

    /** Tag (pill shaped) */
    tag: 'px-3 py-1 text-sm rounded-full bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300',
} as const;

// =============================================================================
// STATUS COLORS
// =============================================================================

export const statusStyles = {
    /** Online/Active status */
    online: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',

    /** Offline/Inactive status */
    offline: 'bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-300',

    /** Degraded/Warning status */
    degraded: 'bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200',

    /** Error/Failed status */
    error: 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200',

    /** Pending status */
    pending: 'bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200',

    /** Processed/Success status */
    processed: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',

    /** Running status */
    running: 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',
} as const;

// =============================================================================
// TABLE STYLES
// =============================================================================

export const tableStyles = {
    /** Table container */
    container: 'overflow-x-auto',

    /** Table element */
    table: 'w-full',

    /** Table header */
    thead: 'bg-gray-50 dark:bg-gray-700',

    /** Table header cell */
    th: 'px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider',

    /** Table body */
    tbody: 'divide-y divide-gray-200 dark:divide-gray-700',

    /** Table row */
    tr: 'hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors',

    /** Table row (selected) */
    trSelected: 'bg-blue-50 dark:bg-blue-900/30',

    /** Table cell */
    td: 'px-4 py-3 text-sm text-gray-900 dark:text-white',
} as const;

// =============================================================================
// MODAL STYLES
// =============================================================================

export const modalStyles = {
    /** Modal overlay */
    overlay: 'fixed inset-0 bg-black/50 flex items-center justify-center z-50',

    /** Modal container */
    container: 'bg-white dark:bg-gray-800 rounded-lg shadow-xl p-6 w-full',

    /** Modal title */
    title: 'text-xl font-bold text-gray-900 dark:text-white mb-4',

    /** Modal close button */
    closeButton: 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200',
} as const;

// =============================================================================
// TOAST STYLES
// =============================================================================

export const toastStyles = {
    /** Base toast */
    base: 'fixed bottom-4 right-4 px-4 py-3 rounded-lg shadow-lg text-white',

    /** Success toast */
    success: 'bg-green-600',

    /** Error toast */
    error: 'bg-red-600',

    /** Info toast */
    info: 'bg-blue-600',

    /** Warning toast */
    warning: 'bg-yellow-600',
} as const;

// =============================================================================
// METRIC CARD STYLES (with colored left border)
// =============================================================================

export const metricCardStyles = {
    /** Base metric card */
    base: 'bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm',

    /** With green accent */
    green: 'bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-green-500',

    /** With red accent */
    red: 'bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-red-500',

    /** With blue accent */
    blue: 'bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-blue-500',

    /** With orange accent */
    orange: 'bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-orange-500',

    /** With purple accent */
    purple: 'bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-purple-500',

    /** With yellow accent */
    yellow: 'bg-white dark:bg-gray-800 p-4 rounded-lg shadow-sm border-l-4 border-yellow-500',
} as const;

// =============================================================================
// NAVIGATION STYLES
// =============================================================================

export const navStyles = {
    /** Sidebar container */
    sidebar: 'w-64 bg-gray-900 text-white flex flex-col',

    /** Nav section header */
    sectionHeader: 'px-3 py-2 text-xs font-semibold text-gray-400 uppercase tracking-wider',

    /** Nav item */
    item: 'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors text-sm',

    /** Nav item active */
    itemActive: 'bg-blue-600 text-white',

    /** Nav item inactive */
    itemInactive: 'text-gray-300 hover:bg-gray-800 hover:text-white',
} as const;

// =============================================================================
// TYPE EXPORTS
// =============================================================================

export type CardStyleKey = keyof typeof cardStyles;
export type TextStyleKey = keyof typeof textStyles;
export type BgStyleKey = keyof typeof bgStyles;
export type BorderStyleKey = keyof typeof borderStyles;
export type InputStyleKey = keyof typeof inputStyles;
export type ButtonStyleKey = keyof typeof buttonStyles;
export type BadgeStyleKey = keyof typeof badgeStyles;
export type StatusStyleKey = keyof typeof statusStyles;
export type TableStyleKey = keyof typeof tableStyles;
export type ModalStyleKey = keyof typeof modalStyles;
export type ToastStyleKey = keyof typeof toastStyles;
export type MetricCardStyleKey = keyof typeof metricCardStyles;
export type NavStyleKey = keyof typeof navStyles;
