import { useQuery } from '@tanstack/react-query';
import React, { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import type { Blocker } from '../../../components/phase/PhaseBlockerPanel';
import { PhaseBlockerPanel } from '../../../components/phase/PhaseBlockerPanel';
import { PhaseCockpitLayout } from '../../../components/phase/PhaseCockpitLayout';
import type { EvidenceItem } from '../../../components/phase/PhaseEvidencePanel';
import { PhaseEvidencePanel } from '../../../components/phase/PhaseEvidencePanel';
import type { GovernanceRecord } from '../../../components/phase/PhaseGovernanceTrace';
import { PhaseGovernanceTrace } from '../../../components/phase/PhaseGovernanceTrace';
import { PhasePrimaryActionCard } from '../../../components/phase/PhasePrimaryActionCard';
import type { SuggestedStep } from '../../../components/phase/PhaseSuggestedNextStep';
import { PhaseSuggestedNextStep } from '../../../components/phase/PhaseSuggestedNextStep';
import {
  buildPhaseBlockers,
  buildPhaseEvidence,
  buildPhaseGovernanceRecords,
  buildPhaseSuggestedSteps,
  getPhaseCockpitConfig,
} from '../../../services/phase';
import type {
  MountedPhase,
  PhaseActivityEvent,
  PhaseActivityResponse,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from '../../../services/phase';

import CanvasRoute from './canvas';
import DeployRoute from './deploy';
import LifecycleRoute from './lifecycle';
import { PhaseStatusPanels } from './PhaseStatusPanels';
import PreviewRoute from './preview';

import ProjectOverviewRoute from './index';

type LifecyclePhase =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'RUN'
  | 'OBSERVE'
  | 'LEARN'
  | 'EVOLVE';

interface RawPhaseProjectSnapshot extends Omit<PhaseProjectSnapshot, 'healthScore' | 'nextActionHints'> {
  aiHealthScore?: number | null;
  aiNextActions?: string[];
}

function asTypedList<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

const LIFECYCLE_PHASES: readonly LifecyclePhase[] = [
  'INTENT',
  'SHAPE',
  'VALIDATE',
  'GENERATE',
  'RUN',
  'OBSERVE',
  'LEARN',
  'EVOLVE',
];

async function readResponseBody(response: Response): Promise<string> {
  const maybeText = (response as Response & { text?: () => Promise<string> }).text;
  if (typeof maybeText === 'function') {
    return maybeText.call(response);
  }

  const maybeJson = (response as Response & { json?: () => Promise<unknown> }).json;
  if (typeof maybeJson === 'function') {
    const payload = await maybeJson.call(response);
    if (typeof payload === 'string') {
      return payload;
    }
    return JSON.stringify(payload ?? {});
  }

  return '';
}

async function parseJsonResponse<T>(
  response: Response,
  context: string,
): Promise<T> {
  const raw = await readResponseBody(response);

  if (!raw) {
    throw new Error(`${context} returned an empty response`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context} returned invalid JSON: ${detail}`, {
      cause: error,
    });
  }
}

async function parseProjectResponse(
  response: Response,
  context: string,
): Promise<PhaseProjectSnapshot> {
  const payload = await parseJsonResponse<unknown>(response, context);

  const rawProject = (
    typeof payload === 'object' &&
    payload !== null &&
    'project' in payload
      ? (payload as { project: RawPhaseProjectSnapshot }).project
      : (payload as RawPhaseProjectSnapshot)
  );

  return {
    ...rawProject,
    healthScore: rawProject.aiHealthScore ?? null,
    nextActionHints: rawProject.aiNextActions ?? [],
  };

}

function isLifecyclePhase(value: string): value is LifecyclePhase {
  return (LIFECYCLE_PHASES as readonly string[]).includes(value);
}

async function fetchPhaseTransitionPreview(
  currentPhase: LifecyclePhase,
  projectId: string,
): Promise<PhaseTransitionPreviewSnapshot> {
  const response = await fetch(
    `/api/phases/${encodeURIComponent(currentPhase)}/next?projectId=${encodeURIComponent(projectId)}`,
    {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    },
  );

  if (!response.ok) {
    throw new Error('Failed to load phase transition preview.');
  }

  return parseJsonResponse<PhaseTransitionPreviewSnapshot>(
    response,
    'fetch phase transition preview',
  );
}

function formatTimestamp(timestamp: string): string {
  return new Date(timestamp).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function renderEmbeddedSurface(phase: MountedPhase): React.ReactNode {
  switch (phase) {
    case 'intent':
      return <ProjectOverviewRoute />;
    case 'shape':
    case 'generate':
      return <CanvasRoute />;
    case 'run':
      return <DeployRoute />;
    case 'observe':
      return (
        <div className="space-y-4">
          <div data-testid="project-preview-iframe">
            <PreviewRoute />
          </div>
          <LifecycleRoute />
        </div>
      );
    case 'validate':
      return (
        <div
          data-testid="validate-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Validation and approval gates are already surfaced in the phase-native cockpit panels.
          Open Observe for full lifecycle timeline and audit history details.
        </div>
      );
    case 'learn':
      return (
        <div
          data-testid="learn-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Learning summaries and reusable patterns stay phase-native. Use this panel for supporting
          notes while preserving the focused cockpit flow.
        </div>
      );
    case 'evolve':
      return (
        <div
          data-testid="evolve-advanced-panel"
          className="rounded-xl border border-border bg-surface p-4 text-sm text-fg-muted"
        >
          Evolution roadmap and backlog prioritization remain in the cockpit surface to avoid context switching.
        </div>
      );
    default:
      return <LifecycleRoute />;
  }
}

function PhaseCockpitRoute({ phase }: { phase: MountedPhase }) {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [feedback, setFeedback] = useState<string | null>(null);
  const config = getPhaseCockpitConfig(phase);

  const projectQuery = useQuery<PhaseProjectSnapshot>({
    queryKey: ['project', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}`);
      if (!response.ok) {
        throw new Error(`Failed to load the ${config.name.toLowerCase()} cockpit.`);
      }

      return parseProjectResponse(
        response,
        `${config.name.toLowerCase()} cockpit`,
      );
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
  const lifecyclePhase =
    projectPhase && isLifecyclePhase(projectPhase) ? projectPhase : null;

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

  const scrollToSupportingSurface = useCallback(() => {
    document.getElementById(`${phase}-supporting-surface`)?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  }, [phase]);

  const handlePrimaryAction = () => {
    if (phase === 'intent' && projectId) {
      void navigate(`/p/${projectId}/intent?drawer=idea`);
      return;
    }

    setFeedback(config.actionFeedback);
    scrollToSupportingSurface();
  };

  const handleSecondaryAction = () => {
    setFeedback(`Reviewing ${config.supportingTitle.toLowerCase()} for ${config.name}.`);
    scrollToSupportingSurface();
  };

  const project = projectQuery.data;
  const activity = useMemo<PhaseActivityEvent[]>(
    () => activityQuery.data?.activity ?? [],
    [activityQuery.data],
  );
  const preview = previewQuery.data ?? null;

  const blockers = useMemo<Blocker[]>(
    () => (project ? asTypedList<Blocker>(buildPhaseBlockers(phase, project, preview)) : []),
    [phase, project, preview],
  );
  const evidence = useMemo<EvidenceItem[]>(
    () => (project ? asTypedList<EvidenceItem>(buildPhaseEvidence(phase, project, activity, preview)) : []),
    [activity, phase, preview, project],
  );
  const governance = useMemo<GovernanceRecord[]>(() => asTypedList<GovernanceRecord>(buildPhaseGovernanceRecords(activity)), [activity]);
  const suggestions = useMemo<SuggestedStep[]>(
    () => (project ? asTypedList<SuggestedStep>(buildPhaseSuggestedSteps(phase, project, scrollToSupportingSurface)) : []),
    [phase, project, scrollToSupportingSurface],
  );

  if (projectQuery.isLoading || !project) {
    return (
      <div className="p-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-48 rounded bg-surface-muted" />
          <div className="h-28 rounded-xl bg-surface-muted" />
          <div className="grid gap-4 md:grid-cols-2">
            <div className="h-40 rounded-xl bg-surface-muted" />
            <div className="h-40 rounded-xl bg-surface-muted" />
          </div>
        </div>
      </div>
    );
  }

  const projectName = project.name ?? 'this project';
  const statusPanels = (
    <PhaseStatusPanels
      phase={phase}
      preview={preview}
      blockers={blockers}
      activity={activity}
    />
  );
  const advancedDetails = (
    <div
      id={`${phase}-supporting-surface`}
      className="rounded-2xl border border-border bg-surface-raised p-4 shadow-sm"
    >
      <div className="mb-4">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-fg-muted">
          Existing route details
        </p>
        <h2 className="mt-2 text-lg font-semibold text-fg">{config.supportingTitle}</h2>
        <p className="mt-1 text-sm text-fg-muted">
          Review the existing detailed surface below when you need deeper context beyond the phase-native cockpit.
        </p>
      </div>
      <div className="mb-4 text-xs text-fg-muted">
        Last activity:{' '}
        {activity[0]?.timestamp ? formatTimestamp(activity[0].timestamp) : 'No recent backed activity'}
      </div>
      {renderEmbeddedSurface(phase)}
    </div>
  );

  return (
    <div className="p-6 space-y-6">
      <PhaseCockpitLayout
        testId={`${phase}-cockpit`}
        phaseName={config.name}
        phaseDescription={`${config.description} Project: ${projectName}.`}
        primaryAction={(
          <PhasePrimaryActionCard
            title={config.primaryTitle}
            description={config.primaryDescription}
            actionLabel={config.primaryLabel}
            onAction={handlePrimaryAction}
            secondaryActionLabel={config.secondaryLabel}
            onSecondaryAction={handleSecondaryAction}
            icon={config.icon}
            disabled={phase === 'validate' || phase === 'generate' ? blockers.length > 0 : false}
            disabledReason={blockers.length > 0 ? blockers[0]?.description : undefined}
            testId={`${phase}-primary-action-card`}
            actionTestId={config.primaryTestId}
            secondaryActionTestId={config.secondaryTestId}
            actionAriaLabel={`${config.name} primary action`}
          />
        )}
        blockers={<PhaseBlockerPanel blockers={blockers} />}
        evidence={<PhaseEvidencePanel evidence={evidence} />}
        suggestedAutomation={<PhaseSuggestedNextStep steps={suggestions} />}
        governanceTrace={<PhaseGovernanceTrace records={governance} />}
        advancedTools={advancedDetails}
        advancedToolsLabel="Advanced details"
      >
        <div className="space-y-4" data-testid={`${phase}-native-summary`}>
          {feedback ? (
            <div className="rounded-xl border border-sky-200 bg-sky-50 p-4 text-sm text-sky-900">
              {feedback}
            </div>
          ) : null}
          {statusPanels}
        </div>
      </PhaseCockpitLayout>
    </div>
  );
}

export function IntentCockpitRoute() {
  return <PhaseCockpitRoute phase="intent" />;
}

export function ShapeCockpitRoute() {
  return <PhaseCockpitRoute phase="shape" />;
}

export function ValidateCockpitRoute() {
  return <PhaseCockpitRoute phase="validate" />;
}

export function GenerateCockpitRoute() {
  return <PhaseCockpitRoute phase="generate" />;
}

export function RunCockpitRoute() {
  return <PhaseCockpitRoute phase="run" />;
}

export function ObserveCockpitRoute() {
  return <PhaseCockpitRoute phase="observe" />;
}

export function LearnCockpitRoute() {
  return <PhaseCockpitRoute phase="learn" />;
}

export function EvolveCockpitRoute() {
  return <PhaseCockpitRoute phase="evolve" />;
}
