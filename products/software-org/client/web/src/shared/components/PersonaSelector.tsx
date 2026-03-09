import { UserRole, PERSONA_CONFIGS, type PersonaConfig } from '@/config/personaConfig';
import { clsx } from 'clsx';
import { Tooltip } from './Tooltip';

/**
 * PersonaSelector - Development tool for selecting user personas
 *
 * <p><b>Purpose</b><br>
 * Allows developers/testers to quickly switch between personas during testing
 * and development. This component will be hidden in production when proper
 * authentication is implemented.
 *
 * <p><b>Features</b><br>
 * - Visual persona selector with 4 roles (admin, lead, engineer, viewer)
 * - Icon and label for each persona
 * - Clear visual feedback for active persona
 * - Can be positioned fixed or inline
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <PersonaSelector
 *   selectedPersona="engineer"
 *   onSelectPersona={(role) => updatePersona(role)}
 *   showLabel={true}
 *   variant="fixed"
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Development persona selector tool
 * @doc.layer product
 * @doc.pattern Selection Component
 */

interface PersonaSelectorProps {
    selectedPersona: UserRole;
    onSelectPersona: (role: UserRole) => void;
    showLabel?: boolean;
    variant?: 'fixed' | 'inline' | 'dropdown';
    className?: string;
}

const ROLE_META: Partial<Record<UserRole, { icon: string; color: string }>> = {
    admin: {
        icon: '👑',
        color: 'bg-red-100 dark:bg-red-900 text-red-700 dark:text-red-200 border-red-300 dark:border-red-700',
    },
    lead: {
        icon: '👥',
        color: 'bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-200 border-blue-300 dark:border-blue-700',
    },
    engineer: {
        icon: '🔧',
        color: 'bg-green-100 dark:bg-green-900 text-green-700 dark:text-green-200 border-green-300 dark:border-green-700',
    },
    viewer: {
        icon: '👁️',
        color: 'bg-purple-100 dark:bg-purple-900 text-purple-700 dark:text-purple-200 border-purple-300 dark:border-purple-700',
    },
};

const PERSONA_OPTIONS: Array<{
    role: UserRole;
    label: string;
    icon: string;
    color: string;
    description: string;
}> = (Object.values(PERSONA_CONFIGS) as PersonaConfig[]).map((config) => {
    const meta = ROLE_META[config.role] ?? {
        icon: '👤',
        color: 'bg-slate-100 dark:bg-neutral-700 text-slate-600 dark:text-neutral-300 border-slate-300 dark:border-neutral-600',
    };

    return {
        role: config.role,
        label: config.displayName,
        icon: meta.icon,
        color: meta.color,
        description: config.tagline,
    };
});

export function PersonaSelector({
    selectedPersona,
    onSelectPersona,
    showLabel = true,
    variant = 'fixed',
    className = '',
}: PersonaSelectorProps) {
    if (variant === 'fixed') {
        return (
            <div className={clsx(
                'fixed top-4 right-4 z-40 bg-white dark:bg-neutral-800 rounded-lg shadow-lg border border-slate-200 dark:border-neutral-600 p-4',
                className
            )}>
                <div className="mb-2">
                    <p className="text-xs font-semibold text-slate-600 dark:text-neutral-300 uppercase tracking-wider">
                        Dev: Select Persona
                    </p>
                </div>
                <div className="flex gap-2 flex-wrap">
                    {PERSONA_OPTIONS.map((option) => (
                        <Tooltip
                            key={option.role}
                            content={option.description}
                            position="top"
                        >
                            <button
                                type="button"
                                onClick={() => onSelectPersona(option.role)}
                                className={clsx(
                                    'px-3 py-2 rounded-md font-medium text-sm transition-all border-2 hover:shadow-md',
                                    selectedPersona === option.role
                                        ? clsx(option.color, 'border-current scale-105')
                                        : 'bg-slate-100 dark:bg-neutral-700 text-slate-600 dark:text-neutral-400 border-slate-300 dark:border-neutral-600 hover:bg-slate-200 dark:hover:bg-slate-600'
                                )}
                                aria-label={`Switch to ${option.label} persona`}
                            >
                                <span>{option.icon}</span>
                                {showLabel && <span className="ml-1">{option.label}</span>}
                            </button>
                        </Tooltip>
                    ))}
                </div>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-2">
                    {selectedPersona === 'admin' && '🔐 Admin - Full platform access, security & compliance focus'}
                    {selectedPersona === 'lead' && '👥 Lead - Team management, approvals, workflow oversight'}
                    {selectedPersona === 'engineer' && '🔧 Engineer - Workflow creation, deployments, testing'}
                    {selectedPersona === 'viewer' && '👁️ Viewer - Dashboards, reports, read-only access'}
                </p>
            </div>
        );
    }

    if (variant === 'dropdown') {
        return (
            <div className={clsx('inline-block relative', className)}>
                <select
                    value={selectedPersona}
                    onChange={(e) => onSelectPersona(e.target.value as UserRole)}
                    className="px-3 py-2 rounded-md font-medium text-sm bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100 border border-slate-300 dark:border-neutral-600 hover:border-slate-400 dark:hover:border-slate-500 cursor-pointer"
                >
                    {PERSONA_OPTIONS.map((option) => (
                        <option key={option.role} value={option.role}>
                            {option.icon} {option.label} - {option.description}
                        </option>
                    ))}
                </select>
            </div>
        );
    }

    // Default inline variant
    return (
        <div className={clsx('flex gap-2 flex-wrap items-center', className)}>
            {PERSONA_OPTIONS.map((option) => (
                <Tooltip
                    key={option.role}
                    content={option.description}
                    position="bottom"
                >
                    <button
                        type="button"
                        onClick={() => {
                            if (import.meta.env.DEV) {
                                console.debug('[PersonaSelector] Clicked persona:', option.role);
                            }
                            onSelectPersona(option.role);
                        }}
                        className={clsx(
                            'px-3 py-2 rounded-md font-medium text-sm transition-all border-2',
                            selectedPersona === option.role
                                ? clsx(option.color, 'border-current shadow-md')
                                : 'bg-slate-100 dark:bg-neutral-700 text-slate-600 dark:text-neutral-400 border-slate-300 dark:border-neutral-600 hover:bg-slate-200 dark:hover:bg-slate-600'
                        )}
                        aria-label={`Switch to ${option.label} persona`}
                    >
                        <span>{option.icon}</span>
                        {showLabel && <span className="ml-1">{option.label}</span>}
                    </button>
                </Tooltip>
            ))}
        </div>
    );
}

export type { PersonaSelectorProps };
