/**
 * Audit Service
 * 
 * Service for tracking and emitting audit events.
 * Implements standardized ARTIFACT_VERB pattern.
 * 
 * @doc.type service
 * @doc.purpose Audit event tracking
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { AuditEvent, lifecycleAPI } from './api';
import { FOWStage, ArtifactType } from '@/types/fow-stages';
import { LifecyclePhase } from '@/types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export enum AuditVerb {
    CREATED = 'CREATED',
    UPDATED = 'UPDATED',
    DELETED = 'DELETED',
    APPROVED = 'APPROVED',
    REJECTED = 'REJECTED',
    VERSIONED = 'VERSIONED',
    LINKED = 'LINKED',
    UNLINKED = 'UNLINKED',
    DEPLOYED = 'DEPLOYED',
    VERIFIED = 'VERIFIED',
    COMPLETED = 'COMPLETED',
    STARTED = 'STARTED',
    FAILED = 'FAILED',
}

export interface AuditEventData {
    artifactType?: string;
    verb: AuditVerb;
    userId: string;
    projectId: string;
    artifactId?: string;
    fowStage: FOWStage;
    phase: LifecyclePhase;
    metadata?: Record<string, unknown>;
    description?: string;
}

// ============================================================================
// Audit Service
// ============================================================================

export class AuditService {
    /**
     * Emit a standardized audit event
     */
    async emit(eventData: AuditEventData): Promise<AuditEvent> {
        const type = eventData.artifactType
            ? `${eventData.artifactType}_${eventData.verb}`
            : eventData.verb;

        const description = eventData.description || this.generateDescription(eventData);

        const event: Omit<AuditEvent, 'id' | 'timestamp'> = {
            type,
            userId: eventData.userId,
            projectId: eventData.projectId,
            artifactId: eventData.artifactId,
            fowStage: eventData.fowStage,
            phase: eventData.phase,
            metadata: eventData.metadata,
            description,
        };

        try {
            return await lifecycleAPI.audit.emitEvent(event);
        } catch (error) {
            console.error('Failed to emit audit event:', error);
            throw error;
        }
    }

    /**
     * Generate human-readable description from event data
     */
    private generateDescription(eventData: AuditEventData): string {
        const artifact = eventData.artifactType ? eventData.artifactType.replace(/_/g, ' ').toLowerCase() : 'item';
        const verb = eventData.verb.toLowerCase();

        return `${artifact} ${verb}`;
    }

    /**
     * Emit artifact created event
     */
    async artifactCreated(data: {
        artifactType: ArtifactType;
        artifactId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
        metadata?: Record<string, unknown>;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: data.artifactType,
            verb: AuditVerb.CREATED,
            userId: data.userId,
            projectId: data.projectId,
            artifactId: data.artifactId,
            fowStage: data.fowStage,
            phase: data.phase,
            metadata: data.metadata,
        });
    }

    /**
     * Emit artifact updated event
     */
    async artifactUpdated(data: {
        artifactType: ArtifactType;
        artifactId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
        changes?: Record<string, unknown>;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: data.artifactType,
            verb: AuditVerb.UPDATED,
            userId: data.userId,
            projectId: data.projectId,
            artifactId: data.artifactId,
            fowStage: data.fowStage,
            phase: data.phase,
            metadata: { changes: data.changes },
        });
    }

    /**
     * Emit artifact deleted event
     */
    async artifactDeleted(data: {
        artifactType: ArtifactType;
        artifactId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: data.artifactType,
            verb: AuditVerb.DELETED,
            userId: data.userId,
            projectId: data.projectId,
            artifactId: data.artifactId,
            fowStage: data.fowStage,
            phase: data.phase,
        });
    }

    /**
     * Emit artifact approved event
     */
    async artifactApproved(data: {
        artifactType: ArtifactType;
        artifactId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: data.artifactType,
            verb: AuditVerb.APPROVED,
            userId: data.userId,
            projectId: data.projectId,
            artifactId: data.artifactId,
            fowStage: data.fowStage,
            phase: data.phase,
        });
    }

    /**
     * Emit stage transition event
     */
    async stageTransitioned(data: {
        userId: string;
        projectId: string;
        fromStage: FOWStage;
        toStage: FOWStage;
        phase: LifecyclePhase;
    }): Promise<AuditEvent> {
        return this.emit({
            verb: AuditVerb.COMPLETED,
            userId: data.userId,
            projectId: data.projectId,
            fowStage: data.toStage,
            phase: data.phase,
            metadata: { fromStage: data.fromStage },
            description: `Transitioned from stage ${data.fromStage} to ${data.toStage}`,
        });
    }

    /**
     * Emit task started event
     */
    async taskStarted(data: {
        taskId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
        taskType: string;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: 'TASK',
            verb: AuditVerb.STARTED,
            userId: data.userId,
            projectId: data.projectId,
            fowStage: data.fowStage,
            phase: data.phase,
            metadata: { taskId: data.taskId, taskType: data.taskType },
        });
    }

    /**
     * Emit task completed event
     */
    async taskCompleted(data: {
        taskId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
        taskType: string;
        outputArtifacts?: string[];
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: 'TASK',
            verb: AuditVerb.COMPLETED,
            userId: data.userId,
            projectId: data.projectId,
            fowStage: data.fowStage,
            phase: data.phase,
            metadata: {
                taskId: data.taskId,
                taskType: data.taskType,
                outputArtifacts: data.outputArtifacts,
            },
        });
    }

    /**
     * Emit evidence linked event
     */
    async evidenceLinked(data: {
        userId: string;
        projectId: string;
        artifactId: string;
        evidenceId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: 'EVIDENCE',
            verb: AuditVerb.LINKED,
            userId: data.userId,
            projectId: data.projectId,
            artifactId: data.artifactId,
            fowStage: data.fowStage,
            phase: data.phase,
            metadata: { evidenceId: data.evidenceId },
        });
    }

    /**
     * Emit release deployed event
     */
    async releaseDeployed(data: {
        releaseId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
        environment: string;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: 'RELEASE',
            verb: AuditVerb.DEPLOYED,
            userId: data.userId,
            projectId: data.projectId,
            fowStage: data.fowStage,
            phase: data.phase,
            metadata: { releaseId: data.releaseId, environment: data.environment },
        });
    }

    /**
     * Emit release verified event
     */
    async releaseVerified(data: {
        releaseId: string;
        userId: string;
        projectId: string;
        fowStage: FOWStage;
        phase: LifecyclePhase;
        verificationResults: unknown;
    }): Promise<AuditEvent> {
        return this.emit({
            artifactType: 'RELEASE',
            verb: AuditVerb.VERIFIED,
            userId: data.userId,
            projectId: data.projectId,
            fowStage: data.fowStage,
            phase: data.phase,
            metadata: {
                releaseId: data.releaseId,
                verificationResults: data.verificationResults,
            },
        });
    }
}

