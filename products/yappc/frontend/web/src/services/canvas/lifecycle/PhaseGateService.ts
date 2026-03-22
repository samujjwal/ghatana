/**
 * Phase Gate Service
 *
 * Service for managing phase gate transitions in the 7-phase lifecycle model.
 * Validates phase transitions, tracks gate status, and enforces artifact requirements.
 *
 * @doc.type service
 * @doc.purpose Phase gate transition management
 * @doc.layer product
 * @doc.pattern Service
 */

import { LifecyclePhase } from '@/types/lifecycle';
import type {
    PhaseGate,
    GateStatus,
    GateContext,
    ItemSummary,
} from '@/shared/types/phase-gates';
import {
    PHASE_GATES,
    PHASE_GATES_BY_ID,
    getGateForTransition,
    validateGate,
    validatePhaseTransition,
} from '@/shared/types/phase-gates';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import type {
    LifecycleArtifactService,
} from './LifecycleArtifactService';

// ============================================================================
// Types
// ============================================================================

/**
 * Project phase state.
 */
export interface ProjectPhaseState {
    projectId: string;
    currentPhase: LifecyclePhase;
    phaseHistory: PhaseTransitionRecord[];
    gateStatuses: Record<string, GateStatus>;
    lastUpdated: string;
}

/**
 * Recorded phase transition.
 */
export interface PhaseTransitionRecord {
    fromPhase: LifecyclePhase;
    toPhase: LifecyclePhase;
    gateId?: string;
    bypassed: boolean;
    bypassReason?: string;
    userId: string;
    timestamp: string;
}

/**
 * Phase transition request.
 */
export interface TransitionRequest {
    projectId: string;
    fromPhase: LifecyclePhase;
    toPhase: LifecyclePhase;
    userId: string;
    bypass?: boolean;
    bypassReason?: string;
}

/**
 * Phase transition result.
 */
export interface TransitionResult {
    success: boolean;
    newPhase?: LifecyclePhase;
    gateStatus?: GateStatus;
    errors: string[];
    warnings: string[];
}

/**
 * Phase progress summary.
 */
export interface PhaseProgress {
    phase: LifecyclePhase;
    status: 'not_started' | 'in_progress' | 'completed' | 'blocked';
    artifactProgress: {
        total: number;
        completed: number;
        percentage: number;
    };
    gateStatus?: GateStatus;
}

/**
 * Overall project lifecycle progress.
 */
export interface LifecycleProgress {
    projectId: string;
    currentPhase: LifecyclePhase;
    phases: PhaseProgress[];
    overallProgress: number;
    blockedGates: string[];
}

// ============================================================================
// Repository Interface
// ============================================================================

/**
 * Repository interface for project phase state persistence.
 */
export interface IProjectPhaseRepository {
    getState(projectId: string): Promise<ProjectPhaseState | null>;
    saveState(state: ProjectPhaseState): Promise<void>;
    updatePhase(
        projectId: string,
        phase: LifecyclePhase,
        record: PhaseTransitionRecord
    ): Promise<void>;
}

// ============================================================================
// In-Memory Repository
// ============================================================================

/**
 * In-memory repository implementation for development/testing.
 */
export class InMemoryProjectPhaseRepository implements IProjectPhaseRepository {
    private states: Map<string, ProjectPhaseState> = new Map();

    async getState(projectId: string): Promise<ProjectPhaseState | null> {
        const state = this.states.get(projectId);
        return state ? { ...state } : null;
    }

    async saveState(state: ProjectPhaseState): Promise<void> {
        this.states.set(state.projectId, { ...state });
    }

    async updatePhase(
        projectId: string,
        phase: LifecyclePhase,
        record: PhaseTransitionRecord
    ): Promise<void> {
        const state = this.states.get(projectId);
        if (!state) {
            throw new Error(`Project state not found: ${projectId}`);
        }
        state.currentPhase = phase;
        state.phaseHistory.push(record);
        state.lastUpdated = new Date().toISOString();
        this.states.set(projectId, state);
    }
}

