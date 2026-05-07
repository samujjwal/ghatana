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
  MountedPhase,
  PhaseActivityEvent,
  PhaseActivityResponse,
  PhaseCockpitContext,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from './types';

export interface UsePhaseCockpitDataParams {
  readonly phase: MountedPhase;
  readonly projectId?: string;
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

const DEFAULT_ENABLED_PHASE_FLAGS: PhaseCockpitContext['enabledFlags'] = new Set([
  'phase.generate.enabled',
  'phase.run.preview.enabled',
  'phase.observe.enabled',
]);

function describeQueryError(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }

  return fallback;
}

export function usePhaseCockpitData({
  phase,
  projectId,
  onSuggestionAction,
}: UsePhaseCockpitDataParams): UsePhaseCockpitDataResult {
  const projectQuery = useQuery<PhaseProjectSnapshot>({
    queryKey: ['project', projectId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Missing project id for phase cockpit.');
      }

      return fetchProjectSnapshot(projectId);
    },
    enabled: Boolean(projectId),
    retry: false,
  });

  const activityQuery = useQuery<PhaseActivityResponse>({
    queryKey: ['project-activity', projectId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Missing project id for phase activity.');
      }

      return yappcApi.projects.activity(projectId);
    },
    enabled: Boolean(projectId),
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
  const config = useMemo(
    () =>
      getAdaptivePhaseCockpitConfig(phase, {
        role: projectRole === 'owner' ? 'owner' : projectRole === 'collaborator' ? 'contributor' : 'viewer',
        tier: 'starter',
        enabledFlags: DEFAULT_ENABLED_PHASE_FLAGS,
        hasBlockers: blockers.length > 0,
        gatesPassed: preview?.canAdvance ?? true,
        currentLifecyclePhase: phase,
      }),
    [blockers.length, phase, preview?.canAdvance, projectRole],
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
