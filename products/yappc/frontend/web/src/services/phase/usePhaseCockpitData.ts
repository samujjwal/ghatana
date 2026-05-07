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
}

const DEFAULT_ENABLED_PHASE_FLAGS: PhaseCockpitContext['enabledFlags'] = new Set([
  'phase.generate.enabled',
  'phase.run.preview.enabled',
  'phase.observe.enabled',
]);

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
  const activity = useMemo<PhaseActivityEvent[]>(
    () => activityQuery.data?.activity ?? [],
    [activityQuery.data],
  );
  const preview = previewQuery.data ?? null;

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
            role: 'contributor',
          })
        : [],
    [blockers, phase, preview, project, onSuggestionAction],
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
        role: 'contributor',
        tier: 'starter',
        enabledFlags: DEFAULT_ENABLED_PHASE_FLAGS,
        hasBlockers: blockers.length > 0,
        gatesPassed: preview?.canAdvance ?? true,
        currentLifecyclePhase: phase,
      }),
    [blockers.length, phase, preview?.canAdvance],
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
  };
}
