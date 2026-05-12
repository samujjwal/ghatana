import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { buildPhaseBlockers } from './PhaseBlockerBuilder';
import {
  buildPhaseCockpitContract,
  type PhaseCockpitContract,
} from './PhaseCockpitContractBuilder';
import { buildPhaseEvidence, buildPhaseGovernanceRecords } from './PhaseEvidenceBuilder';
import { getAdaptivePhaseCockpitConfig, getPhaseCockpitConfig } from './PhaseCockpitConfigService';
import {
  fetchProjectSnapshot,
  fetchPhaseTransitionPreview,
  isLifecyclePhase,
} from './PhaseCockpitDataService';
import type { LifecyclePhase } from './PhaseCockpitDataService';
import { buildPhaseSuggestedSteps } from './PhaseSuggestionBuilder';
import { yappcApi } from '@/lib/api/client';
import { projectGuidanceRole } from '@/services/workspace/accessControl';
import { usePhasePacket } from '@/hooks/usePhasePacket';
import type {
  PhaseFeatureFlag,
  MountedPhase,
  PhaseActivityEvent,
  PhaseActivityResponse,
  PhaseCockpitContext,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
  TenantTier,
} from './types';
import type { PhaseCockpitPacket, PhaseAction } from '@/types/phasePacket';

export interface UsePhaseCockpitDataParams {
  readonly phase: MountedPhase;
  readonly projectId?: string;
  readonly workspaceId?: string;
  readonly onSuggestionAction: () => void;
}

export interface PhaseCockpitDataWarning {
  readonly source: 'activity' | 'preview';
  readonly title: string;
  readonly message: string;
  readonly retryLabel: string;
  readonly retry: () => void;
}

export interface UsePhaseCockpitDataResult {
  readonly config: ReturnType<typeof getPhaseCockpitConfig>;
  readonly projectQuery: ReturnType<typeof useQuery<PhaseProjectSnapshot>>;
  readonly activityQuery: ReturnType<typeof useQuery<PhaseActivityResponse>>;
  readonly previewQuery: ReturnType<typeof useQuery<PhaseTransitionPreviewSnapshot>>;
  readonly lifecyclePhase: LifecyclePhase | null;
  readonly project: PhaseProjectSnapshot | undefined;
  readonly activity: PhaseActivityEvent[];
  readonly preview: PhaseTransitionPreviewSnapshot | null;
  readonly blockers: ReturnType<typeof buildPhaseBlockers>;
  readonly evidence: ReturnType<typeof buildPhaseEvidence>;
  readonly governance: ReturnType<typeof buildPhaseGovernanceRecords>;
  readonly suggestions: ReturnType<typeof buildPhaseSuggestedSteps>;
  readonly contract: PhaseCockpitContract | null;
  readonly dataWarnings: readonly PhaseCockpitDataWarning[];
  readonly hasPartialData: boolean;
}

// YAPPC-P0-005: Removed hardcoded fallback flags - tier and flags must come from backend entitlement/capability system

function resolveTenantTier(project: PhaseProjectSnapshot | undefined): TenantTier {
  // YAPPC-P0-005: Require explicit backend tier - no fallback
  if (!project?.tenantTier) {
    throw new Error('Tenant tier not provided by backend entitlement system');
  }
  return project.tenantTier;
}

function resolveEnabledFlags(project: PhaseProjectSnapshot | undefined): ReadonlySet<PhaseFeatureFlag> {
  // YAPPC-P0-005: Require explicit backend flags - no fallback
  const sourceFlags = project?.enabledPhaseFlags;
  if (!Array.isArray(sourceFlags) || sourceFlags.length === 0) {
    throw new Error('Enabled phase flags not provided by backend capability system');
  }
  return new Set(sourceFlags);
}

function describeQueryError(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }

  return fallback;
}

