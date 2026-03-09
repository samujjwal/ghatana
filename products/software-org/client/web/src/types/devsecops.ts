/**
 * DevSecOps Types
 *
 * Shared type definitions for DevSecOps features.
 *
 * @doc.type types
 * @doc.purpose Shared DevSecOps type definitions
 */

export type StageStatus = 'on-track' | 'at-risk' | 'blocked' | 'completed';

export interface StageHealth {
    stage: string;
    status: StageStatus;
    itemsTotal: number;
    itemsCompleted: number;
    itemsBlocked: number;
    itemsInProgress: number;
    criticalIssues: number;
    lastUpdated: string;
}

export interface StageMapping {
    stage: string;
    label: string;
    description: string;
    phases: string[];
    order: number;
    category: 'planning' | 'development' | 'validation' | 'deployment' | 'operations';
}
