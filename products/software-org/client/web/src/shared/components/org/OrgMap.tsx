/**
 * OrgMap Component
 *
 * Organization structure map showing department hierarchy and relationships.
 * Displays departments as nodes with health status and interactive capabilities.
 *
 * @example
 * <OrgMap 
 *   departments={[
 *     { id: 'eng', name: 'Engineering', status: 'active', children: ['qa'] },
 *     { id: 'qa', name: 'QA', status: 'warning', parent: 'eng' }
 *   ]}
 *   onDepartmentClick={(id) => console.log(id)}
 * />
 *
 * @package @ghatana/software-org-web
 */

import React, { CSSProperties, useMemo } from 'react';
import { clsx } from 'clsx';

export interface OrgDepartment {
    /** Unique department identifier */
    id: string;
    /** Department name */
    name: string;
    /** Department status */
    status: 'active' | 'warning' | 'critical' | 'idle' | 'unknown';
    /** Optional parent department ID */
    parent?: string;
    /** Optional child department IDs */
    children?: string[];
    /** Optional department description */
    description?: string;
    /** Optional metadata for rendering */
    metadata?: Record<string, unknown>;
}

export interface OrgMapProps {
    /** List of departments to display */
    departments: OrgDepartment[];
    /** Optional callback when department is clicked */
    onDepartmentClick?: (deptId: string) => void;
    /** Optional callback when department is hovered */
    onDepartmentHover?: (deptId: string | null) => void;
    /** Optional selected department ID */
    selectedId?: string;
    /** Layout style: 'hierarchical' (top-down) or 'graph' (force-directed) */
    layout?: 'hierarchical' | 'graph';
    /** Whether to show connection lines */
    showConnections?: boolean;
    /** Optional CSS class */
    className?: string;
    /** Optional inline styles */
    style?: CSSProperties;
}

/**
 * Status color configuration for org map nodes.
 */
const STATUS_COLORS: Record<string, { bg: string; border: string; dot: string; hover: string }> = {
    active: {
        bg: 'bg-green-50 dark:bg-green-950',
        border: 'border-green-400 dark:border-green-600',
        dot: 'bg-green-500',
        hover: 'hover:bg-green-100 dark:hover:bg-green-900',
    },
    warning: {
        bg: 'bg-amber-50 dark:bg-amber-950',
        border: 'border-amber-400 dark:border-amber-600',
        dot: 'bg-amber-500',
        hover: 'hover:bg-amber-100 dark:hover:bg-amber-900',
    },
    critical: {
        bg: 'bg-red-50 dark:bg-red-950',
        border: 'border-red-400 dark:border-red-600',
        dot: 'bg-red-500',
        hover: 'hover:bg-red-100 dark:hover:bg-red-900',
    },
    idle: {
        bg: 'bg-gray-50 dark:bg-gray-900',
        border: 'border-gray-300 dark:border-gray-700',
        dot: 'bg-gray-400',
        hover: 'hover:bg-gray-100 dark:hover:bg-gray-800',
    },
    unknown: {
        bg: 'bg-slate-50 dark:bg-slate-950',
        border: 'border-slate-300 dark:border-slate-700',
        dot: 'bg-slate-400',
        hover: 'hover:bg-slate-100 dark:hover:bg-slate-900',
    },
};

/**
 * Helper: Build hierarchy structure from departments list.
 */
const buildHierarchy = (departments: OrgDepartment[]): Map<string, OrgDepartment[]> => {
    const hierarchy = new Map<string, OrgDepartment[]>();

    for (const dept of departments) {
        const parent = dept.parent || 'root';
        if (!hierarchy.has(parent)) {
            hierarchy.set(parent, []);
        }
        hierarchy.get(parent)!.push(dept);
    }

    return hierarchy;
};

/**
 * Hierarchical node component.
 */
interface DepartmentNodeProps {
    dept: OrgDepartment;
    isSelected: boolean;
    children?: OrgDepartment[];
    onDepartmentClick?: (deptId: string) => void;
    onDepartmentHover?: (deptId: string | null) => void;
}

