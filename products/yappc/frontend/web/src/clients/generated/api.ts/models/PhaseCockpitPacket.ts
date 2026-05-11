/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { ActivityFeedEntry } from './ActivityFeedEntry';
import type { ActorContext } from './ActorContext';
import type { CapabilityModel } from './CapabilityModel';
import type { CompletedArtifact } from './CompletedArtifact';
import type { DashboardActionClassification } from './DashboardActionClassification';
import type { GovernanceRecord } from './GovernanceRecord';
import type { HealthSignals } from './HealthSignals';
import type { PhaseAction } from './PhaseAction';
import type { PhaseBlocker } from './PhaseBlocker';
import type { PhaseEvidence } from './PhaseEvidence';
import type { PhaseReadiness } from './PhaseReadiness';
import type { PlatformRunStatus } from './PlatformRunStatus';
import type { RequiredArtifact } from './RequiredArtifact';
export type PhaseCockpitPacket = {
    phase: PhaseCockpitPacket.phase;
    projectId: string;
    projectName?: string;
    tenantId: string;
    workspaceId: string;
    workspaceName?: string;
    actor: ActorContext;
    lifecyclePhase?: string;
    tenantTier: PhaseCockpitPacket.tenantTier;
    enabledPhaseFlags: Array<string>;
    capabilities: CapabilityModel;
    blockers: Array<PhaseBlocker>;
    readiness: PhaseReadiness;
    requiredArtifacts: Array<RequiredArtifact>;
    completedArtifacts: Array<CompletedArtifact>;
    activityFeed: Array<ActivityFeedEntry>;
    evidence: Array<PhaseEvidence>;
    governance: Array<GovernanceRecord>;
    platformRunStatus?: PlatformRunStatus;
    availableActions: Array<PhaseAction>;
    dashboardActions: DashboardActionClassification;
    healthSignals: HealthSignals;
    timestamp: number;
    correlationId?: string;
};
export namespace PhaseCockpitPacket {
    export enum phase {
        INTENT = 'intent',
        SHAPE = 'shape',
        VALIDATE = 'validate',
        GENERATE = 'generate',
        RUN = 'run',
        OBSERVE = 'observe',
        LEARN = 'learn',
        EVOLVE = 'evolve',
    }
    export enum tenantTier {
        FREE = 'FREE',
        PRO = 'PRO',
        ENTERPRISE = 'ENTERPRISE',
    }
}

