/**
 * Design Tokens
 * 
 * Centralized design system tokens for consistent UI across the application.
 * These tokens define spacing, sizing, and visual properties used throughout.
 * 
 * @doc.type module
 * @doc.purpose Design system constants
 * @doc.layer product
 * @doc.pattern Design Tokens
 */

// ============================================================================
// Spacing
// ============================================================================

export const SPACING = {
    /** Extra small gap - 4px */
    xs: 'gap-1',
    /** Small gap - 8px */
    sm: 'gap-2',
    /** Medium gap - 16px */
    md: 'gap-4',
    /** Large gap - 24px */
    lg: 'gap-6',
    /** Extra large gap - 32px */
    xl: 'gap-8',
} as const;

// ============================================================================
// Sidebar (DEPRECATED - Removed in favor of UnifiedHeaderBar)
// ============================================================================

/**
 * @deprecated Sidebar has been removed. Use UnifiedHeaderBar instead.
 * These constants are kept for backwards compatibility only.
 */
export const SIDEBAR = {
    /** @deprecated Expanded width - 224px */
    expandedWidth: 'w-56',
    expandedWidthPx: 224,
    /** @deprecated Collapsed width - 60px */
    collapsedWidth: 'w-[60px]',
    collapsedWidthPx: 60,
    /** @deprecated Transition duration */
    transition: 'transition-all duration-200 ease-in-out',
} as const;

// ============================================================================
// Toolbar
// ============================================================================

export const TOOLBAR = {
    /** Unified header height - 56px (optimized for space efficiency) */
    headerHeight: 'h-14',
    headerHeightPx: 56,
    /** Standard toolbar height - 48px */
    height: 'h-12',
    heightPx: 48,
    /** Compact toolbar height - 40px */
    compactHeight: 'h-10',
    compactHeightPx: 40,
    /** Toolbar horizontal padding */
    padding: 'px-4',
    paddingPx: 16,
    /** Gap between toolbar sections */
    gap: 'gap-3',
    gapPx: 12,
} as const;

// ============================================================================
// Panels
// ============================================================================

export const PANELS = {
    /** Standard panel width - 320px */
    width: 'w-80',
    widthPx: 320,
    /** Wide panel width - 400px */
    wideWidth: 'w-[400px]',
    wideWidthPx: 400,
    /** Task panel collapsed width - 48px */
    taskCollapsedWidth: 'w-12',
    taskCollapsedWidthPx: 48,
    /** Task panel expanded width - 280px */
    taskExpandedWidth: 'w-72',
    taskExpandedWidthPx: 280,
} as const;

// ============================================================================
// Status Bar
// ============================================================================

export const STATUS_BAR = {
    /** Status bar height - 32px */
    height: 'h-8',
    heightPx: 32,
} as const;

// ============================================================================
// Buttons
// ============================================================================

export const BUTTON = {
    /** Small button height - 28px */
    sm: 'h-7',
    smPx: 28,
    /** Default button height - 36px */
    default: 'h-9',
    defaultPx: 36,
    /** Large button height - 44px */
    lg: 'h-11',
    lgPx: 44,
} as const;

// ============================================================================
// Inputs
// ============================================================================

export const INPUT = {
    /** Standard input height - 36px */
    height: 'h-9',
    heightPx: 36,
} as const;

// ============================================================================
// Badges
// ============================================================================

export const BADGE = {
    /** Badge height - 24px */
    height: 'h-6',
    heightPx: 24,
} as const;

// ============================================================================
// Border Radius
// ============================================================================

export const RADIUS = {
    /** Button radius - 8px */
    button: 'rounded-lg',
    /** Card radius - 12px */
    card: 'rounded-xl',
    /** Badge radius - full */
    badge: 'rounded-full',
    /** Input radius - 8px */
    input: 'rounded-lg',
    /** Panel radius - 0 */
    panel: 'rounded-none',
} as const;

// ============================================================================
// Z-Index
// ============================================================================

export const Z_INDEX = {
    /** Canvas base */
    canvas: 0,
    /** Floating controls */
    controls: 20,
    /** Main header/navigation */
    header: 50,
    /** Toolbar */
    toolbar: 100,
    /** Panels */
    panel: 40,
    /** Floating cards (e.g., NextBestTask) */
    floatingCard: 45,
    /** Command palette */
    commandPalette: 60,
    /** Toasts */
    toast: 70,
    /** Dropdown menus - rendered via portal to avoid clipping */
    dropdown: 1200,
    /** Modals */
    modal: 1300,
} as const;

