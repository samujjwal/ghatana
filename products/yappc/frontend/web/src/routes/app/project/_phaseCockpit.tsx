import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import type {
  ProjectActivityEventContract,
  ProjectActivityResponseContract,
  ProjectContract,
} from '@/contracts/workspace-project';
import { parseJsonResourceResponse } from '@/lib/http';
import {
  phaseTransitionAPI,
  type PhaseTransitionPreview,
} from '@/services/lifecycle/phase-transition-api';
import {
  PhaseBlockerPanel,
  PhaseCockpitLayout,
  PhaseEvidencePanel,
  PhaseGovernanceTrace,
  PhasePrimaryActionCard,
  PhaseSuggestedNextStep,
  type Blocker,
  type EvidenceItem,
  type GovernanceRecord,
  type SuggestedStep,
} from '../../../components/phase';
import { ProvenanceBadge } from '../../../components/shared/ProvenanceBadge';
import ProjectOverviewRoute from './index';
import LifecycleRoute from './lifecycle';
import CanvasRoute from './canvas';
import DeployRoute from './deploy';
import PreviewRoute from './preview';

type MountedPhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

interface PhaseConfig {
  name: string;
  description: string;
  primaryTitle: string;
  primaryDescription: string;
  primaryLabel: string;
  primaryTestId: string;
  secondaryLabel: string;
  secondaryTestId: string;
  icon: string;
  supportingTitle: string;
  actionFeedback: string;
}

const PHASE_CONFIG: Record<MountedPhase, PhaseConfig> = {
  intent: {
    name: 'Intent',
    description: 'Clarify the goal, the problem to solve, and the evidence that supports why this work matters.',
    primaryTitle: 'Define Requirements',
    primaryDescription: 'Capture the user outcome and problem framing so later phases inherit a clear intent.',
    primaryLabel: 'Define Requirements',
    primaryTestId: 'define-requirements',
    secondaryLabel: 'Review Evidence',
    secondaryTestId: 'review-evidence',
    icon: '🎯',
    supportingTitle: 'Project overview',
    actionFeedback: 'The intent support surface below is ready for goal and evidence review.',
  },
  shape: {
    name: 'Shape',
    description: 'Turn intent into structure by shaping the UI, information flow, and component composition.',
    primaryTitle: 'Add Components',
    primaryDescription: 'Open the builder surface below and shape the page structure with real components.',
    primaryLabel: 'Add Components',
    primaryTestId: 'add-components',
    secondaryLabel: 'Review Requirements',
    secondaryTestId: 'review-requirements',
    icon: '🎨',
    supportingTitle: 'Canvas and page builder',
    actionFeedback: 'The canvas surface below is ready for component and layout work.',
  },
  validate: {
    name: 'Validate',
    description: 'Review whether the shaped solution is consistent, ready, and safe to approve for generation.',
    primaryTitle: 'Approve Changes',
    primaryDescription: 'Use the gate and blocker evidence below to decide whether this packet is ready to move forward.',
    primaryLabel: 'Approve Changes',
    primaryTestId: 'approve-changes',
    secondaryLabel: 'Request Changes',
    secondaryTestId: 'request-changes',
    icon: '✓',
    supportingTitle: 'Validation and lifecycle review',
    actionFeedback: 'Validation details below now reflect the latest gate summary for this phase.',
  },
  generate: {
    name: 'Generate',
    description: 'Prepare the implementation handoff from approved design into generated output and reviewable diffs.',
    primaryTitle: 'Generate Code',
    primaryDescription: 'Review the supporting builder context below before kicking off implementation work.',
    primaryLabel: 'Generate Code',
    primaryTestId: 'generate-code',
    secondaryLabel: 'Preview Codegen Plan',
    secondaryTestId: 'view-codegen-preview',
    icon: '⚙️',
    supportingTitle: 'Implementation context',
    actionFeedback: 'Implementation context below is ready for code generation review.',
  },
  run: {
    name: 'Run',
    description: 'Check readiness and execute the safest available run or deployment path for this project.',
    primaryTitle: 'Check Readiness',
    primaryDescription: 'Review the deployment posture and capability gates before attempting any release action.',
    primaryLabel: 'Check Readiness',
    primaryTestId: 'check-readiness',
    secondaryLabel: 'View Run Plan',
    secondaryTestId: 'view-run-plan',
    icon: '🚀',
    supportingTitle: 'Deployment and run plan',
    actionFeedback: 'Run readiness and deployment planning are surfaced below.',
  },
  observe: {
    name: 'Observe',
    description: 'Watch preview health, activity, and operator signals to understand how the current build behaves.',
    primaryTitle: 'View Metrics',
    primaryDescription: 'Use the preview and observability evidence below to decide what needs attention first.',
    primaryLabel: 'View Metrics',
    primaryTestId: 'view-metrics',
    secondaryLabel: 'View Project Preview',
    secondaryTestId: 'view-project-preview',
    icon: '👁️',
    supportingTitle: 'Preview and observation',
    actionFeedback: 'Preview and metrics surfaces below are ready for review.',
  },
  learn: {
    name: 'Learn',
    description: 'Capture what worked, what failed, and which reusable patterns should inform the next cycle.',
    primaryTitle: 'Capture Learnings',
    primaryDescription: 'Review the latest evidence below and record the most useful lessons from this iteration.',
    primaryLabel: 'Capture Learnings',
    primaryTestId: 'capture-learnings',
    secondaryLabel: 'View Retrospective',
    secondaryTestId: 'view-retrospective',
    icon: '💡',
    supportingTitle: 'Retrospective and insights',
    actionFeedback: 'Retrospective support below is ready for learnings capture.',
  },
  evolve: {
    name: 'Evolve',
    description: 'Convert validated learnings into the next cycle plan, roadmap changes, and backlog priorities.',
    primaryTitle: 'Plan Next Cycle',
    primaryDescription: 'Use the backed evidence below to decide what the next improvement cycle should prioritize.',
    primaryLabel: 'Plan Next Cycle',
    primaryTestId: 'plan-next-cycle',
    secondaryLabel: 'Review Backlog',
    secondaryTestId: 'view-roadmap',
    icon: '🔄',
    supportingTitle: 'Roadmap and backlog',
    actionFeedback: 'Roadmap and backlog support below are ready for the next cycle plan.',
  },
};