// ============================================================================
// Singleton instance
// ============================================================================

export const auditService = new AuditService();

// ============================================================================
// React Hook for easy usage
// ============================================================================

/**
 * Hook to access audit service with current user/project context
 */
export function useAuditService(projectId: string, userId: string, fowStage: FOWStage, phase: LifecyclePhase) {
    const emit = (eventData: Partial<AuditEventData>) => {
        return auditService.emit({
            userId,
            projectId,
            fowStage,
            phase,
            ...eventData,
        } as AuditEventData);
    };

    return {
        emit,
        artifactCreated: (artifactType: ArtifactType, artifactId: string, metadata?: Record<string, unknown>) =>
            auditService.artifactCreated({ artifactType, artifactId, userId, projectId, fowStage, phase, metadata }),
        artifactUpdated: (artifactType: ArtifactType, artifactId: string, changes?: Record<string, unknown>) =>
            auditService.artifactUpdated({ artifactType, artifactId, userId, projectId, fowStage, phase, changes }),
        artifactDeleted: (artifactType: ArtifactType, artifactId: string) =>
            auditService.artifactDeleted({ artifactType, artifactId, userId, projectId, fowStage, phase }),
        taskStarted: (taskId: string, taskType: string) =>
            auditService.taskStarted({ taskId, userId, projectId, fowStage, phase, taskType }),
        taskCompleted: (taskId: string, taskType: string, outputArtifacts?: string[]) =>
            auditService.taskCompleted({ taskId, userId, projectId, fowStage, phase, taskType, outputArtifacts }),
    };
}