// ============================================================================
// Transitions
// ============================================================================

export const TRANSITIONS = {
    /** Fast transition - 150ms */
    fast: 'transition-all duration-150',
    /** Default transition - 200ms */
    default: 'transition-all duration-200',
    /** Slow transition - 300ms */
    slow: 'transition-all duration-300',
} as const;

// ============================================================================
// Canvas Layout
// ============================================================================

export const CANVAS_LAYOUT = {
    /** Top bar height */
    topBarHeight: 'h-12',
    topBarHeightPx: 48,
    /** Status bar height */
    statusBarHeight: 'h-8',
    statusBarHeightPx: 32,
    /** Minimum usable canvas height */
    minCanvasHeight: 'calc(100vh - 48px - 32px)',
} as const;

// ============================================================================
// Canvas Mode Colors (from CANVAS_TOOLBAR_UI_UX_SPECIFICATION.md)
// ============================================================================

/**
 * Mode colors for canvas toolbar and UI accents.
 * Each mode has a primary color that represents its activity type.
 * Colors align with the 7-phase lifecycle model.
 */
export const MODE_COLORS = {
    /** Brainstorm - Indigo (Idea generation, intent) */
    brainstorm: {
        primary: '#6366f1', // indigo-500
        light: '#eef2ff', // indigo-50
        dark: '#4f46e5', // indigo-600
        text: '#312e81', // indigo-900
    },
    /** Diagram - Violet (Architecture, structure) */
    diagram: {
        primary: '#8b5cf6', // violet-500
        light: '#f5f3ff', // violet-50
        dark: '#7c3aed', // violet-600
        text: '#4c1d95', // violet-900
    },
    /** Design - Amber/Yellow (UI/UX design) */
    design: {
        primary: '#f59e0b', // amber-500
        light: '#fffbeb', // amber-50
        dark: '#d97706', // amber-600
        text: '#78350f', // amber-900
    },
    /** Code - Emerald/Green (Implementation) */
    code: {
        primary: '#10b981', // emerald-500
        light: '#ecfdf5', // emerald-50
        dark: '#059669', // emerald-600
        text: '#064e3b', // emerald-900
    },
    /** Test - Blue (Testing, validation) */
    test: {
        primary: '#3b82f6', // blue-500
        light: '#eff6ff', // blue-50
        dark: '#2563eb', // blue-600
        text: '#1e3a8a', // blue-900
    },
    /** Deploy - Cyan (Deployment, infrastructure) */
    deploy: {
        primary: '#06b6d4', // cyan-500
        light: '#ecfeff', // cyan-50
        dark: '#0891b2', // cyan-600
        text: '#164e63', // cyan-900
    },
    /** Observe - Pink/Rose (Monitoring, insights) */
    observe: {
        primary: '#ec4899', // pink-500
        light: '#fdf2f8', // pink-50
        dark: '#db2777', // pink-600
        text: '#831843', // pink-900
    },
} as const;

/**
 * Abstraction level colors for consistent visual hierarchy
 */
export const LEVEL_COLORS = {
    /** System - Teal (High-level architecture) */
    system: {
        primary: '#14b8a6', // teal-500
        light: '#f0fdfa', // teal-50
        dark: '#0f766e', // teal-600
    },
    /** Component - Blue (Module relationships) */
    component: {
        primary: '#3b82f6', // blue-500
        light: '#eff6ff', // blue-50
        dark: '#2563eb', // blue-600
    },
    /** File - Purple (File-level details) */
    file: {
        primary: '#a855f7', // purple-500
        light: '#faf5ff', // purple-50
        dark: '#9333ea', // purple-600
    },
    /** Code - Green (Implementation details) */
    code: {
        primary: '#22c55e', // green-500
        light: '#f0fdf4', // green-50
        dark: '#16a34a', // green-600
    },
} as const;

// ============================================================================
// Lifecycle Phase Design System
// ============================================================================

/**
 * Lifecycle phase enum mapping - Single source of truth
 * Used throughout the application for type safety
 */