function formatTimestamp(timestamp: string): string {
  return new Date(timestamp).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function buildBlockers(
  phase: MountedPhase,
  project: ProjectContract,
  preview: PhaseTransitionPreview | null,
): Blocker[] {
  const projectAny = project as ProjectContract & {
    description?: string | null;
  };
  const blockers: Blocker[] = [];

  if (!projectAny.description?.trim()) {
    blockers.push({
      id: `${phase}-missing-description`,
      title: 'Project description missing',
      description: 'Add a short description so collaborators understand the scope and intended outcome.',
      severity: 'medium',
    });
  }

  if (preview && !preview.canAdvance) {
    preview.blockers.forEach((blocker, index) => {
      blockers.push({
        id: `${phase}-gate-${index}`,
        title: 'Lifecycle gate still blocked',
        description: blocker,
        severity: index === 0 ? 'high' : 'medium',
      });
    });
  }

  if (phase === 'run' && !preview?.nextPhase) {
    blockers.push({
      id: 'run-final-phase',
      title: 'No further promotion step',
      description: 'This project is already at the final available lifecycle phase.',
      severity: 'low',
    });
  }

  return blockers;
}

function buildEvidence(
  phase: MountedPhase,
  project: ProjectContract,
  activity: ProjectActivityEventContract[],
  preview: PhaseTransitionPreview | null,
): EvidenceItem[] {
  const evidence: EvidenceItem[] = [];
  const projectAny = project as ProjectContract & {
    updatedAt?: string;
    lifecyclePhase?: string;
    aiHealthScore?: number | null;
  };

  if (projectAny.lifecyclePhase) {
    evidence.push({
      id: `${phase}-current-phase`,
      type: 'artifact',
      title: 'Current lifecycle phase',
      description: 'Backed lifecycle state from the project record.',
      value: projectAny.lifecyclePhase,
      source: 'project API',
      timestamp: projectAny.updatedAt,
    });
  }

  if (typeof projectAny.aiHealthScore === 'number') {
    evidence.push({
      id: `${phase}-health-score`,
      type: 'metric',
      title: 'Project health score',
      description: 'Current health signal derived from the mounted project overview response.',
      value: projectAny.aiHealthScore,
      source: 'project API',
      timestamp: projectAny.updatedAt,
    });
  }

  if (preview) {
    evidence.push({
      id: `${phase}-readiness`,
      type: 'metric',
      title: 'Readiness prediction',
      description: preview.canAdvance
        ? 'Lifecycle checks currently allow promotion to the next step.'
        : 'Lifecycle checks still report blockers for the next step.',
      value: `${Math.round(preview.readiness ?? 0)}%`,
      source: 'phase transition API',
      timestamp: preview.checkedAt,
    });
  }

  activity.slice(0, 3).forEach((entry) => {
    evidence.push({
      id: `${phase}-activity-${entry.id}`,
      type: entry.source === 'audit' ? 'artifact' : 'observation',
      title: entry.action,
      description: entry.summary,
      source: entry.source,
      timestamp: entry.timestamp,
    });
  });

  return evidence;
}

function buildGovernance(activity: ProjectActivityEventContract[]): GovernanceRecord[] {
  return activity.slice(0, 5).map((entry) => ({
    id: entry.id,
    artifactId: entry.id,
    action: entry.action,
    actor: entry.actor ?? 'system',
    timestamp: entry.timestamp,
    reviewState: entry.success === false ? 'rejected' : 'approved',
    source: entry.source === 'audit' ? 'backed' : 'derived',
    metadata: {
      summary: entry.summary,
      severity: entry.severity ?? undefined,
    },
  }));
}

function buildSuggestedSteps(
  phase: MountedPhase,
  project: ProjectContract,
  onAccept: () => void,
): SuggestedStep[] {
  const nextActions = ((project as ProjectContract & { aiNextActions?: string[] }).aiNextActions ?? [])
    .slice(0, 2)
    .map((title, index) => ({
      id: `${phase}-suggested-${index}`,
      title,
      description: 'Derived from the backed project overview response and ready for operator review.',
      type: 'review' as const,
      estimatedTime: '5 min',
      onAccept,
    }));

  if (nextActions.length > 0) {
    return nextActions;
  }

  return [
    {
      id: `${phase}-default-suggestion`,
      title: 'Review the mounted supporting surface',
      description: 'Use the embedded route below to gather the evidence needed for the next decision.',
      type: 'manual',
      estimatedTime: '3 min',
      onAccept,
    },
  ];
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
    case 'learn':
    case 'evolve':
    default:
      return <LifecycleRoute />;
  }
}

