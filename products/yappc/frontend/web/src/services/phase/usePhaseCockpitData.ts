import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { buildPhaseBlockers } from './PhaseBlockerBuilder';
import { buildPhaseEvidence, buildPhaseGovernanceRecords } from './PhaseEvidenceBuilder';
import { getPhaseCockpitConfig } from './PhaseCockpitConfigService';
import {
  fetchPhaseTransitionPreview,
  isLifecyclePhase,
  parseJsonResponse,
  parseProjectResponse,
} from './PhaseCockpitDataService';
import { buildPhaseSuggestedSteps } from './PhaseSuggestionBuilder';
import type {
  LifecyclePhase,
  MountedPhase,
  PhaseActivityEvent,
  PhaseActivityResponse,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from './types';

function asTypedList<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

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
}

export function usePhaseCockpitData({
  phase,
  projectId,
  onSuggestionAction,
}: UsePhaseCockpitDataParams): UsePhaseCockpitDataResult {
  const config = getPhaseCockpitConfig(phase);

  const projectQuery = useQuery<PhaseProjectSnapshot>({
    queryKey: ['project', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}`);
      if (!response.ok) {
        throw new Error(`Failed to load the ${config.name.toLowerCase()} cockpit.`);
      }

      return parseProjectResponse(response, `${config.name.toLowerCase()} cockpit`);
    },
    enabled: Boolean(projectId),
  });

  const activityQuery = useQuery<PhaseActivityResponse>({
    queryKey: ['project-activity', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}/activity`);
      if (!response.ok) {
        throw new Error('Failed to load recent project activity.');
      }

      return parseJsonResponse<PhaseActivityResponse>(response, 'project activity');
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
    () => (project ? asTypedList(buildPhaseBlockers(phase, project, preview)) : []),
    [phase, project, preview],
  );
  const evidence = useMemo(
    () => (project ? asTypedList(buildPhaseEvidence(phase, project, activity, preview)) : []),
    [activity, phase, preview, project],
  );
  const governance = useMemo(
    () => asTypedList(buildPhaseGovernanceRecords(activity)),
    [activity],
  );
  const suggestions = useMemo(
    () =>
      project
        ? asTypedList(buildPhaseSuggestedSteps(phase, project, onSuggestionAction))
        : [],
    [phase, project, onSuggestionAction],
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
  };
}