export const LIFECYCLE_PHASE = {
    INTENT: 'INTENT',
    SHAPE: 'SHAPE',
    VALIDATE: 'VALIDATE',
    GENERATE: 'GENERATE',
    RUN: 'RUN',
    OBSERVE: 'OBSERVE',
    IMPROVE: 'IMPROVE',
} as const;

/**
 * Standard phase labels - OFFICIAL labels for all UI
 * Use these labels consistently across the entire application
 */
export const PHASE_LABELS = {
    INTENT: 'Ideate',
    SHAPE: 'Design',
    VALIDATE: 'Validate',
    GENERATE: 'Generate',
    RUN: 'Deploy',
    OBSERVE: 'Monitor',
    IMPROVE: 'Enhance',
} as const;

/**
 * Phase descriptions for user guidance
 */
export const PHASE_DESCRIPTIONS = {
    INTENT: 'Express what you want to build and research requirements',
    SHAPE: 'Design your application architecture and user experience',
    VALIDATE: 'AI validates your design for gaps and risks',
    GENERATE: 'AI generates code, configurations, and resources',
    RUN: 'Deploy and run your application in production',
    OBSERVE: 'Monitor performance, behavior, and user feedback',
    IMPROVE: 'Iterate and enhance based on insights and data',
} as const;

/**
 * Phase icons - Consistent emoji representation
 */
export const PHASE_ICONS = {
    INTENT: '💡',
    SHAPE: '🎨',
    VALIDATE: '✓',
    GENERATE: '⚙️',
    RUN: '🚀',
    OBSERVE: '📊',
    IMPROVE: '🔄',
} as const;

/**
 * Lifecycle phase colors (aligns with UnifiedPhaseRail)
 * 7 phases: Ideate, Design, Validate, Generate, Deploy, Monitor, Enhance
 */
export const PHASE_COLORS = {
    INTENT: {
        primary: '#3b82f6', // blue-500
        background: '#EFF6FF', // blue-50 (light background)
        bg: 'bg-blue-50 dark:bg-blue-900/20',
        text: 'text-blue-700 dark:text-blue-400',
        border: 'border-blue-200 dark:border-blue-800',
        activeBg: 'bg-blue-600 dark:bg-blue-500',
        gradient: 'from-blue-500 to-blue-600',
        icon: '💡',
    },
    SHAPE: {
        primary: '#8b5cf6', // purple-500
        background: '#F5F3FF', // purple-50 (light background)
        bg: 'bg-purple-50 dark:bg-purple-900/20',
        text: 'text-purple-700 dark:text-purple-400',
        border: 'border-purple-200 dark:border-purple-800',
        activeBg: 'bg-purple-600 dark:bg-purple-500',
        gradient: 'from-purple-500 to-purple-600',
        icon: '🎨',
    },
    VALIDATE: {
        primary: '#f59e0b', // amber-500
        background: '#FFFBEB', // amber-50 (light background)
        bg: 'bg-amber-50 dark:bg-amber-900/20',
        text: 'text-amber-700 dark:text-amber-400',
        border: 'border-amber-200 dark:border-amber-800',
        activeBg: 'bg-amber-600 dark:bg-amber-500',
        gradient: 'from-amber-500 to-amber-600',
        icon: '✓',
    },
    GENERATE: {
        primary: '#10b981', // green-500
        background: '#ECFDF5', // green-50 (light background)
        bg: 'bg-green-50 dark:bg-green-900/20',
        text: 'text-green-700 dark:text-green-400',
        border: 'border-green-200 dark:border-green-800',
        activeBg: 'bg-green-600 dark:bg-green-500',
        gradient: 'from-green-500 to-green-600',
        icon: '⚙️',
    },
    RUN: {
        primary: '#06b6d4', // cyan-500
        background: '#ECFEFF', // cyan-50 (light background)
        bg: 'bg-cyan-50 dark:bg-cyan-900/20',
        text: 'text-cyan-700 dark:text-cyan-400',
        border: 'border-cyan-200 dark:border-cyan-800',
        activeBg: 'bg-cyan-600 dark:bg-cyan-500',
        gradient: 'from-cyan-500 to-cyan-600',
        icon: '🚀',
    },
    OBSERVE: {
        primary: '#6366f1', // indigo-500
        background: '#EEF2FF', // indigo-50 (light background)
        bg: 'bg-indigo-50 dark:bg-indigo-900/20',
        text: 'text-indigo-700 dark:text-indigo-400',
        border: 'border-indigo-200 dark:border-indigo-800',
        activeBg: 'bg-indigo-600 dark:bg-indigo-500',
        gradient: 'from-indigo-500 to-indigo-600',
        icon: '📊',
    },
    IMPROVE: {
        primary: '#ec4899', // rose-500
        background: '#FDF2F8', // rose-50 (light background)
        bg: 'bg-rose-50 dark:bg-rose-900/20',
        text: 'text-rose-700 dark:text-rose-400',
        border: 'border-rose-200 dark:border-rose-800',
        activeBg: 'bg-rose-600 dark:bg-rose-500',
        gradient: 'from-rose-500 to-rose-600',
        icon: '🔄',
    },
} as const;