// ============================================================================
// Service Implementation
// ============================================================================

/**
 * Phase Gate Service.
 *
 * Manages phase gate transitions with validation, bypass support,
 * and progress tracking.
 */
export class PhaseGateService {
    constructor(
        private readonly repository: IProjectPhaseRepository,
        private readonly artifactService?: LifecycleArtifactService
    ) { }

    // -------------------------------------------------------------------------
    // State Management
    // -------------------------------------------------------------------------

    /**
     * Initialize project phase state.
     */
    async initializeProject(
        projectId: string,
        initialPhase: LifecyclePhase = LifecyclePhase.INTENT
    ): Promise<ProjectPhaseState> {
        const existing = await this.repository.getState(projectId);
        if (existing) {
            return existing;
        }

        const state: ProjectPhaseState = {
            projectId,
            currentPhase: initialPhase,
            phaseHistory: [],
            gateStatuses: {},
            lastUpdated: new Date().toISOString(),
        };

        await this.repository.saveState(state);
        return state;
    }

    /**
     * Get current project phase state.
     */
    async getProjectState(projectId: string): Promise<ProjectPhaseState | null> {
        return this.repository.getState(projectId);
    }

    /**
     * Get current phase for a project.
     */
    async getCurrentPhase(projectId: string): Promise<LifecyclePhase> {
        const state = await this.repository.getState(projectId);
        return state?.currentPhase || LifecyclePhase.INTENT;
    }

    // -------------------------------------------------------------------------
    // Gate Operations
    // -------------------------------------------------------------------------

    /**
     * Get all phase gates.
     */
    getAllGates(): PhaseGate[] {
        return PHASE_GATES;
    }

    /**
     * Get gate by ID.
     */
    getGateById(gateId: string): PhaseGate | undefined {
        return PHASE_GATES_BY_ID[gateId];
    }

    /**
     * Get gate for a specific transition.
     */
    getGateForTransition(from: LifecyclePhase, to: LifecyclePhase): PhaseGate | undefined {
        return getGateForTransition(from, to);
    }

    /**
     * Check gate status for a project.
     */
    async checkGateStatus(projectId: string, gateId: string): Promise<GateStatus> {
        const gate = PHASE_GATES_BY_ID[gateId];
        if (!gate) {
            throw new Error(`Gate not found: ${gateId}`);
        }

        const context = await this.buildGateContext(projectId, gate.fromPhase, gate.toPhase);
        return validateGate(gate, context);
    }

    /**
     * Check all gate statuses for a project.
     */
    async checkAllGateStatuses(projectId: string): Promise<Record<string, GateStatus>> {
        const statuses: Record<string, GateStatus> = {};

        for (const gate of PHASE_GATES) {
            const context = await this.buildGateContext(projectId, gate.fromPhase, gate.toPhase);
            statuses[gate.id] = await validateGate(gate, context);
        }

        return statuses;
    }

    // -------------------------------------------------------------------------
    // Phase Transitions
    // -------------------------------------------------------------------------

    /**
     * Check if a phase transition is valid.
     */
    async canTransition(
        projectId: string,
        fromPhase: LifecyclePhase,
        toPhase: LifecyclePhase
    ): Promise<{ canTransition: boolean; gateStatus?: GateStatus }> {
        const context = await this.buildGateContext(projectId, fromPhase, toPhase);
        const result = await validatePhaseTransition(context);
        return {
            canTransition: result.canTransition,
            gateStatus: result.gateStatus,
        };
    }

