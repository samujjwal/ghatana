/**
 * Stage Metadata Helper
 *
 * Provides display metadata for DevSecOps stages.
 * Backend returns minimal StageMapping (stage + phases).
 * This helper enriches it with labels, descriptions, ordering, and categories.
 *
 * @doc.type utils
 * @doc.purpose Enrich backend stage mappings with display metadata
 * @doc.layer product
 */

import type { ComponentType } from 'react';

import {
    Code,
    FileText,
    FlaskConical,
    GitBranch,
    Monitor,
    Package,
    Rocket,
    Settings,
    Shield,
} from 'lucide-react';

export interface StageMetadata {
    label: string;
    description: string;
    order: number;
    category: 'planning' | 'development' | 'validation' | 'deployment' | 'operations';
    color: string;
    icon: ComponentType<{ className?: string }>;
}

const STAGE_METADATA: Record<string, StageMetadata> = {
    'plan': {
        label: 'Plan',
        description: 'Requirements gathering, story creation, and sprint planning',
        order: 1,
        category: 'planning',
        color: 'bg-blue-600',
        icon: FileText,
    },
    'design': {
        label: 'Design',
        description: 'Architecture design, technical specifications, and solution planning',
        order: 2,
        category: 'planning',
        color: 'bg-indigo-600',
        icon: GitBranch,
    },
    'develop': {
        label: 'Develop',
        description: 'Code development, unit testing, and peer reviews',
        order: 3,
        category: 'development',
        color: 'bg-purple-600',
        icon: Code,
    },
    'build': {
        label: 'Build',
        description: 'Continuous integration, artifact creation, and build validation',
        order: 4,
        category: 'development',
        color: 'bg-sky-600',
        icon: Package,
    },
    'test': {
        label: 'Test',
        description: 'Integration testing, system testing, and quality validation',
        order: 5,
        category: 'validation',
        color: 'bg-amber-600',
        icon: FlaskConical,
    },
    'secure': {
        label: 'Secure',
        description: 'Security scanning, vulnerability assessment, and compliance checks',
        order: 6,
        category: 'validation',
        color: 'bg-red-600',
        icon: Shield,
    },
    'compliance': {
        label: 'Compliance',
        description: 'Regulatory compliance validation and audit preparation',
        order: 7,
        category: 'validation',
        color: 'bg-green-600',
        icon: Shield,
    },
    'staging': {
        label: 'Staging',
        description: 'Pre-production validation and user acceptance testing',
        order: 8,
        category: 'deployment',
        color: 'bg-teal-600',
        icon: Package,
    },
    'deploy': {
        label: 'Deploy',
        description: 'Production deployment, rollout, and release management',
        order: 9,
        category: 'deployment',
        color: 'bg-emerald-600',
        icon: Rocket,
    },
    'operate': {
        label: 'Operate',
        description: 'Production operations, incident management, and service reliability',
        order: 10,
        category: 'operations',
        color: 'bg-slate-600',
        icon: Settings,
    },
    'monitor': {
        label: 'Monitor',
        description: 'System monitoring, alerting, and performance tracking',
        order: 11,
        category: 'operations',
        color: 'bg-cyan-600',
        icon: Monitor,
    },
};

/**
 * Get display metadata for a stage.
 * Returns default metadata if stage not found.
 */
export function getStageMetadata(stageKey: string): StageMetadata {
    return STAGE_METADATA[stageKey] || {
        label: stageKey.charAt(0).toUpperCase() + stageKey.slice(1),
        description: `${stageKey} stage`,
        order: 999,
        category: 'operations',
        color: 'bg-slate-600',
        icon: Settings,
    };
}

/**
 * Get all stage keys in order.
 */
export function getStageOrder(): string[] {
    return Object.keys(STAGE_METADATA).sort(
        (a, b) => STAGE_METADATA[a].order - STAGE_METADATA[b].order
    );
}

/**
 * Get stages by category.
 */
export function getStagesByCategory(category: StageMetadata['category']): string[] {
    return Object.entries(STAGE_METADATA)
        .filter(([_, meta]) => meta.category === category)
        .sort(([_, a], [__, b]) => a.order - b.order)
        .map(([key]) => key);
}