/** @deprecated Use PHASE_COLORS with uppercase keys (e.g., PHASE_COLORS.INTENT) */
export const LEGACY_PHASE_COLORS = {
    intent: PHASE_COLORS.INTENT,
    shape: PHASE_COLORS.SHAPE,
    validate: PHASE_COLORS.VALIDATE,
    generate: PHASE_COLORS.GENERATE,
    run: PHASE_COLORS.RUN,
    observe: PHASE_COLORS.OBSERVE,
    improve: PHASE_COLORS.IMPROVE,
} as const;

// ============================================================================
// Interaction & Hover Effects
// ============================================================================

/**
 * Standardized hover and interaction patterns
 */
export const INTERACTIONS = {
    /** Lift effect - subtle elevation on hover */
    hoverLift: 'hover:shadow-md hover:-translate-y-0.5 transition-all duration-200',
    /** Highlight effect - background change on hover */
    hoverHighlight: 'hover:bg-grey-100 dark:hover:bg-grey-800 transition-colors duration-200',
    /** Border highlight - border emphasis on hover */
    hoverBorder: 'hover:border-primary-400 transition-colors duration-200',
    /** Focus ring for keyboard navigation */
    focusRing: 'focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 focus:outline-none',
    /** Focus visible (keyboard only) */
    focusVisible: 'focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2 focus-visible:outline-none',
    /** Scale effect on hover */
    hoverScale: 'hover:scale-105 transition-transform duration-200',
    /** Opacity change on hover */
    hoverOpacity: 'hover:opacity-80 transition-opacity duration-200',
} as const;

// ============================================================================
// Typography Scale
// ============================================================================

/**
 * Typography hierarchy for consistent text styling
 */
export const TYPOGRAPHY = {
    /** Panel header - 14px bold */
    panelHeader: 'text-sm font-bold',
    /** Section title - 16px semibold */
    sectionTitle: 'text-base font-semibold',
    /** Card title - 14px medium */
    cardTitle: 'text-sm font-medium',
    /** Body text - 14px normal */
    body: 'text-sm',
    /** Secondary body - 14px with muted color */
    bodySecondary: 'text-sm text-text-secondary',
    /** Caption - 12px muted */
    caption: 'text-xs text-text-secondary',
    /** Label - 12px medium uppercase */
    label: 'text-xs font-medium uppercase tracking-wide',
    /** Code - monospace */
    code: 'font-mono text-sm',
} as const;

// ============================================================================
// Tooltip & UI Delays
// ============================================================================

/**
 * Consistent timing for user interactions
 */
export const TIMING = {
    /** Tooltip appearance delay - 500ms */
    tooltipDelay: 500,
    /** Transition duration - 200ms */
    transitionMs: 200,
    /** Hover effect delay - 100ms */
    hoverDelayMs: 100,
    /** Short animation - 150ms */
    shortAnimationMs: 150,
    /** Medium animation - 300ms */
    mediumAnimationMs: 300,
} as const;

// ============================================================================
// Type Exports
// ============================================================================

export type SpacingKey = keyof typeof SPACING;
export type ButtonSize = keyof typeof BUTTON;
export type RadiusKey = keyof typeof RADIUS;
export type TransitionKey = keyof typeof TRANSITIONS;
export type CanvasMode = keyof typeof MODE_COLORS;
export type AbstractionLevel = keyof typeof LEVEL_COLORS;
export type LifecyclePhase = keyof typeof PHASE_COLORS;
