/**
 * Entity Detail Page Component
 *
 * Reusable detail page component for viewing and editing configuration entities.
 * Provides consistent layout, styling, and interaction patterns across all entity types.
 *
 * @doc.type component
 * @doc.purpose Reusable entity detail view/edit page
 * @doc.layer shared
 */

import { useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import {
    ArrowLeft,
    Edit2,
    Save,
    X,
    Trash2,
    Copy,
    ExternalLink,
    MoreVertical,
    Check,
} from 'lucide-react';
import { components, typography, getEntityColors, cn } from '@/lib/theme';

// ============================================================================
// Types
// ============================================================================

export interface EntityField {
    key: string;
    label: string;
    value: string | number | boolean | string[] | null | undefined;
    type?: 'text' | 'number' | 'boolean' | 'array' | 'code' | 'link' | 'badge' | 'date';
    editable?: boolean;
    required?: boolean;
    description?: string;
    linkTo?: string;
}

export interface EntitySection {
    id: string;
    title: string;
    description?: string;
    fields?: EntityField[];
    content?: ReactNode;
}

export interface EntityAction {
    id: string;
    label: string;
    icon?: ReactNode;
    onClick: () => void;
    variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
    disabled?: boolean;
}

export interface RelatedEntity {
    id: string;
    name: string;
    type: string;
    href: string;
    status?: string;
}

export interface EntityDetailPageProps {
    // Entity info
    entityType: string;
    entityId: string;
    title: string;
    subtitle?: string;
    description?: string;
    status?: string;
    icon?: ReactNode;

    // Navigation
    backHref: string;
    backLabel?: string;

    // Content
    sections: EntitySection[];
    relatedEntities?: RelatedEntity[];

    // Actions
    actions?: EntityAction[];
    onEdit?: () => void;
    onDelete?: () => void;
    onSave?: (data: Record<string, unknown>) => void;

    // State
    isLoading?: boolean;
    isEditing?: boolean;
    error?: string | null;
}

// ============================================================================
// Sub-components
// ============================================================================

function FieldValue({ field }: { field: EntityField }) {
    const { value, type, linkTo } = field;

    if (value === null || value === undefined) {
        return <span className="text-gray-400 dark:text-gray-500 italic">Not set</span>;
    }

    switch (type) {
        case 'boolean':
            return (
                <span className={cn(
                    'inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium',
                    value ? 'bg-green-100 dark:bg-green-900/50 text-green-700 dark:text-green-300'
                        : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
                )}>
                    {value ? <Check className="w-3 h-3" /> : <X className="w-3 h-3" />}
                    {value ? 'Yes' : 'No'}
                </span>
            );

        case 'array':
            if (!Array.isArray(value) || value.length === 0) {
                return <span className="text-gray-400 dark:text-gray-500 italic">None</span>;
            }
            return (
                <div className="flex flex-wrap gap-1.5">
                    {value.map((item, i) => (
                        <span
                            key={i}
                            className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300"
                        >
                            {String(item)}
                        </span>
                    ))}
                </div>
            );

        case 'code':
            return (
                <code className="px-2 py-1 rounded bg-gray-100 dark:bg-gray-800 text-sm font-mono text-gray-800 dark:text-gray-200">
                    {String(value)}
                </code>
            );

        case 'link':
            return (
                <Link
                    to={linkTo || '#'}
                    className={cn(typography.link, 'inline-flex items-center gap-1')}
                >
                    {String(value)}
                    <ExternalLink className="w-3 h-3" />
                </Link>
            );

        case 'badge':
            return (
                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300">
                    {String(value)}
                </span>
            );

        case 'date':
            return (
                <span className="text-gray-900 dark:text-gray-100">
                    {new Date(String(value)).toLocaleString()}
                </span>
            );

        default:
            return <span className="text-gray-900 dark:text-gray-100">{String(value)}</span>;
    }
}

function SectionCard({ section }: { section: EntitySection }) {
    return (
        <div className={cn(components.card.base, components.card.padding)}>
            <div className="mb-4">
                <h3 className={typography.h4}>{section.title}</h3>
                {section.description && (
                    <p className={cn(typography.small, 'mt-1')}>{section.description}</p>
                )}
            </div>

            {section.content ? (
                section.content
            ) : section.fields ? (
                <dl className="space-y-4">
                    {section.fields.map((field) => (
                        <div key={field.key} className="grid grid-cols-3 gap-4">
                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400">
                                {field.label}
                                {field.required && <span className="text-red-500 ml-0.5">*</span>}
                            </dt>
                            <dd className="col-span-2">
                                <FieldValue field={field} />
                                {field.description && (
                                    <p className="mt-1 text-xs text-gray-400 dark:text-gray-500">
                                        {field.description}
                                    </p>
                                )}
                            </dd>
                        </div>
                    ))}
                </dl>
            ) : null}
        </div>
    );
}

function RelatedEntitiesCard({ entities }: { entities: RelatedEntity[] }) {
    if (!entities || entities.length === 0) return null;

    return (
        <div className={cn(components.card.base, components.card.padding)}>
            <h3 className={cn(typography.h4, 'mb-4')}>Related Entities</h3>
            <div className="space-y-3">
                {entities.map((entity) => {
                    const relatedColors = getEntityColors(entity.type);
                    return (
                        <Link
                            key={entity.id}
                            to={entity.href}
                            className={cn(
                                'flex items-center justify-between p-3 rounded-lg border transition-all',
                                'hover:shadow-sm',
                                relatedColors.bg,
                                relatedColors.border
                            )}
                        >
                            <div className="flex items-center gap-3">
                                <span className={cn(
                                    'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium',
                                    relatedColors.badge
                                )}>
                                    {entity.type}
                                </span>
                                <span className="font-medium text-gray-900 dark:text-gray-100">
                                    {entity.name}
                                </span>
                            </div>
                            {entity.status && (
                                <span className={cn(
                                    'text-xs font-medium',
                                    entity.status === 'active' ? 'text-green-600 dark:text-green-400' : 'text-gray-500'
                                )}>
                                    {entity.status}
                                </span>
                            )}
                        </Link>
                    );
                })}
            </div>
        </div>
    );
}

// ============================================================================
// Main Component
// ============================================================================

export function EntityDetailPage({
    entityType,
    entityId,
    title,
    subtitle,
    description,
    status,
    icon,
    backHref,
    backLabel = 'Back',
    sections,
    relatedEntities,
    actions = [],
    onEdit,
    onDelete,
    onSave,
    isLoading = false,
    isEditing = false,
    error,
}: EntityDetailPageProps) {
    const navigate = useNavigate();
    const [showActions, setShowActions] = useState(false);
    const [copied, setCopied] = useState(false);

    const entityColors = getEntityColors(entityType);

    const handleCopyId = async () => {
        await navigator.clipboard.writeText(entityId);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    if (isLoading) {
        return (
            <div className={components.page.wrapper}>
                <div className={components.page.container}>
                    <div className="animate-pulse space-y-6">
                        <div className="h-8 w-48 bg-gray-200 dark:bg-gray-700 rounded" />
                        <div className="h-4 w-96 bg-gray-200 dark:bg-gray-700 rounded" />
                        <div className="h-64 bg-gray-200 dark:bg-gray-700 rounded-xl" />
                    </div>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className={components.page.wrapper}>
                <div className={components.page.container}>
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-6">
                        <h2 className="text-lg font-semibold text-red-800 dark:text-red-200 mb-2">
                            Error Loading {entityType}
                        </h2>
                        <p className="text-red-700 dark:text-red-300">{error}</p>
                        <Link
                            to={backHref}
                            className={cn(components.button.secondary, 'mt-4')}
                        >
                            <ArrowLeft className="w-4 h-4" />
                            {backLabel}
                        </Link>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className={components.page.wrapper}>
            <div className={components.page.container}>
                {/* Header */}
                <div className="mb-8">
                    {/* Breadcrumb */}
                    <Link
                        to={backHref}
                        className={cn(
                            'inline-flex items-center gap-1.5 text-sm mb-4',
                            typography.link
                        )}
                    >
                        <ArrowLeft className="w-4 h-4" />
                        {backLabel}
                    </Link>

                    {/* Title row */}
                    <div className="flex items-start justify-between">
                        <div className="flex items-start gap-4">
                            {/* Icon */}
                            {icon && (
                                <div className={cn(
                                    'flex-shrink-0 w-12 h-12 rounded-xl flex items-center justify-center text-white',
                                    entityColors.icon
                                )}>
                                    {icon}
                                </div>
                            )}

                            <div>
                                {/* Entity type badge */}
                                <span className={cn(
                                    'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium mb-2',
                                    entityColors.badge
                                )}>
                                    {entityType}
                                </span>

                                {/* Title */}
                                <h1 className={typography.h1}>{title}</h1>

                                {/* Subtitle / ID */}
                                <div className="flex items-center gap-3 mt-2">
                                    {subtitle && (
                                        <span className={typography.small}>{subtitle}</span>
                                    )}
                                    <button
                                        onClick={handleCopyId}
                                        className="inline-flex items-center gap-1.5 px-2 py-1 rounded text-xs font-mono bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                                    >
                                        {copied ? <Check className="w-3 h-3 text-green-500" /> : <Copy className="w-3 h-3" />}
                                        {entityId}
                                    </button>
                                    {status && (
                                        <span className={cn(
                                            'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
                                            status === 'active' || status === 'healthy'
                                                ? 'bg-green-100 dark:bg-green-900/50 text-green-700 dark:text-green-300'
                                                : status === 'warning' || status === 'degraded'
                                                    ? 'bg-amber-100 dark:bg-amber-900/50 text-amber-700 dark:text-amber-300'
                                                    : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
                                        )}>
                                            {status}
                                        </span>
                                    )}
                                </div>

                                {/* Description */}
                                {description && (
                                    <p className={cn(typography.body, 'mt-3 max-w-2xl')}>
                                        {description}
                                    </p>
                                )}
                            </div>
                        </div>

                        {/* Actions */}
                        <div className="flex items-center gap-2">
                            {isEditing ? (
                                <>
                                    <button
                                        onClick={() => onSave?.({})}
                                        className={components.button.primary}
                                    >
                                        <Save className="w-4 h-4" />
                                        Save Changes
                                    </button>
                                    <button
                                        onClick={() => navigate(0)}
                                        className={components.button.ghost}
                                    >
                                        Cancel
                                    </button>
                                </>
                            ) : (
                                <>
                                    {onEdit && (
                                        <button
                                            onClick={onEdit}
                                            className={components.button.secondary}
                                        >
                                            <Edit2 className="w-4 h-4" />
                                            Edit
                                        </button>
                                    )}

                                    {actions.map((action) => (
                                        <button
                                            key={action.id}
                                            onClick={action.onClick}
                                            disabled={action.disabled}
                                            className={components.button[action.variant || 'secondary']}
                                        >
                                            {action.icon}
                                            {action.label}
                                        </button>
                                    ))}

                                    {onDelete && (
                                        <div className="relative">
                                            <button
                                                onClick={() => setShowActions(!showActions)}
                                                className={components.button.icon}
                                            >
                                                <MoreVertical className="w-5 h-5" />
                                            </button>

                                            {showActions && (
                                                <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-slate-800 rounded-lg shadow-lg border border-gray-200 dark:border-slate-700 py-1 z-10">
                                                    <button
                                                        onClick={() => {
                                                            setShowActions(false);
                                                            onDelete();
                                                        }}
                                                        className="w-full flex items-center gap-2 px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                        Delete {entityType}
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </>
                            )}
                        </div>
                    </div>
                </div>

                {/* Content */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Main content */}
                    <div className="lg:col-span-2 space-y-6">
                        {sections.map((section) => (
                            <SectionCard
                                key={section.id}
                                section={section}
                            />
                        ))}
                    </div>

                    {/* Sidebar */}
                    <div className="space-y-6">
                        {relatedEntities && relatedEntities.length > 0 && (
                            <RelatedEntitiesCard
                                entities={relatedEntities}
                            />
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

export default EntityDetailPage;
