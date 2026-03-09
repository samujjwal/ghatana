import { Link } from 'react-router';
import { useMemo } from 'react';

/**
 * Navigation hint configuration
 */
export interface NavigationHintConfig {
    /** Unique identifier */
    id: string;
    /** Display label */
    label: string;
    /** Target href */
    href: string;
    /** Icon (emoji or component) */
    icon?: string;
    /** Description text */
    description?: string;
    /** Badge count (optional) */
    badge?: number;
    /** Whether this is a primary action */
    primary?: boolean;
}

/**
 * NavigationHint Props
 */
export interface NavigationHintProps {
    /** Hint configuration */
    hint: NavigationHintConfig;
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Variant style */
    variant?: 'default' | 'subtle' | 'prominent';
    /** Additional CSS classes */
    className?: string;
}

/**
 * NavigationHint - Contextual navigation suggestion
 *
 * <p><b>Purpose</b><br>
 * Provides contextual navigation hints to guide users to related
 * pages or actions based on their current context.
 *
 * @doc.type component
 * @doc.purpose Contextual navigation hint
 * @doc.layer shared
 * @doc.pattern Navigation Component
 */
export function NavigationHint({
    hint,
    size = 'md',
    variant = 'default',
    className = '',
}: NavigationHintProps) {
    const sizeClasses = {
        sm: 'px-2 py-1 text-xs gap-1.5',
        md: 'px-3 py-2 text-sm gap-2',
        lg: 'px-4 py-3 text-base gap-3',
    };

    const variantClasses = {
        default: 'bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 hover:bg-slate-50 dark:hover:bg-slate-700',
        subtle: 'bg-slate-50 dark:bg-neutral-800/50 hover:bg-slate-100 dark:hover:bg-slate-800',
        prominent: 'bg-blue-50 dark:bg-indigo-600/30 border border-blue-200 dark:border-blue-800 hover:bg-blue-100 dark:hover:bg-blue-900/30',
    };

    return (
        <Link
            to={hint.href}
            className={`
                inline-flex items-center rounded-lg font-medium transition-colors
                ${sizeClasses[size]}
                ${variantClasses[variant]}
                text-slate-700 dark:text-neutral-300
                ${className}
            `}
        >
            {hint.icon && <span>{hint.icon}</span>}
            <span>{hint.label}</span>
            {hint.badge !== undefined && hint.badge > 0 && (
                <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full text-[10px] font-semibold bg-blue-600 text-white">
                    {hint.badge > 99 ? '99+' : hint.badge}
                </span>
            )}
        </Link>
    );
}

/**
 * NavigationHintGroup Props
 */
export interface NavigationHintGroupProps {
    /** Group title */
    title?: string;
    /** Hints to display */
    hints: NavigationHintConfig[];
    /** Layout direction */
    direction?: 'horizontal' | 'vertical';
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Additional CSS classes */
    className?: string;
}

/**
 * NavigationHintGroup - Group of navigation hints
 */
export function NavigationHintGroup({
    title,
    hints,
    direction = 'horizontal',
    size = 'md',
    className = '',
}: NavigationHintGroupProps) {
    const directionClasses = {
        horizontal: 'flex flex-wrap gap-2',
        vertical: 'flex flex-col gap-1',
    };

    if (hints.length === 0) {
        return null;
    }

    return (
        <div className={className}>
            {title && (
                <div className="text-xs font-medium text-slate-500 dark:text-neutral-400 mb-2">
                    {title}
                </div>
            )}
            <div className={directionClasses[direction]}>
                {hints.map((hint) => (
                    <NavigationHint
                        key={hint.id}
                        hint={hint}
                        size={size}
                        variant={hint.primary ? 'prominent' : 'default'}
                    />
                ))}
            </div>
        </div>
    );
}

/**
 * Contextual hints based on current page/context
 */
export interface ContextualHintsProps {
    /** Current page context */
    context: 'home' | 'devsecops' | 'org' | 'reports' | 'security' | 'monitoring';
    /** Current persona (optional) */
    personaId?: string;
    /** Additional hints to include */
    additionalHints?: NavigationHintConfig[];
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Additional CSS classes */
    className?: string;
}

/**
 * ContextualHints - Auto-generated hints based on context
 */
export function ContextualHints({
    context,
    personaId,
    additionalHints = [],
    size = 'sm',
    className = '',
}: ContextualHintsProps) {
    const hints = useMemo(() => {
        const baseHints: NavigationHintConfig[] = [];

        switch (context) {
            case 'home':
                baseHints.push(
                    { id: 'devsecops', label: 'DevSecOps Board', href: '/devsecops/board', icon: '🔄' },
                    { id: 'org', label: 'Org Builder', href: '/org', icon: '🏗️' },
                );
                break;
            case 'devsecops':
                baseHints.push(
                    { id: 'home', label: 'Home', href: '/', icon: '🏠' },
                    { id: 'reports', label: 'Reports', href: '/reports', icon: '📈' },
                    { id: 'org', label: 'Org Builder', href: '/org', icon: '🏗️' },
                );
                break;
            case 'org':
                baseHints.push(
                    { id: 'devsecops', label: 'DevSecOps Board', href: '/devsecops/board', icon: '🔄' },
                    { id: 'workflows', label: 'Workflows', href: '/workflows', icon: '🔗' },
                    { id: 'personas', label: 'Personas', href: '/personas', icon: '👤' },
                );
                break;
            case 'reports':
                baseHints.push(
                    { id: 'devsecops', label: 'DevSecOps Board', href: '/devsecops/board', icon: '🔄' },
                    { id: 'dashboard', label: 'Control Tower', href: '/dashboard', icon: '📊' },
                );
                break;
            case 'security':
                baseHints.push(
                    { id: 'devsecops', label: 'DevSecOps Board', href: '/devsecops/board?persona=security', icon: '🔄', primary: true },
                    { id: 'reports', label: 'Compliance Reports', href: '/reports?type=compliance', icon: '📈' },
                );
                break;
            case 'monitoring':
                baseHints.push(
                    { id: 'devsecops', label: 'DevSecOps Board', href: '/devsecops/board?persona=sre', icon: '🔄', primary: true },
                    { id: 'dashboard', label: 'Control Tower', href: '/dashboard', icon: '📊' },
                );
                break;
        }

        // Add persona-specific hints
        if (personaId === 'sre') {
            baseHints.push({ id: 'monitor', label: 'Real-Time Monitor', href: '/realtime-monitor', icon: '⏱️' });
        } else if (personaId === 'security') {
            baseHints.push({ id: 'security', label: 'Security Center', href: '/security', icon: '🔒' });
        }

        return [...baseHints, ...additionalHints];
    }, [context, personaId, additionalHints]);

    return (
        <NavigationHintGroup
            title="Related"
            hints={hints}
            direction="horizontal"
            size={size}
            className={className}
        />
    );
}

export default NavigationHint;