    /**
     * Execute a phase transition.
     */
    async transition(request: TransitionRequest): Promise<TransitionResult> {
        const { projectId, fromPhase, toPhase, userId, bypass, bypassReason } = request;

        // Build context and validate
        const context = await this.buildGateContext(projectId, fromPhase, toPhase);
        const validation = await validatePhaseTransition(context);

        const errors: string[] = [];
        const warnings: string[] = [];

        // Check if transition is allowed
        if (!validation.canTransition) {
            if (bypass && validation.gate?.canBypass) {
                // Bypass is allowed
                warnings.push(`Gate bypassed: ${validation.gate.name}`);
            } else if (validation.gateStatus?.blockedReason) {
                errors.push(validation.gateStatus.blockedReason);
                return { success: false, errors, warnings };
            }
        }

        // Collect warnings from validation results
        if (validation.gateStatus?.validationResults) {
            for (const result of validation.gateStatus.validationResults) {
                warnings.push(...result.warnings);
            }
        }

        // Record the transition
        const record: PhaseTransitionRecord = {
            fromPhase,
            toPhase,
            gateId: validation.gate?.id,
            bypassed: bypass || false,
            bypassReason,
            userId,
            timestamp: new Date().toISOString(),
        };

        await this.repository.updatePhase(projectId, toPhase, record);

        // Update gate status to passed
        if (validation.gateStatus) {
            const state = await this.repository.getState(projectId);
            if (state) {
                state.gateStatuses[validation.gate!.id] = {
                    ...validation.gateStatus,
                    status: bypass ? 'bypassed' : 'passed',
                };
                await this.repository.saveState(state);
            }
        }

        return {
            success: true,
            newPhase: toPhase,
            gateStatus: validation.gateStatus,
            errors,
            warnings,
        };
    }

    // -------------------------------------------------------------------------
    // Progress Tracking
    // -------------------------------------------------------------------------

    /**
     * Get progress for a specific phase.
     */
    async getPhaseProgress(
        projectId: string,
        phase: LifecyclePhase
    ): Promise<PhaseProgress> {
        const state = await this.repository.getState(projectId);
        const currentPhase = state?.currentPhase || LifecyclePhase.INTENT;

        // Determine phase status
        let status: PhaseProgress['status'] = 'not_started';
        const phaseOrder = Object.values(LifecyclePhase);
        const phaseIndex = phaseOrder.indexOf(phase);
        const currentIndex = phaseOrder.indexOf(currentPhase);

        if (phaseIndex < currentIndex) {
            status = 'completed';
        } else if (phaseIndex === currentIndex) {
            status = 'in_progress';
        }

        // Calculate artifact progress
        // Note: This is a simplified calculation. In production, you would
        // integrate with LifecycleArtifactService to get actual artifact counts
        const expectedArtifactsByPhase: Record<LifecyclePhase, number> = {
            INTENT: 3,      // IdeaBrief, ResearchPack, ProblemStatement
            SHAPE: 5,       // Requirements, ADR, UxSpec, ThreatModel, Artifacts
            VALIDATE: 1,    // ValidationReport
            GENERATE: 2,    // BuildPlan, SourceCode
            RUN: 2,         // DeliveryPlan, ReleaseStrategy
            OBSERVE: 2,     // Runbook, SLOs
            IMPROVE: 1,     // RetroDoc
        };

        const expectedTotal = expectedArtifactsByPhase[phase] || 1;
        const completedCount = phaseIndex < currentIndex ? expectedTotal :
            phaseIndex === currentIndex ? Math.floor(expectedTotal / 2) :
                0;

        const artifactProgress = {
            total: expectedTotal,
            completed: completedCount,
            percentage: Math.round((completedCount / expectedTotal) * 100),
        };

        // Get gate status for next transition
        const nextPhase = phaseOrder[phaseIndex + 1];
        let gateStatus: GateStatus | undefined;
        if (nextPhase) {
            const gate = getGateForTransition(phase, nextPhase);
            if (gate) {
                const context = await this.buildGateContext(projectId, phase, nextPhase);
                gateStatus = await validateGate(gate, context);
                if (gateStatus.status === 'blocked') {
                    status = 'blocked';
                }
            }
        }

        return {
            phase,
            status,
            artifactProgress,
            gateStatus,
        };
    }

