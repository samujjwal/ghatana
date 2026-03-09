/**
 * Basic UI Components
 *
 * Provides minimal UI components for the Software Org application.
 * These are temporary placeholders while the @ghatana/ui library is being integrated.
 *
 * @doc.type components
 * @doc.purpose Basic UI building blocks
 * @doc.layer product
 * @doc.pattern Component Library
 */

import React from 'react';
import clsx from 'clsx';

// ============================================
// Card Component
// ============================================

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
    children?: React.ReactNode;
    className?: string;
    padded?: boolean | 'false';
    fullWidth?: boolean;
}

export const Card: React.FC<CardProps> = ({
    children,
    className,
    padded = true,
    fullWidth,
    ...props
}) => {
    // Filter out custom props before spreading to DOM
    const { padded: _, fullWidth: __, ...domProps } = { padded, fullWidth, ...props };

    return (
        <div
            className={clsx(
                'rounded-lg border border-slate-200 bg-white shadow-sm',
                'dark:border-[#30363d] dark:bg-[#161b22]',
                padded !== false && 'p-4',
                fullWidth && 'w-full',
                className
            )}
            {...domProps}
        >
            {children}
        </div>
    );
};

// ============================================
// Box Component
// ============================================

export interface BoxProps extends React.HTMLAttributes<HTMLDivElement> {
    children?: React.ReactNode;
    className?: string;
}

export const Box: React.FC<BoxProps> = ({ children, className, ...props }) => (
    <div className={clsx('flex flex-col', className)} {...props}>
        {children}
    </div>
);

// ============================================
// Button Component
// ============================================

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
    size?: 'sm' | 'md' | 'lg';
    children?: React.ReactNode;
    tone?: 'success' | 'warning' | 'danger' | 'neutral';
    fullWidth?: boolean;
}

export const Button: React.FC<ButtonProps> = ({
    variant = 'primary',
    size = 'md',
    tone,
    fullWidth,
    className,
    children,
    ...props
}) => {
    const baseStyles =
        'inline-flex items-center justify-center font-medium rounded-lg transition-colors';

    const variantStyles = {
        primary: 'bg-[#238636] text-white hover:bg-[#2ea043] dark:bg-[#238636] dark:hover:bg-[#2ea043]',
        secondary:
            'bg-slate-200 text-slate-900 hover:bg-slate-300 dark:bg-[#21262d] dark:text-[#e6edf3] dark:hover:bg-[#30363d]',
        outline:
            'border border-slate-300 text-slate-900 hover:bg-slate-50 dark:border-[#30363d] dark:text-[#e6edf3] dark:hover:bg-[#21262d]',
        ghost: 'text-slate-900 hover:bg-slate-100 dark:text-[#e6edf3] dark:hover:bg-[#21262d]',
    };

    // Support tone prop for backward compatibility
    const toneStyles = {
        success: 'bg-green-600 text-white hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-800',
        warning: 'bg-yellow-600 text-white hover:bg-yellow-700 dark:bg-yellow-700 dark:hover:bg-yellow-800',
        danger: 'bg-red-600 text-white hover:bg-red-700 dark:bg-red-700 dark:hover:bg-red-800',
        neutral: 'bg-slate-600 text-white hover:bg-slate-700 dark:bg-neutral-700 dark:hover:bg-slate-800',
    };

    const sizeStyles = {
        sm: 'px-3 py-1.5 text-sm',
        md: 'px-4 py-2 text-base',
        lg: 'px-6 py-3 text-lg',
    };

    // Use tone if provided, otherwise use variant
    const activeStyle = tone ? toneStyles[tone as keyof typeof toneStyles] : variantStyles[variant as keyof typeof variantStyles];

    // Filter out custom props before spreading to DOM
    const { tone: _, fullWidth: __, variant: ___, size: ____, ...domProps } = { tone, fullWidth, variant, size, ...props };

    return (
        <button
            className={clsx(baseStyles, activeStyle, sizeStyles[size], fullWidth && 'w-full', className)}
            {...domProps}
        >
            {children}
        </button>
    );
};

// ============================================
// Badge Component
// ============================================

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
    variant?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
    tone?: 'positive' | 'neutral' | 'warning' | 'negative';
    children?: React.ReactNode;
}