export function usePhaseCockpitData({
  phase,
  projectId,
  workspaceId,
  onSuggestionAction,
}: UsePhaseCockpitDataParams): UsePhaseCockpitDataResult {
  // Task 5.A.4: Use phase packet endpoint as canonical data source instead of local calculations
  const { packet, isLoading: packetLoading, error: packetError, refetch: refetchPacket } = usePhasePacket({
    phase,
    projectId: projectId ?? '',
    workspaceId: workspaceId ?? '',
  });

  // Fallback to local queries if packet endpoint fails (for backward compatibility during transition)
  const projectQuery = useQuery<PhaseProjectSnapshot>({
    queryKey: ['project', projectId, workspaceId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Missing project id for phase cockpit.');
      }

      if (!workspaceId) {
        throw new Error(
          'Workspace context is required. Please access this project from within a workspace.'
        );
      }
      return fetchProjectSnapshot(projectId, workspaceId);
    },
    enabled: Boolean(projectId) && packetError !== null,
    retry: false,
  });

  const activityQuery = useQuery<PhaseActivityResponse>({
    queryKey: ['project-activity', projectId, workspaceId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Missing project id for phase activity.');
      }
      if (!workspaceId) {
        throw new Error('Workspace context is required for activity fetch - project access must be scoped');
      }

      return yappcApi.projects.activity(projectId, workspaceId);
    },
    enabled: Boolean(projectId && workspaceId) && packetError !== null,
    retry: false,
  });

  const projectPhase = projectQuery.data?.lifecyclePhase;
  const lifecyclePhase = projectPhase && isLifecyclePhase(projectPhase) ? projectPhase : null;

  const previewQuery = useQuery<PhaseTransitionPreviewSnapshot>({
    queryKey: ['project-phase-preview', projectId, projectPhase],
    queryFn: () => {
      if (!projectId || !lifecyclePhase) {
        throw new Error('Missing lifecycle phase context.');
      }

      return fetchPhaseTransitionPreview(lifecyclePhase, projectId);
    },
    enabled: Boolean(projectId && lifecyclePhase) && packetError !== null,
    retry: false,
  });

  // Use packet data as primary source, fallback to local queries if packet unavailable
  const project = useMemo(() => {
    if (packet) {
      // Map PhaseCockpitPacket to PhaseProjectSnapshot
      return {
        id: packet.projectId,
        name: packet.projectName,
        lifecyclePhase: packet.lifecyclePhase || packet.phase as any,
        tenantId: packet.tenantId,
        tenantTier: packet.tenantTier as any,
        enabledPhaseFlags: Array.from(packet.enabledPhaseFlags),
        aiHealthScore: packet.healthSignals?.preview?.status === 'healthy' ? 100 : 50,
        aiNextActions: packet.availableActions.map((a: PhaseAction) => a.label),
        updatedAt: new Date(packet.timestamp).toISOString(),
        ownerWorkspaceId: packet.workspaceId,
        ownerWorkspace: { id: packet.workspaceId, name: packet.workspaceName },
      } as PhaseProjectSnapshot;
    }
    return projectQuery.data;
  }, [packet, projectQuery.data]);

  const projectRole = projectGuidanceRole(project);

  // Map packet activity to PhaseActivityEvent
  const activity = useMemo<PhaseActivityEvent[]>(() => {
    if (packet?.activityFeed) {
      return packet.activityFeed.map(entry => ({
        id: entry.id,
        source: 'lifecycle' as const,
        action: entry.action,
        summary: entry.summary,
        timestamp: new Date(entry.timestamp).toISOString(),
        actor: entry.actor,
        severity: entry.severity as any,
        success: true,
      }));
    }
    return activityQuery.data?.activity ?? [];
  }, [packet, activityQuery.data]);

  // Map packet readiness to PhaseTransitionPreviewSnapshot
  const preview = useMemo<PhaseTransitionPreviewSnapshot | null>(() => {
    if (packet?.readiness) {
      return {
        projectId: packet.projectId,
        currentPhase: packet.lifecyclePhase || packet.phase as any,
        nextPhase: packet.readiness.nextPhase as any,
        canAdvance: packet.readiness.canAdvance,
        readiness: Math.round(packet.readiness.completenessScore * 100),
        blockers: Array.from(packet.readiness.missingPrerequisites),
        requiredArtifacts: packet.requiredArtifacts.map(a => a.artifactId),
        completedArtifacts: packet.completedArtifacts.map(a => a.artifactId),
        estimatedReadyIn: packet.readiness.canAdvance ? 'Ready now' : 'Blocked',
        estimatedReadyInHours: packet.readiness.canAdvance ? 0 : 24,
        predictionConfidence: packet.readiness.completenessScore,
        decisionSupport: null,
        checkedAt: new Date(packet.timestamp).toISOString(),
      } as PhaseTransitionPreviewSnapshot;
    }
    return previewQuery.data ?? null;
  }, [packet, previewQuery.data]);

  // Map packet blockers to local blocker format
  const blockers = useMemo(() => {
    if (packet?.blockers) {
      return packet.blockers.map(b => ({
        id: b.id,
        title: b.title,
        severity: (b.severity === 'CRITICAL' ? 'critical' : b.severity === 'WARNING' ? 'medium' : 'low') as 'critical' | 'medium' | 'high' | 'low',
        description: b.description,
        source: b.type,
      }));
    }
    return project ? buildPhaseBlockers(phase, project, preview) : [];
  }, [packet, project, preview, phase]);

  // Map packet evidence to local evidence format
  const evidence = useMemo(() => {
    if (packet?.evidence) {
      return packet.evidence.map(e => ({
        id: e.id,
        type: (e.type as 'metric' | 'log' | 'artifact' | 'observation' | 'recommendation') || 'observation',
        title: e.title,
        description: e.description,
        timestamp: new Date(e.timestamp).toISOString(),
        metadata: e.metadata,
      }));
    }
    return project ? buildPhaseEvidence(phase, project, activity, preview) : [];
  }, [packet, project, activity, preview, phase]);

  // Map packet governance to local governance format
  const governance = useMemo(() => {
    if (packet?.governance) {
      return packet.governance.map(g => ({
        id: g.id,
        artifactId: g.id,
        action: g.type,
        actor: g.actor,
        source: (g.type as 'preview' | 'backed' | 'derived' | 'suggested' | 'unavailable') || 'derived',
        summary: g.outcome,
        timestamp: new Date(g.timestamp).toISOString(),
        decision: g.outcome,
      }));
    }
    return buildPhaseGovernanceRecords(activity);
  }, [packet, activity]);

  // Map packet actions to local suggestions format
  const suggestions = useMemo(() => {
    if (packet?.availableActions) {
      return packet.availableActions.map((a: PhaseAction) => ({
        id: a.actionId,
        title: a.label,
        type: 'suggestion' as const,
        label: a.label,
        description: a.description,
        confidence: 0.5,
        evidence: [] as any,
        action: onSuggestionAction,
        priority: a.enabled ? 'high' : 'low',
        kind: 'suggestion' as const,
        enabled: a.enabled,
        metadata: a.parameters,
      })) as any;
    }
    return project
      ? buildPhaseSuggestedSteps(phase, project, onSuggestionAction, {
          blockers,
          preview,
          role: projectRole === 'owner' ? 'owner' : projectRole === 'collaborator' ? 'contributor' : 'viewer',
        })
      : [];
  }, [packet, project, blockers, preview, phase, projectRole, onSuggestionAction]);

  // Build contract from packet data
  const contract = useMemo(() => {
    if (!project) return null;
    return buildPhaseCockpitContract({
      phase,
      project,
      activity,
      preview,
      blockers,
      evidence,
      governance,
      suggestions,
    });
  }, [activity, blockers, evidence, governance, phase, preview, project, suggestions]);

  const tier = useMemo(() => {
    if (packet) return packet.tenantTier as any;
    return resolveTenantTier(project);
  }, [packet, project]);

  const enabledFlags = useMemo(() => {
    if (packet) return new Set(packet.enabledPhaseFlags) as any;
    return resolveEnabledFlags(project);
  }, [packet, project]);

  const config = useMemo(
    () =>
      getAdaptivePhaseCockpitConfig(phase, {
        role: projectRole === 'owner' ? 'owner' : projectRole === 'collaborator' ? 'contributor' : 'viewer',
        tier,
        enabledFlags,
        hasBlockers: blockers.length > 0,
        gatesPassed: preview?.canAdvance ?? true,
        currentLifecyclePhase: phase,
      }),
    [blockers.length, enabledFlags, phase, preview?.canAdvance, projectRole, tier],
  );

  const dataWarnings = useMemo<readonly PhaseCockpitDataWarning[]>(() => {
    const warnings: PhaseCockpitDataWarning[] = [];

    if (packetError) {
      warnings.push({
        source: 'activity',
        title: 'Phase packet endpoint unavailable',
        message: describeQueryError(packetError, 'The cockpit is using fallback data sources.'),
        retryLabel: 'Retry phase packet',
        retry: () => {
          void refetchPacket();
        },
      });
    }

    if (activityQuery.isError) {
      warnings.push({
        source: 'activity',
        title: 'Recent activity is temporarily unavailable',
        message: describeQueryError(activityQuery.error, 'The cockpit is showing project data without the latest activity feed.'),
        retryLabel: 'Retry activity',
        retry: () => {
          void activityQuery.refetch();
        },
      });
    }

    if (previewQuery.isError) {
      warnings.push({
        source: 'preview',
        title: 'Lifecycle readiness preview is temporarily unavailable',
        message: describeQueryError(previewQuery.error, 'The cockpit is showing project data without the latest lifecycle readiness preview.'),
        retryLabel: 'Retry readiness',
        retry: () => {
          void previewQuery.refetch();
        },
      });
    }

    return warnings;
  }, [
    activityQuery.error,
    activityQuery.isError,
    activityQuery.refetch,
    packetError,
    previewQuery.error,
    previewQuery.isError,
    previewQuery.refetch,
    refetchPacket,
  ]);

  return {
    config,
    projectQuery,
    activityQuery,
    previewQuery,
    lifecyclePhase,
    project,
    activity,
    preview,
    blockers,
    evidence,
    governance,
    suggestions,
    contract,
    dataWarnings,
    hasPartialData: dataWarnings.length > 0,
  };
}
