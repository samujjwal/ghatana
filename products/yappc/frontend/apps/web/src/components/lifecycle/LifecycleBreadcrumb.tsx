/**
 * Lifecycle Breadcrumb Component
 * 
 * Provides hierarchical navigation context for lifecycle phases and artifacts.
 * Shows: Project > Lifecycle > Phase > Artifact (if selected)
 * 
 * @doc.type component
 * @doc.purpose Navigation breadcrumb for lifecycle context
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from 'react';
import { ChevronRight, Home } from 'lucide-react';
import type { LifecyclePhase } from '@/shared/types/lifecycle';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

export interface BreadcrumbItem {
    label: string;
    icon?: React.ReactNode;
    onClick?: () => void;
    active?: boolean;
}

export interface LifecycleBreadcrumbProps {
    projectName?: string;
    currentPhase?: LifecyclePhase;
    selectedArtifactKind?: LifecycleArtifactKind;
    selectedArtifactTitle?: string;
    onNavigateToRoot?: () => void;
    onNavigateToPhase?: (phase: LifecyclePhase) => void;
    onClearArtifact?: () => void;
}

const PHASE_LABELS: Record<LifecyclePhase, string> = {
    INTENT: 'Intent',
    SHAPE: 'Shape',
    VALIDATE: 'Validate',
    GENERATE: 'Generate',
    RUN: 'Run',
    OBSERVE: 'Observe',
    IMPROVE: 'Improve',
};

/**
 * Lifecycle Breadcrumb Component
 * 
 * Displays current location in lifecycle hierarchy with clickable navigation.
 */
export function LifecycleBreadcrumb({
    projectName = 'Project',
    currentPhase,
    selectedArtifactKind,
    selectedArtifactTitle,
    onNavigateToRoot,
    onNavigateToPhase,
    onClearArtifact,
}: LifecycleBreadcrumbProps) {
    const breadcrumbs: BreadcrumbItem[] = [
        {
            label: projectName,
            icon: <Home className="w-4 h-4" />,
            onClick: onNavigateToRoot,
        },
        {
            label: 'Lifecycle',
            onClick: onNavigateToRoot,
        },
    ];

    if (currentPhase) {
        breadcrumbs.push({
            label: PHASE_LABELS[currentPhase],
            onClick: () => onNavigateToPhase?.(currentPhase),
        });
    }

    if (selectedArtifactKind && selectedArtifactTitle) {
        breadcrumbs.push({
            label: selectedArtifactTitle,
            onClick: onClearArtifact,
            active: true,
        });
    }

    return (
        <nav className="flex items-center gap-2 text-sm text-text-secondary mb-4 px-4 py-2 bg-bg-subtle rounded-lg border border-divider">
            {breadcrumbs.map((item, index) => (
                <React.Fragment key={index}>
                    {index > 0 && (
                        <ChevronRight className="w-4 h-4 text-text-tertiary" />
                    )}
                    <button
                        onClick={item.onClick}
                        disabled={!item.onClick}
                        className={`flex items-center gap-1.5 transition-colors ${item.active
                                ? 'text-primary-600 font-medium cursor-default'
                                : item.onClick
                                    ? 'hover:text-text-primary cursor-pointer'
                                    : 'text-text-tertiary cursor-default'
                            }`}
                    >
                        {item.icon}
                        <span>{item.label}</span>
                    </button>
                </React.Fragment>
            ))}
        </nav>
    );
}