const DepartmentNode: React.FC<DepartmentNodeProps> = ({
    dept,
    isSelected,
    children,
    onDepartmentClick,
    onDepartmentHover,
}) => {
    const config = STATUS_COLORS[dept.status] || STATUS_COLORS.unknown;

    return (
        <div className="flex flex-col items-center gap-3">
            {/* Department node card */}
            <div
                className={clsx(
                    'px-4 py-2 rounded-lg border-2 transition-all duration-200',
                    'cursor-pointer whitespace-nowrap text-center min-w-max',
                    config.bg,
                    config.border,
                    config.hover,
                    isSelected && 'ring-2 ring-blue-500 ring-offset-2'
                )}
                onClick={() => onDepartmentClick?.(dept.id)}
                onMouseEnter={() => onDepartmentHover?.(dept.id)}
                onMouseLeave={() => onDepartmentHover?.(null)}
                role="button"
                tabIndex={0}
                aria-label={`${dept.name} - Status: ${dept.status}`}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onDepartmentClick?.(dept.id);
                    }
                }}
            >
                <div className="flex items-center gap-2">
                    <div className={clsx('w-2.5 h-2.5 rounded-full flex-shrink-0', config.dot)} />
                    <div>
                        <div className="font-semibold text-sm">{dept.name}</div>
                        {dept.description && (
                            <div className="text-xs text-gray-600 dark:text-gray-400">{dept.description}</div>
                        )}
                    </div>
                </div>
            </div>

            {/* Connection line to children */}
            {children && children.length > 0 && (
                <>
                    <div className="w-0.5 h-4 bg-gray-300 dark:bg-gray-700" />

                    {/* Children container */}
                    <div className="flex gap-8 justify-center">
                        {children.map((child) => (
                            <DepartmentNode
                                key={child.id}
                                dept={child}
                                isSelected={isSelected}
                                onDepartmentClick={onDepartmentClick}
                                onDepartmentHover={onDepartmentHover}
                            />
                        ))}
                    </div>
                </>
            )}
        </div>
    );
};

/**
 * OrgMap: Visualizes organization structure with department hierarchy.
 *
 * Features:
 * - Hierarchical and graph-based layout options
 * - Status-based color coding for departments
 * - Interactive selection and hovering
 * - Connection lines showing parent-child relationships
 * - Keyboard navigation support
 * - Dark mode support
 * - Full accessibility (ARIA labels, keyboard support)
 *
 * @param props Component props
 * @returns JSX element
 */
export const OrgMap: React.FC<OrgMapProps> = ({
    departments,
    onDepartmentClick,
    onDepartmentHover,
    selectedId,
    layout = 'hierarchical',
    showConnections = true,
    className,
    style,
}) => {
    const hierarchy = useMemo(() => buildHierarchy(departments), [departments]);

    // Get root departments (no parent or parent is 'root')
    const roots = hierarchy.get('root') || [];

    if (departments.length === 0) {
        return (
            <div
                className={clsx(
                    'flex items-center justify-center p-8 rounded-lg border-2 border-dashed',
                    'text-gray-500 dark:text-gray-400',
                    className
                )}
                style={style}
            >
                No departments to display
            </div>
        );
    }

    if (layout === 'hierarchical') {
        return (
            <div
                className={clsx(
                    'p-6 rounded-lg bg-white dark:bg-slate-950',
                    'border border-gray-200 dark:border-gray-800',
                    'overflow-auto',
                    className
                )}
                style={style}
                role="region"
                aria-label="Organization map"
            >
                <div className="flex flex-col items-center gap-6">
                    {roots.map((root) => (
                        <DepartmentNode
                            key={root.id}
                            dept={root}
                            isSelected={selectedId === root.id}
                            children={hierarchy.get(root.id)}
                            onDepartmentClick={onDepartmentClick}
                            onDepartmentHover={onDepartmentHover}
                        />
                    ))}
                </div>
            </div>
        );
    }

    // Graph layout: simple grid for now
    return (
        <div
            className={clsx(
                'p-6 rounded-lg bg-white dark:bg-slate-950',
                'border border-gray-200 dark:border-gray-800',
                'grid grid-cols-2 md:grid-cols-3 gap-4',
                className
            )}
            style={style}
            role="region"
            aria-label="Organization map"
        >
            {departments.map((dept) => {
                const config = STATUS_COLORS[dept.status] || STATUS_COLORS.unknown;

                return (
                    <div
                        key={dept.id}
                        className={clsx(
                            'p-3 rounded-lg border-2 transition-all duration-200',
                            'cursor-pointer text-center',
                            config.bg,
                            config.border,
                            config.hover,
                            selectedId === dept.id && 'ring-2 ring-blue-500'
                        )}
                        onClick={() => onDepartmentClick?.(dept.id)}
                        onMouseEnter={() => onDepartmentHover?.(dept.id)}
                        onMouseLeave={() => onDepartmentHover?.(null)}
                        role="button"
                        tabIndex={0}
                        aria-label={`${dept.name} - Status: ${dept.status}`}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                onDepartmentClick?.(dept.id);
                            }
                        }}
                    >
                        <div className="flex items-center gap-2 justify-center mb-1">
                            <div className={clsx('w-2 h-2 rounded-full', config.dot)} />
                            <span className="font-semibold text-sm">{dept.name}</span>
                        </div>
                        {dept.description && (
                            <p className="text-xs text-gray-600 dark:text-gray-400">{dept.description}</p>
                        )}
                    </div>
                );
            })}
        </div>
    );
};

export default OrgMap;
