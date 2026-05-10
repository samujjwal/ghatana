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
  const projectQuery = useQuery<PhaseProjectSnapshot>({
    queryKey: ['project', projectId, workspaceId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Missing project id for phase cockpit.');
      }

      if (!workspaceId) {
        throw new Error('Workspace context is required - project snapshot fetch must be scoped (TODO-001)');
      }
      return fetchProjectSnapshot(projectId, workspaceId);
    },
    enabled: Boolean(projectId),
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
    enabled: Boolean(projectId && workspaceId),
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
    enabled: Boolean(projectId && lifecyclePhase),
    retry: false,
  });

  const project = projectQuery.data;
  const projectRole = projectGuidanceRole(project);
  const activity = useMemo<PhaseActivityEvent[]>(
    () => activityQuery.data?.activity ?? [],
    [activityQuery.data],
  );
  const preview = previewQuery.data ?? null;
  const dataWarnings = useMemo<readonly PhaseCockpitDataWarning[]>(() => {
    const warnings: PhaseCockpitDataWarning[] = [];

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
    previewQuery.error,
    previewQuery.isError,
    previewQuery.refetch,
  ]);

  const blockers = useMemo(
    () => (project ? buildPhaseBlockers(phase, project, preview) : []),
    [phase, project, preview],
  );
  const evidence = useMemo(
    () => (project ? buildPhaseEvidence(phase, project, activity, preview) : []),
    [activity, phase, preview, project],
  );
  const governance = useMemo(
    () => buildPhaseGovernanceRecords(activity),
    [activity],
  );
  const suggestions = useMemo(
    () =>
      project
        ? buildPhaseSuggestedSteps(phase, project, onSuggestionAction, {
            blockers,
            preview,
            role: projectRole === 'owner' ? 'owner' : projectRole === 'collaborator' ? 'contributor' : 'viewer',
          })
        : [],
    [blockers, phase, preview, project, projectRole, onSuggestionAction],
  );
  const contract = useMemo(
    () =>
      project
        ? buildPhaseCockpitContract({
            phase,
            project,
            activity,
            preview,
            blockers,
            evidence,
            governance,
            suggestions,
          })
        : null,
    [activity, blockers, evidence, governance, phase, preview, project, suggestions],
  );
  const tier = useMemo(() => resolveTenantTier(project), [project]);
  const enabledFlags = useMemo(() => resolveEnabledFlags(project), [project]);
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
