import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clock3,
  Gauge,
  ListTodo,
  ShieldAlert,
} from 'lucide-react';
import { useParams } from 'react-router';
import type {
  ProjectActivityResponseContract,
  ProjectContract,
} from '@/contracts/workspace-project';
import { yappcApi } from '@/lib/api';
import { useAtomValue } from 'jotai';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import {
  phaseTransitionAPI,
  type PhaseTransitionPreview,
} from '@/services/lifecycle/phase-transition-api';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import {
  getLifecyclePhaseLabel,
  isLifecyclePhase,
  type LifecyclePhase,
} from '../../../shared/types/lifecycle';

function formatActivityTime(timestamp: string): string {
  return new Date(timestamp).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function describePromotionStatus(
  preview: PhaseTransitionPreview | null,
  previewError: string | null
): {
  label: string;
  tone: 'ready' | 'blocked' | 'review' | 'warning';
  detail: string;
} {
  if (previewError) {
    return {
      label: 'Gate status unavailable',
      tone: 'warning',
      detail: previewError,
    };
  }

  if (!preview) {
    return {
      label: 'Checking lifecycle gate',
      tone: 'warning',
      detail: 'Loading the current readiness prediction for the next lifecycle step.',
    };
  }

  if (!preview.nextPhase) {
    return {
      label: 'Lifecycle complete',
      tone: 'ready',
      detail: 'This project is already at the final lifecycle stage.',
    };
  }

  if (preview.canAdvance) {
    return {
      label: `Ready for ${preview.nextPhase}`,
      tone: preview.readiness >= 90 ? 'ready' : 'review',
      detail:
        preview.readiness >= 90
          ? 'The next promotion step is clear. An operator can move this project forward.'
          : 'The next promotion step is open, but it still deserves operator review.',
    };
  }

  return {
    label: 'Blocked before promotion',
    tone: 'blocked',
    detail:
      preview.blockers[0] ??
      'Resolve the listed lifecycle blockers before promoting this project.',
  };
}

function getStatusPanelClassName(
  tone: 'ready' | 'blocked' | 'review' | 'warning'
): string {
  switch (tone) {
    case 'ready':
      return 'border-success-border bg-success-bg text-success-color dark:border-success-border/60 dark:bg-success-bg/40 dark:text-success-color';
    case 'blocked':
      return 'border-destructive-border bg-destructive-bg text-destructive dark:border-destructive-border/60 dark:bg-destructive-bg/40 dark:text-destructive';
    case 'review':
      return 'border-warning-border bg-warning-bg text-warning-color dark:border-warning-border/60 dark:bg-warning-bg/40 dark:text-warning-color';
    default:
      return 'border-border bg-surface-muted text-fg dark:border-border dark:bg-surface/60 dark:text-fg-muted';
  }
}

export default function ProjectIndexRoute() {
  const { projectId } = useParams<{ projectId: string }>();
  const currentWorkspaceId = useAtomValue(currentWorkspaceIdAtom);

  const projectQuery = useQuery<ProjectContract>({
    queryKey: ['project', projectId, currentWorkspaceId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Project id is required to load project overview.');
      }
      if (!currentWorkspaceId) {
        throw new Error('Workspace context is required. Please access this project from within a workspace.');
      }
      return yappcApi.projects.getScoped(projectId, currentWorkspaceId) as unknown as Promise<ProjectContract>;
    },
    enabled: Boolean(projectId) && Boolean(currentWorkspaceId),
  });

  const activityQuery = useQuery<ProjectActivityResponseContract>({
    queryKey: ['project-activity', projectId, currentWorkspaceId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error('Project id is required to load recent project activity.');
      }
      if (!currentWorkspaceId) {
        throw new Error('Workspace context is required. Please access this project from within a workspace.');
      }
      return yappcApi.projects.activity(projectId, currentWorkspaceId) as unknown as Promise<ProjectActivityResponseContract>;
    },
    enabled: Boolean(projectId) && Boolean(currentWorkspaceId),
  });

  const currentPhase = projectQuery.data?.lifecyclePhase;
  const phasePreviewQuery = useQuery<PhaseTransitionPreview>({
    queryKey: ['project-phase-preview', projectId, currentPhase],
    queryFn: async () => {
      if (!currentPhase || !isLifecyclePhase(currentPhase)) {
        throw new Error(
          'Project overview could not determine the current lifecycle phase.'
        );
      }

      return phaseTransitionAPI.getNextPhase(
        currentPhase as unknown as import('../../../types/lifecycle').LifecyclePhase,
        projectId ?? ''
      );
    },
    enabled: Boolean(projectId && currentPhase && isLifecyclePhase(currentPhase)),
    retry: false,
  });

  if (projectQuery.isLoading) {
    return (
      <div className="p-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 w-64 rounded bg-surface-muted" />
          <div className="grid gap-4 md:grid-cols-3">
            <div className="h-32 rounded-xl bg-surface-muted" />
            <div className="h-32 rounded-xl bg-surface-muted" />
            <div className="h-32 rounded-xl bg-surface-muted" />
          </div>
          <div className="h-56 rounded-xl bg-surface-muted" />
        </div>
      </div>
    );
  }

  if (!projectQuery.data) {
    return (
      <div className="p-6">
        <div className="rounded-xl border border-destructive-border bg-destructive-bg p-4 text-sm text-destructive dark:border-destructive-border/60 dark:bg-destructive-bg/40 dark:text-destructive">
          Unable to load the project overview.
        </div>
      </div>
    );
  }

  const project = projectQuery.data;
  const projectPhase: LifecyclePhase | null =
    project.lifecyclePhase && isLifecyclePhase(project.lifecyclePhase)
      ? project.lifecyclePhase
      : null;
  const promotionStatus = describePromotionStatus(
    phasePreviewQuery.data ?? null,
    phasePreviewQuery.error instanceof Error ? phasePreviewQuery.error.message : null
  );
  const recentActivity = activityQuery.data?.activity ?? [];

  return (
    <div className="p-6 space-y-6" data-testid="project-overview-route">
      <section className="rounded-2xl border border-border bg-surface-raised p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-fg-muted">
              Project cockpit
            </p>
            <h1 className="mt-2 text-2xl font-semibold text-fg">{project.name}</h1>
            <p className="mt-2 max-w-3xl text-sm text-fg-muted">
              {project.description?.trim() ||
                'Add a short project description so collaborators understand the scope and operator intent.'}
            </p>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-xl border border-border bg-surface p-4">
              <p className="text-xs font-medium uppercase tracking-wide text-fg-muted">
                Current phase
              </p>
              <p
                className="mt-2 text-lg font-semibold text-fg"
                data-testid="project-overview-phase"
              >
                {projectPhase
                  ? getLifecyclePhaseLabel(projectPhase)
                  : project.lifecyclePhase}
              </p>
              <p className="mt-1 text-xs text-fg-muted">Status: {project.status}</p>
            </div>
            <div className="rounded-xl border border-border bg-surface p-4">
              <p className="text-xs font-medium uppercase tracking-wide text-fg-muted">
                Health score
              </p>
              <p className="mt-2 text-lg font-semibold text-fg">
                {project.aiHealthScore ?? 'N/A'}
              </p>
              <p className="mt-1 text-xs text-fg-muted">
                Rule-based completeness from backed project signals.
              </p>
            </div>
            <div className="rounded-xl border border-border bg-surface p-4">
              <p className="text-xs font-medium uppercase tracking-wide text-fg-muted">
                Last updated
              </p>
              <p className="mt-2 text-lg font-semibold text-fg">
                {formatActivityTime(project.updatedAt)}
              </p>
              <p className="mt-1 text-xs text-fg-muted">
                Owner workspace: {project.ownerWorkspaceId}
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.4fr,1fr]">
        <div className="grid gap-4 md:grid-cols-2">
          <article
            className={[
              'rounded-2xl border p-5 shadow-sm',
              getStatusPanelClassName(promotionStatus.tone),
            ].join(' ')}
            data-testid="project-overview-promotion-status"
          >
            <div className="flex items-start gap-3">
              {promotionStatus.tone === 'ready' ? (
                <CheckCircle2 className="mt-0.5 h-5 w-5" />
              ) : promotionStatus.tone === 'blocked' ? (
                <ShieldAlert className="mt-0.5 h-5 w-5" />
              ) : (
                <Clock3 className="mt-0.5 h-5 w-5" />
              )}
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide">
                  Promotion gate
                </p>
                <h2 className="mt-2 text-lg font-semibold">
                  {promotionStatus.label}
                </h2>
                <p className="mt-2 text-sm opacity-90">{promotionStatus.detail}</p>
                {phasePreviewQuery.data?.nextPhase && (
                  <p className="mt-3 text-xs font-medium opacity-80">
                    Next stage: {phasePreviewQuery.data.currentPhase}{' '}
                    <ArrowRight className="mx-1 inline h-3 w-3" />
                    {phasePreviewQuery.data.nextPhase}
                  </p>
                )}
              </div>
            </div>
          </article>

          <article className="rounded-2xl border border-border bg-surface-raised p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <Gauge className="mt-0.5 h-5 w-5 text-info-color" />
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">
                  Delivery posture
                </p>
                <h2 className="mt-2 text-lg font-semibold text-fg">
                  {phasePreviewQuery.data?.canAdvance
                    ? 'Promotion planning enabled'
                    : 'Hold deployment work behind the lifecycle gate'}
                </h2>
                <p className="mt-2 text-sm text-fg-muted">
                  {phasePreviewQuery.data?.estimatedReadyIn
                    ? `Readiness forecast: ${phasePreviewQuery.data.estimatedReadyIn}.`
                    : 'No readiness forecast is available yet.'}
                </p>
                {(phasePreviewQuery.data?.blockers.length ?? 0) > 0 && (
                  <ul
                    className="mt-3 space-y-2 text-sm text-fg-muted"
                    data-testid="project-overview-blockers"
                  >
                    {phasePreviewQuery.data?.blockers.slice(0, 3).map((blocker) => (
                      <li key={blocker} className="flex items-start gap-2">
                        <AlertTriangle className="mt-0.5 h-4 w-4 text-warning-color" />
                        <span>{blocker}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </article>

          <article className="rounded-2xl border border-border bg-surface-raised p-5 shadow-sm md:col-span-2">
            <div className="flex items-start gap-3">
              <ListTodo className="mt-0.5 h-5 w-5 text-info-color" />
              <div className="w-full">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">
                  Automation guidance
                </p>
                <h2 className="mt-2 text-lg font-semibold text-fg">What to do next</h2>
                {project.aiNextActions?.length ? (
                  <ul className="mt-3 grid gap-3 md:grid-cols-2">
                    {project.aiNextActions.slice(0, 4).map((action) => (
                      <li
                        key={action}
                        className="rounded-xl border border-border bg-surface p-3 text-sm text-fg-muted"
                      >
                        {action}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="mt-2 text-sm text-fg-muted">
                    No next actions are available yet. Refresh project guidance after the next backed change.
                  </p>
                )}
              </div>
            </div>
          </article>
        </div>

        <article
          className="rounded-2xl border border-border bg-surface-raised p-5 shadow-sm"
          data-testid="project-overview-timeline"
        >
          <h2 className="text-lg font-semibold text-fg">Recent activity</h2>
          <p className="mt-1 text-sm text-fg-muted">
            Lifecycle and audit events that are currently backed by the mounted API surface.
          </p>
          {activityQuery.isLoading ? (
            <div className="mt-4 space-y-3">
              <div className="h-16 rounded-xl bg-surface-muted animate-pulse" />
              <div className="h-16 rounded-xl bg-surface-muted animate-pulse" />
            </div>
          ) : activityQuery.isError ? (
            <div className="mt-4 rounded-xl border border-destructive-border bg-destructive-bg p-4 text-sm text-destructive dark:border-destructive-border/60 dark:bg-destructive-bg/40 dark:text-destructive">
              {activityQuery.error instanceof Error
                ? activityQuery.error.message
                : 'Failed to load project activity.'}
            </div>
          ) : recentActivity.length ? (
            <ol className="mt-4 space-y-3">
              {recentActivity.map((entry) => (
                <li key={entry.id} className="rounded-xl border border-border bg-surface p-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-semibold uppercase tracking-wide text-fg-muted">
                      {entry.source}
                    </span>
                    <span className="text-xs text-fg-muted">
                      {formatActivityTime(entry.timestamp)}
                    </span>
                  </div>
                  <p className="mt-2 text-sm font-medium text-fg">{entry.action}</p>
                  <p className="mt-1 text-sm text-fg-muted">{entry.summary}</p>
                  {entry.actor && (
                    <p className="mt-2 text-xs text-fg-muted">Actor: {entry.actor}</p>
                  )}
                </li>
              ))}
            </ol>
          ) : (
            <div className="mt-4 rounded-xl border border-dashed border-border p-4 text-sm text-fg-muted">
              No backed project activity is available yet. New lifecycle changes and audit events will appear here.
            </div>
          )}
        </article>
      </section>
    </div>
  );
}

export function ErrorBoundary() {
  return (
    <RouteErrorBoundary
      message="Unable to load the mounted project overview."
      title="Project Overview Error"
    />
  );
}