export const Badge: React.FC<BadgeProps> = ({
    variant = 'primary',
    tone,
    className,
    children,
    ...props
}) => {
    // Support both variant and tone props for backward compatibility
    const finalVariant = tone ? tone.toLowerCase().replace('positive', 'success').replace('negative', 'danger') : variant;

    const variantStyles = {
        primary: 'bg-blue-100 text-blue-800 dark:bg-[#388bfd26] dark:text-[#58a6ff]',
        success: 'bg-green-100 text-green-800 dark:bg-[#23863626] dark:text-[#3fb950]',
        warning: 'bg-amber-100 text-amber-800 dark:bg-[#d2992226] dark:text-[#d29922]',
        danger: 'bg-red-100 text-red-800 dark:bg-[#f8514926] dark:text-[#f85149]',
        neutral: 'bg-slate-100 text-slate-800 dark:bg-[#30363d] dark:text-[#8b949e]',
    };

    // Filter out custom props before spreading to DOM
    const { tone: _, variant: __, ...domProps } = { tone, variant, ...props };

    return (
        <span
            className={clsx(
                'inline-block px-2.5 py-0.5 text-xs font-semibold rounded-full',
                variantStyles[finalVariant as keyof typeof variantStyles] || variantStyles.primary,
                className
            )}
            {...domProps}
        >
            {children}
        </span>
    );
};

// ============================================
// KpiCard Component
// ============================================

export interface KpiCardProps extends React.HTMLAttributes<HTMLDivElement> {
    title: string;
    value: string | number;
    subtitle?: string;
    trend?: 'up' | 'down' | 'neutral';
    trendValue?: string;
    icon?: React.ComponentType<{ className?: string }>;
    /** Optional style variant (currently ignored; accepted for compatibility). */
    variant?: string;
    children?: React.ReactNode;
}

export const KpiCard: React.FC<KpiCardProps> = ({
    title,
    value,
    subtitle,
    trend,
    trendValue,
    icon: Icon,
    className,
    children,
    ...props
}) => (
    <Card className={clsx('min-w-48', className)} {...props}>
        <div className="space-y-2">
            <p className="text-sm font-medium text-slate-600 dark:text-[#8b949e]">{title}</p>
            <div className="flex items-baseline justify-between">
                <p className="text-2xl font-bold text-slate-900 dark:text-[#e6edf3]">{value}</p>
                {Icon ? (
                    <Icon className="h-5 w-5 text-slate-500 dark:text-[#8b949e]" />
                ) : null}
                {trend && trendValue && (
                    <span
                        className={clsx('text-xs font-semibold', {
                            'text-green-600 dark:text-[#3fb950]': trend === 'up',
                            'text-red-600 dark:text-[#f85149]': trend === 'down',
                            'text-slate-600 dark:text-[#8b949e]': trend === 'neutral',
                        })}
                    >
                        {trend === 'up' && '↑'} {trend === 'down' && '↓'} {trendValue}
                    </span>
                )}
            </div>
            {subtitle && <p className="text-xs text-slate-500 dark:text-[#6e7681]">{subtitle}</p>}
        </div>
        {children}
    </Card>
);

// ============================================
// DashboardLayout Component
// ============================================

export interface DashboardLayoutProps extends React.HTMLAttributes<HTMLDivElement> {
    children?: React.ReactNode;
    sidebar?: React.ReactNode;
    header?: React.ReactNode;
    // Custom layout props (do not forward to DOM)
    sidebarCollapsed?: boolean;
    onSidebarToggle?: (collapsed: boolean) => void;
    backgroundColor?: string; // expects tailwind class like 'bg-slate-50' or 'bg-slate-900'
    padding?: 'sm' | 'md' | 'lg' | string;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({
    children,
    sidebar,
    header,
    className,
    sidebarCollapsed, // intentionally extracted to avoid DOM warning
    onSidebarToggle, // intentionally extracted to avoid DOM warning
    backgroundColor,
    padding = 'md',
    ...props
}) => {
    // Map padding token to tailwind class
    const paddingClass =
        padding === 'sm' ? 'p-4' : padding === 'lg' ? 'p-8' : typeof padding === 'string' && padding.startsWith('p') ? padding : 'p-6';

    const rootClass = clsx(
        'flex h-screen',
        backgroundColor || 'bg-slate-50 dark:bg-neutral-800',
        className
    );

    // Don't forward custom props (`sidebarCollapsed`, `onSidebarToggle`, `backgroundColor`, `padding`) to DOM
    const { sidebarCollapsed: _sc, onSidebarToggle: _ost, backgroundColor: _bg, padding: _pd, ...domProps } = {
        sidebarCollapsed,
        onSidebarToggle,
        backgroundColor,
        padding,
        ...props,
    } as any;

    return (
        <div className={rootClass} {...domProps}>
            {sidebar && <aside className="w-64 border-r border-slate-200 dark:border-[#30363d]">{sidebar}</aside>}
            <div className="flex flex-1 flex-col">
                {header && <header className="border-b border-slate-200 dark:border-[#30363d]">{header}</header>}
                <main className={clsx('flex-1 overflow-auto', paddingClass)}>{children}</main>
            </div>
        </div>
    );
};
