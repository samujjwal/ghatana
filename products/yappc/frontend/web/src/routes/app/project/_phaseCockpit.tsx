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
  fetchPhaseTransitionPreview,
  formatTimestamp,
  getPhaseCockpitConfig,
  isLifecyclePhase,
  parseJsonResponse,
  parseProjectResponse,
} from '../../../services/phase';
import type {
  LifecyclePhase,
  MountedPhase,
  PhaseActivityEvent,
  PhaseActivityResponse,
  PhaseProjectSnapshot,
  PhaseTransitionPreviewSnapshot,
} from '../../../services/phase';
import { PhaseStatusPanels } from './PhaseStatusPanels';
import { PhaseEmbeddedSurface } from './PhaseEmbeddedSurface';

function asTypedList<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
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
      <PhaseEmbeddedSurface phase={phase} />
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
            <div className="rounded-xl border border-info-border bg-info-bg p-4 text-sm text-info-color">
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