    /**
     * Get overall lifecycle progress.
     */
    async getLifecycleProgress(projectId: string): Promise<LifecycleProgress> {
        const state = await this.repository.getState(projectId);
        const currentPhase = state?.currentPhase || LifecyclePhase.INTENT;

        const phases: PhaseProgress[] = [];
        const blockedGates: string[] = [];

        for (const phase of Object.values(LifecyclePhase)) {
            const progress = await this.getPhaseProgress(projectId, phase);
            phases.push(progress);

            if (progress.gateStatus?.status === 'blocked') {
                blockedGates.push(progress.gateStatus.gateId);
            }
        }

        // Calculate overall progress
        const completedPhases = phases.filter((p) => p.status === 'completed').length;
        const overallProgress = Math.round((completedPhases / phases.length) * 100);

        return {
            projectId,
            currentPhase,
            phases,
            overallProgress,
            blockedGates,
        };
    }

    // -------------------------------------------------------------------------
    // Private Methods
    // -------------------------------------------------------------------------

    /**
     * Build gate context from project artifacts.
     */
    private async buildGateContext(
        projectId: string,
        fromPhase: LifecyclePhase,
        toPhase: LifecyclePhase
    ): Promise<GateContext> {
        const lifecycleArtifactItemsByKind: Partial<Record<LifecycleArtifactKind, ItemSummary>> = {};

        // If we have an artifact service, use it to get real artifacts
        if (this.artifactService) {
            const artifacts = await this.artifactService.listArtifacts({ projectId });
            for (const artifact of artifacts) {
                lifecycleArtifactItemsByKind[artifact.kind] = {
                    id: artifact.id,
                    title: artifact.title,
                    artifactKind: artifact.kind,
                    status: artifact.status as 'draft' | 'complete' | 'validated',
                    lastUpdated: artifact.updatedAt,
                };
            }
        }

        return {
            projectId,
            currentPhase: fromPhase,
            targetPhase: toPhase,
            lifecycleArtifactItemsByKind,
        };
    }
}

// ============================================================================
// React Hook
// ============================================================================

import React from 'react';

/**
 * React hook for phase gate operations.
 */
export function usePhaseGates(projectId: string) {
    const [service] = React.useState(
        () => new PhaseGateService(new InMemoryProjectPhaseRepository())
    );
    const [currentPhase, setCurrentPhase] = React.useState<LifecyclePhase>(LifecyclePhase.INTENT);
    const [gateStatuses, setGateStatuses] = React.useState<Record<string, GateStatus>>({});
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState<Error | null>(null);

    const refresh = React.useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            await service.initializeProject(projectId);
            const phase = await service.getCurrentPhase(projectId);
            setCurrentPhase(phase);
            const statuses = await service.checkAllGateStatuses(projectId);
            setGateStatuses(statuses);
        } catch (err) {
            setError(err instanceof Error ? err : new Error('Failed to load phase state'));
        } finally {
            setLoading(false);
        }
    }, [service, projectId]);

    React.useEffect(() => {
        refresh();
    }, [refresh]);

    const transition = React.useCallback(
        async (
            toPhase: LifecyclePhase,
            userId: string,
            options?: { bypass?: boolean; bypassReason?: string }
        ): Promise<TransitionResult> => {
            const result = await service.transition({
                projectId,
                fromPhase: currentPhase,
                toPhase,
                userId,
                bypass: options?.bypass,
                bypassReason: options?.bypassReason,
            });
            if (result.success) {
                await refresh();
            }
            return result;
        },
        [service, projectId, currentPhase, refresh]
    );

    const canTransition = React.useCallback(
        async (toPhase: LifecyclePhase) => {
            return service.canTransition(projectId, currentPhase, toPhase);
        },
        [service, projectId, currentPhase]
    );

    const getProgress = React.useCallback(async () => {
        return service.getLifecycleProgress(projectId);
    }, [service, projectId]);

    return {
        currentPhase,
        gateStatuses,
        loading,
        error,
        refresh,
        transition,
        canTransition,
        getProgress,
        service,
    };
}