function renderPhaseStatusPanels(
  phase: MountedPhase,
  preview: PhaseTransitionPreview | null,
  blockers: Blocker[],
  activity: ProjectActivityEventContract[],
): React.ReactNode {
  if (phase === 'validate') {
    const requiredApprovals = preview?.requiredArtifacts ?? [];

    return (
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="validation-status">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Validation status</p>
          <p className="mt-2 text-lg font-semibold text-fg">
            {blockers.length === 0 ? 'Passed' : preview?.canAdvance ? 'Pending review' : 'Failed'}
          </p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="approval-gates">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Approval gates</p>
          {requiredApprovals.length > 0 ? (
            <div className="mt-3 space-y-2">
              {requiredApprovals.map((item) => (
                <div key={item} data-testid="required-approval" className="flex items-center gap-2 text-sm text-fg">
                  <ProvenanceBadge type="backed" size="sm" label="Required" />
                  <span>{item}</span>
                </div>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-fg-muted" data-testid="required-approval-empty">
              No explicit approval artifacts are currently reported by lifecycle preview.
            </p>
          )}
        </div>
      </div>
    );
  }

  if (phase === 'generate') {
    const outputBundle = preview?.requiredArtifacts ?? [];
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="codegen-preview-panel">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Codegen preview</p>
          <p className="mt-2 text-sm text-fg-muted">
            The builder surface below is mounted so generation can operate on the latest design context.
          </p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="generated-file-list">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Planned output bundle</p>
          {outputBundle.length > 0 ? (
            <div className="mt-3 space-y-2">
              {outputBundle.map((item) => (
                <div key={item} data-testid="generated-file" className="text-sm text-fg">
                  {item}
                </div>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-fg-muted" data-testid="generated-file-empty">
              Generation output details will appear once preview planning is available.
            </p>
          )}
        </div>
      </div>
    );
  }

  if (phase === 'run') {
    const requiredCapabilities = preview?.requiredArtifacts ?? [];
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="capability-gates">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Capability gates</p>
          {requiredCapabilities.length > 0 ? (
            <div className="mt-3 space-y-2">
              {requiredCapabilities.map((item) => (
                <div key={item} data-testid="required-capability" className="text-sm text-fg">
                  {item}
                </div>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-fg-muted" data-testid="required-capability-empty">
              No additional capability gates are currently reported for this run transition.
            </p>
          )}
        </div>
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="run-plan-panel">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Run plan</p>
          <p className="mt-2 text-sm text-fg-muted" data-testid="pipeline-readiness">
            {preview?.canAdvance ? 'Ready with operator review' : 'Not ready until blockers are cleared'}
          </p>
        </div>
      </div>
    );
  }

  if (phase === 'observe') {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="metrics-panel">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Metrics summary</p>
          <p className="mt-2 text-sm text-fg-muted">
            {activity.length > 0
              ? `Tracking ${activity.length} recent backed lifecycle or audit events.`
              : 'No recent backed events have been recorded yet.'}
          </p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="incidents-panel">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Incidents</p>
          <p className="mt-2 text-sm text-fg-muted">
            {blockers.length > 0 ? `${blockers.length} issue(s) need review before promotion.` : 'No active incident-level blockers are surfaced here.'}
          </p>
        </div>
      </div>
    );
  }

  if (phase === 'learn') {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="retrospective-panel">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Retrospective</p>
          <p className="mt-2 text-sm text-fg-muted">
            Review the lifecycle evidence below to capture operator learnings and lessons from this cycle.
          </p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="reusable-patterns">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Reusable patterns</p>
          <p className="mt-2 text-sm text-fg-muted">
            Promote stable learnings into repeatable delivery patterns once they have enough backing evidence.
          </p>
        </div>
      </div>
    );
  }

  if (phase === 'evolve') {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="roadmap-panel">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Roadmap</p>
          <p className="mt-2 text-sm text-fg-muted">
            Use the learnings and blockers below to decide which work belongs in the next improvement cycle.
          </p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4" data-testid="backlog-panel">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Backlog</p>
          <p className="mt-2 text-sm text-fg-muted">
            The backlog should stay aligned with backed evidence and the highest-value next action.
          </p>
        </div>
      </div>
    );
  }

  return null;
}

function PhaseCockpitRoute({ phase }: { phase: MountedPhase }) {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [feedback, setFeedback] = useState<string | null>(null);
  const config = PHASE_CONFIG[phase];

  const projectQuery = useQuery<ProjectContract>({
    queryKey: ['project', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}`);
      if (!response.ok) {
        throw new Error(`Failed to load the ${config.name.toLowerCase()} cockpit.`);
      }

      return parseJsonResourceResponse<ProjectContract>(
        response,
        `${config.name.toLowerCase()} cockpit`,
        'project',
      );
    },
    enabled: Boolean(projectId),
  });

  const activityQuery = useQuery<ProjectActivityResponseContract>({
    queryKey: ['project-activity', projectId],
    queryFn: async () => {
      const response = await fetch(`/api/projects/${projectId}/activity`);
      if (!response.ok) {
        throw new Error('Failed to load recent project activity.');
      }

      return (await response.json()) as ProjectActivityResponseContract;
    },
    enabled: Boolean(projectId),
  });

  const projectPhase = (projectQuery.data as ProjectContract & { lifecyclePhase?: string } | undefined)
    ?.lifecyclePhase;

  const previewQuery = useQuery<PhaseTransitionPreview>({
    queryKey: ['project-phase-preview', projectId, projectPhase],
    queryFn: async () => {
      if (!projectId || !projectPhase) {
        throw new Error('Missing lifecycle phase context.');
      }

      return phaseTransitionAPI.getNextPhase(
        projectPhase as import('../../../types/lifecycle').LifecyclePhase,
        projectId,
      );
    },
    enabled: Boolean(projectId && projectPhase),
    retry: false,
  });

  const scrollToSupportingSurface = () => {
    document.getElementById(`${phase}-supporting-surface`)?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  };

  const handlePrimaryAction = () => {
    if (phase === 'intent' && projectId) {
      navigate(`/p/${projectId}/intent?drawer=idea`);
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
  const activity = activityQuery.data?.activity ?? [];
  const preview = previewQuery.data ?? null;

  const blockers = useMemo(
    () => (project ? buildBlockers(phase, project, preview) : []),
    [phase, project, preview],
  );
  const evidence = useMemo(
    () => (project ? buildEvidence(phase, project, activity, preview) : []),
    [activity, phase, preview, project],
  );
  const governance = useMemo(() => buildGovernance(activity), [activity]);
  const suggestions = useMemo(
    () => (project ? buildSuggestedSteps(phase, project, scrollToSupportingSurface) : []),
    [phase, project],
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

  const projectName = (project as ProjectContract & { name?: string }).name ?? 'this project';
  const statusPanels = renderPhaseStatusPanels(phase, preview, blockers, activity);

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
      >
        <div className="space-y-4">
          {feedback ? (
            <div className="rounded-xl border border-sky-200 bg-sky-50 p-4 text-sm text-sky-900">
              {feedback}
            </div>
          ) : null}
          {statusPanels}
          <div
            id={`${phase}-supporting-surface`}
            className="rounded-2xl border border-border bg-surface-raised p-4 shadow-sm"
          >
            <div className="mb-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-fg-muted">
                Supporting surface
              </p>
              <h2 className="mt-2 text-lg font-semibold text-fg">{config.supportingTitle}</h2>
              <p className="mt-1 text-sm text-fg-muted">
                Mounted from the existing project route so each phase uses a cockpit first and a detailed surface second.
              </p>
            </div>
            <div className="text-xs text-fg-muted mb-4">
              Last activity:{' '}
              {activity[0]?.timestamp ? formatTimestamp(activity[0].timestamp) : 'No recent backed activity'}
            </div>
            {renderEmbeddedSurface(phase)}
          </div>
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
