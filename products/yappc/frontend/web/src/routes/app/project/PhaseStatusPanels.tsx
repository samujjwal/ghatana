import React from 'react';

import { Badge, Card, CardContent } from '@ghatana/design-system';

import type { Blocker } from '../../../components/phase/PhaseBlockerPanel';
import { ProvenanceBadge } from '../../../components/shared/ProvenanceBadge';
import type {
  MountedPhase,
  PhaseActivityEvent,
  PhaseTransitionPreviewSnapshot,
} from '../../../services/phase';
import type { AgentGovernanceHealth, PreviewHealth } from '../../../types/phasePacket';

interface PhaseStatusPanelsProps {
  phase: MountedPhase;
  preview: PhaseTransitionPreviewSnapshot | null;
  previewHealth?: PreviewHealth;
  agentGovernance?: AgentGovernanceHealth;
  blockers: Blocker[];
  activity: PhaseActivityEvent[];
}

type ObservePreviewHealth = 'healthy' | 'degraded' | 'down' | 'unknown';
type ObservePreviewDiagnosticKind =
  | 'runtime-error'
  | 'console-log'
  | 'policy-block'
  | 'load-timing'
  | 'user-action';

interface ObservePreviewDiagnostic {
  readonly id: string;
  readonly kind: ObservePreviewDiagnosticKind;
  readonly title: string;
  readonly detail: string;
  readonly timestamp?: string;
  readonly severity: 'info' | 'warning' | 'error';
  readonly actor?: string | null;
  readonly latencyMs?: number;
}

interface ObservePreviewDiagnostics {
  readonly health: ObservePreviewHealth;
  readonly runtimeErrors: readonly ObservePreviewDiagnostic[];
  readonly consoleLogs: readonly ObservePreviewDiagnostic[];
  readonly policyBlocks: readonly ObservePreviewDiagnostic[];
  readonly loadTimings: readonly ObservePreviewDiagnostic[];
  readonly userActionTrace: readonly ObservePreviewDiagnostic[];
}

interface ObserveHealthItem {
  readonly id: 'kernel' | 'app' | 'agent';
  readonly label: string;
  readonly status: 'healthy' | 'degraded' | 'failed' | 'unknown';
  readonly detail: string;
}

interface ObserveRecommendation {
  readonly id: string;
  readonly title: string;
  readonly detail: string;
}

interface LearnEvidenceItem {
  readonly id: string;
  readonly category: 'run' | 'approval' | 'prompt' | 'agent' | 'general';
  readonly summary: string;
  readonly trace: string;
}

interface LearnRecommendation {
  readonly id: string;
  readonly title: string;
  readonly detail: string;
  readonly status: 'ready' | 'needs-review' | 'blocked';
}

interface LearnApprovalItem {
  readonly id: string;
  readonly decision: 'approved' | 'rejected' | 'pending';
  readonly detail: string;
  readonly trace: string;
}

interface LearnPromptSignal {
  readonly id: string;
  readonly title: string;
  readonly detail: string;
  readonly trace: string;
}

interface EvolveSignal {
  readonly id: string;
  readonly title: string;
  readonly detail: string;
  readonly trace: string;
  readonly status: 'ready' | 'needs-review' | 'blocked';
}

type GenerateAssuranceStatus = 'passed' | 'failed' | 'warning' | 'pending';

interface GenerateAssuranceCheck {
  readonly id: string;
  readonly label: string;
  readonly status: GenerateAssuranceStatus;
  readonly detail: string;
  readonly evidenceId?: string;
}

interface GenerateSignal {
  readonly id: string;
  readonly summary: string;
  readonly trace: string;
}

type RunHealthStatus = 'healthy' | 'degraded' | 'failed' | 'unknown';
type RunOperation = 'retry' | 'rollback' | 'promote';

interface RunTimelineEvent {
  readonly id: string;
  readonly summary: string;
  readonly status: RunHealthStatus;
  readonly trace: string;
}

const OBSERVE_HEALTH_LABEL: Record<ObservePreviewHealth, string> = {
  healthy: 'Healthy',
  degraded: 'Degraded',
  down: 'Down',
  unknown: 'Unknown',
};

const GENERATE_ASSURANCE_CHECKS = [
  { id: 'compile', label: 'Compile', keywords: ['compile', 'build', 'typecheck', 'type-check'] },
  { id: 'test', label: 'Tests', keywords: ['test', 'vitest', 'jest', 'playwright'] },
  { id: 'static', label: 'Static analysis', keywords: ['lint', 'static', 'scan'] },
  { id: 'security', label: 'Security', keywords: ['security', 'sast', 'dependency', 'vulnerability'] },
  { id: 'i18n', label: 'i18n', keywords: ['i18n', 'translation', 'locale'] },
  { id: 'a11y', label: 'Accessibility', keywords: ['a11y', 'accessibility', 'axe', 'wcag'] },
] as const;

function getObserveEventText(event: PhaseActivityEvent): string {
  return `${event.action} ${event.summary}`.toLowerCase();
}

function eventSeverity(event: PhaseActivityEvent): ObservePreviewDiagnostic['severity'] {
  const severity = event.severity?.toLowerCase();
  if (event.success === false || severity === 'error' || severity === 'critical') {
    return 'error';
  }

  if (severity === 'warning') {
    return 'warning';
  }

  return 'info';
}

function extractLatencyMs(event: PhaseActivityEvent): number | null {
  const latencyMatch = /(?:latency|load|loaded|reload|ready|mounted)[^\d]*(\d+(?:\.\d+)?)\s*ms/i.exec(event.summary);
  if (!latencyMatch?.[1]) {
    return null;
  }

  return Math.round(Number(latencyMatch[1]));
}

function activityText(event: PhaseActivityEvent): string {
  return `${event.action} ${event.summary}`.toLowerCase();
}

function generateAssuranceStatus(event: PhaseActivityEvent): GenerateAssuranceStatus {
  const severity = event.severity?.toLowerCase();
  if (event.success === false || severity === 'error' || severity === 'critical') {
    return 'failed';
  }
  if (severity === 'warning') {
    return 'warning';
  }
  return 'passed';
}

function buildGenerateAssuranceChecks(activity: readonly PhaseActivityEvent[]): readonly GenerateAssuranceCheck[] {
  return GENERATE_ASSURANCE_CHECKS.map((definition) => {
    const event = activity.find((candidate) => {
      const text = activityText(candidate);
      return definition.keywords.some((keyword) => text.includes(keyword));
    });

    if (!event) {
      return {
        id: definition.id,
        label: definition.label,
        status: 'pending',
        detail: 'No assurance evidence has been reported yet.',
      };
    }

    return {
      id: definition.id,
      label: definition.label,
      status: generateAssuranceStatus(event),
      detail: event.summary,
      evidenceId: event.id,
    };
  });
}

function generateSignals(
  activity: readonly PhaseActivityEvent[],
  predicate: (event: PhaseActivityEvent) => boolean,
): readonly GenerateSignal[] {
  return activity
    .filter(predicate)
    .slice(0, 5)
    .map((event) => ({
      id: event.id,
      summary: event.summary,
      trace: formatActivityTrace(event),
    }));
}

function isGenerateArtifactEvent(event: PhaseActivityEvent): boolean {
  const text = activityText(event);
  return text.includes('artifact') || text.includes('generated file') || text.includes('file created');
}

function isGenerateDiffEvent(event: PhaseActivityEvent): boolean {
  const text = activityText(event);
  return text.includes('diff') || text.includes('patch') || text.includes('changed file');
}

function isGenerateFailureEvent(event: PhaseActivityEvent): boolean {
  const severity = event.severity?.toLowerCase();
  return event.success === false || severity === 'error' || severity === 'critical';
}

function runHealthStatus(event: PhaseActivityEvent): RunHealthStatus {
  const severity = event.severity?.toLowerCase();
  const text = activityText(event);
  if (event.success === false || severity === 'error' || severity === 'critical' || text.includes('failed')) {
    return 'failed';
  }
  if (severity === 'warning' || text.includes('degraded') || text.includes('rollback')) {
    return 'degraded';
  }
  if (event.success === true || text.includes('succeeded') || text.includes('healthy')) {
    return 'healthy';
  }
  return 'unknown';
}

function buildRunTimeline(activity: readonly PhaseActivityEvent[]): readonly RunTimelineEvent[] {
  return activity
    .filter((event) => {
      const text = activityText(event);
      return text.includes('run') || text.includes('preview') || text.includes('deploy') || text.includes('promote') || text.includes('rollback');
    })
    .slice(0, 6)
    .map((event) => ({
      id: event.id,
      summary: event.summary,
      status: runHealthStatus(event),
      trace: formatActivityTrace(event),
    }));
}

function resolveRunStatus(
  activity: readonly PhaseActivityEvent[],
  blockers: readonly Blocker[],
  preview: PhaseTransitionPreviewSnapshot | null,
): RunHealthStatus {
  if (activity.some((event) => runHealthStatus(event) === 'failed')) {
    return 'failed';
  }
  if (blockers.length > 0 || activity.some((event) => runHealthStatus(event) === 'degraded')) {
    return 'degraded';
  }
  if (preview?.canAdvance === true || activity.some((event) => runHealthStatus(event) === 'healthy')) {
    return 'healthy';
  }
  return 'unknown';
}

function runOperationState(
  operation: RunOperation,
  runStatus: RunHealthStatus,
  activity: readonly PhaseActivityEvent[],
): 'available' | 'blocked' | 'ready' {
  const hasOperationEvidence = activity.some((event) => activityText(event).includes(operation));
  if (hasOperationEvidence) {
    return 'available';
  }
  if (operation === 'retry') {
    return runStatus === 'failed' || runStatus === 'degraded' ? 'ready' : 'blocked';
  }
  if (operation === 'rollback') {
    return runStatus === 'failed' ? 'ready' : 'blocked';
  }
  return runStatus === 'healthy' ? 'ready' : 'blocked';
}

function formatActivityTrace(event: PhaseActivityEvent): string {
  const parts = [
    event.actor ? `Actor: ${event.actor}` : null,
    event.timestamp ? `Time: ${new Date(event.timestamp).toLocaleString()}` : null,
    event.eventType ? `Event: ${event.eventType}` : null,
    event.outcome ? `Outcome: ${event.outcome}` : event.success === false ? 'Outcome: FAILURE' : null,
    event.correlationId ? `Correlation ID: ${event.correlationId}` : null,
  ].filter(Boolean);

  return parts.join(' | ');
}

function activityDiagnostic(
  event: PhaseActivityEvent,
  kind: ObservePreviewDiagnosticKind,
  title: string,
  latencyMs?: number,
): ObservePreviewDiagnostic {
  return {
    id: `${kind}-${event.id}`,
    kind,
    title,
    detail: event.summary,
    timestamp: event.timestamp,
    severity: eventSeverity(event),
    actor: event.actor,
    latencyMs,
  };
}

function buildObservePreviewDiagnostics(
  activity: readonly PhaseActivityEvent[],
  blockers: readonly Blocker[],
  preview: PhaseTransitionPreviewSnapshot | null,
): ObservePreviewDiagnostics {
  const runtimeErrors: ObservePreviewDiagnostic[] = [];
  const consoleLogs: ObservePreviewDiagnostic[] = [];
  const policyBlocks: ObservePreviewDiagnostic[] = [];
  const loadTimings: ObservePreviewDiagnostic[] = [];
  const userActionTrace: ObservePreviewDiagnostic[] = [];

  activity.forEach((event) => {
    const eventText = getObserveEventText(event);

    if (
      eventText.includes('preview.runtime.error')
      || (eventText.includes('preview') && eventText.includes('runtime') && eventText.includes('error'))
    ) {
      runtimeErrors.push(activityDiagnostic(event, 'runtime-error', 'Preview runtime error'));
    }

    if (eventText.includes('preview.console') || eventText.includes('console log') || eventText.includes('console warning')) {
      consoleLogs.push(activityDiagnostic(event, 'console-log', 'Preview console log'));
    }

    if (
      (eventText.includes('preview') || eventText.includes('sandbox') || eventText.includes('csp'))
      && eventText.includes('policy')
      && (eventText.includes('block') || eventText.includes('deny') || eventText.includes('refus'))
    ) {
      policyBlocks.push(activityDiagnostic(event, 'policy-block', 'Preview policy block'));
    }

    const latencyMs = extractLatencyMs(event);
    if (
      latencyMs != null
      && eventText.includes('preview')
      && (eventText.includes('load') || eventText.includes('reload') || eventText.includes('mount') || eventText.includes('ready'))
    ) {
      loadTimings.push(activityDiagnostic(event, 'load-timing', 'Preview load timing', latencyMs));
    }

    if (event.actor && event.actor !== 'system' && (eventText.includes('preview') || eventText.includes('observe'))) {
      userActionTrace.push(activityDiagnostic(event, 'user-action', 'Preview user action'));
    }
  });

  blockers.forEach((blocker) => {
    const blockerText = `${blocker.title} ${blocker.description}`.toLowerCase();
    if (
      blockerText.includes('policy')
      || blockerText.includes('sandbox')
      || blockerText.includes('csp')
      || blockerText.includes('preview block')
    ) {
      policyBlocks.push({
        id: `policy-block-${blocker.id}`,
        kind: 'policy-block',
        title: blocker.title,
        detail: blocker.description,
        severity: blocker.severity === 'critical' || blocker.severity === 'high' ? 'error' : 'warning',
      });
    }
  });

  const health: ObservePreviewHealth = runtimeErrors.length > 0 || policyBlocks.some((item) => item.severity === 'error')
    ? 'down'
    : policyBlocks.length > 0 || consoleLogs.some((item) => item.severity !== 'info') || preview?.canAdvance === false
      ? 'degraded'
      : preview
        ? 'healthy'
        : 'unknown';

  return {
    health,
    runtimeErrors,
    consoleLogs,
    policyBlocks,
    loadTimings,
    userActionTrace,
  };
}

function observeStatusFromText(text: string): ObserveHealthItem['status'] {
  if (text.includes('failed') || text.includes('down') || text.includes('error')) {
    return 'failed';
  }
  if (text.includes('degraded') || text.includes('blocked') || text.includes('warning')) {
    return 'degraded';
  }
  if (text.includes('healthy') || text.includes('passed') || text.includes('ready')) {
    return 'healthy';
  }
  return 'unknown';
}

function buildObserveHealthItems(
  activity: readonly PhaseActivityEvent[],
  blockers: readonly Blocker[],
  previewDiagnostics: ObservePreviewDiagnostics,
  agentGovernance?: AgentGovernanceHealth,
): readonly ObserveHealthItem[] {
  const kernelSignals = [
    ...activity.filter((event) => activityText(event).includes('kernel')),
    ...blockers.filter((blocker) => `${blocker.title ?? ''} ${blocker.description}`.toLowerCase().includes('kernel')),
  ];
  const kernelText = kernelSignals
    .map((signal) => 'summary' in signal ? activityText(signal) : `${signal.title ?? ''} ${signal.description}`.toLowerCase())
    .join(' ');
  const kernelStatus = kernelSignals.length > 0 ? observeStatusFromText(kernelText) : 'unknown';

  const appStatus: ObserveHealthItem['status'] =
    previewDiagnostics.health === 'down'
      ? 'failed'
      : previewDiagnostics.health === 'degraded'
        ? 'degraded'
        : previewDiagnostics.health === 'healthy'
          ? 'healthy'
          : 'unknown';

  const agentStatus: ObserveHealthItem['status'] = agentGovernance
    ? agentGovernance.isHealthy
      ? 'healthy'
      : 'degraded'
    : 'unknown';

  return [
    {
      id: 'kernel',
      label: 'Kernel health',
      status: kernelStatus,
      detail: kernelSignals.length > 0 ? 'Kernel truth signals are present in backed evidence.' : 'No Kernel health signal has been reported yet.',
    },
    {
      id: 'app',
      label: 'App health',
      status: appStatus,
      detail: `Preview runtime is ${previewDiagnostics.health}.`,
    },
    {
      id: 'agent',
      label: 'Agent health',
      status: agentStatus,
      detail: agentGovernance
        ? `Agent governance is ${agentGovernance.governanceState}.`
        : 'No agent governance signal has been reported yet.',
    },
  ];
}

function buildObserveRecommendations(
  blockers: readonly Blocker[],
  activity: readonly PhaseActivityEvent[],
  previewDiagnostics: ObservePreviewDiagnostics,
  agentGovernance?: AgentGovernanceHealth,
): readonly ObserveRecommendation[] {
  const recommendations: ObserveRecommendation[] = blockers.slice(0, 3).map((blocker) => ({
    id: `blocker-${blocker.id}`,
    title: blocker.title ?? 'Resolve active blocker',
    detail: blocker.description,
  }));

  activity
    .filter((event) => isGenerateFailureEvent(event) || runHealthStatus(event) === 'failed')
    .slice(0, 2)
    .forEach((event) => {
      recommendations.push({
        id: `activity-${event.id}`,
        title: 'Investigate failed runtime evidence',
        detail: event.summary,
      });
    });

  if (previewDiagnostics.health === 'down') {
    recommendations.push({
      id: 'preview-runtime',
      title: 'Repair preview runtime',
      detail: 'Review runtime errors and policy blocks before promoting Observe to Learn.',
    });
  }

  if (agentGovernance && !agentGovernance.isHealthy) {
    recommendations.push({
      id: 'agent-governance',
      title: 'Review agent governance',
      detail: agentGovernance.issues[0] ?? `Agent governance state is ${agentGovernance.governanceState}.`,
    });
  }

  return recommendations.slice(0, 5);
}

function learnEvidenceCategory(event: PhaseActivityEvent): LearnEvidenceItem['category'] {
  const text = activityText(event);
  if (text.includes('run') || text.includes('preview') || text.includes('runtime')) {
    return 'run';
  }
  if (text.includes('approval') || text.includes('approved') || text.includes('rejected') || text.includes('human')) {
    return 'approval';
  }
  if (text.includes('prompt') || text.includes('model') || text.includes('eval') || text.includes('score')) {
    return 'prompt';
  }
  if (text.includes('agent')) {
    return 'agent';
  }
  return 'general';
}

function buildLearnEvidence(activity: readonly PhaseActivityEvent[]): readonly LearnEvidenceItem[] {
  return activity
    .filter((event) => {
      const text = activityText(event);
      return (
        text.includes('learn')
        || text.includes('evidence')
        || text.includes('recommend')
        || text.includes('lesson')
        || text.includes('pattern')
        || text.includes('approval')
        || text.includes('approved')
        || text.includes('rejected')
        || text.includes('prompt')
        || text.includes('model')
        || text.includes('agent')
        || runHealthStatus(event) === 'failed'
      );
    })
    .slice(0, 6)
    .map((event) => ({
      id: event.id,
      category: learnEvidenceCategory(event),
      summary: event.summary,
      trace: formatActivityTrace(event),
    }));
}

function buildLearnRecommendations(
  blockers: readonly Blocker[],
  activity: readonly PhaseActivityEvent[],
  agentGovernance?: AgentGovernanceHealth,
): readonly LearnRecommendation[] {
  const recommendations: LearnRecommendation[] = blockers.slice(0, 3).map((blocker) => ({
    id: `blocker-${blocker.id}`,
    title: blocker.title ?? 'Capture blocker learning',
    detail: blocker.description,
    status: 'blocked',
  }));

  activity
    .filter((event) => {
      const text = activityText(event);
      return text.includes('recommend') || text.includes('lesson') || text.includes('pattern') || runHealthStatus(event) === 'failed';
    })
    .slice(0, 3)
    .forEach((event) => {
      recommendations.push({
        id: `activity-${event.id}`,
        title: event.success === false ? 'Convert failure into learning' : 'Review learning recommendation',
        detail: event.summary,
        status: event.success === false ? 'needs-review' : 'ready',
      });
    });

  if (agentGovernance && !agentGovernance.isHealthy) {
    recommendations.push({
      id: 'agent-governance-learning',
      title: 'Quarantine agent learning',
      detail: agentGovernance.issues[0] ?? `Agent governance state is ${agentGovernance.governanceState}.`,
      status: 'needs-review',
    });
  }

  return recommendations.slice(0, 5);
}

function buildLearnApprovals(activity: readonly PhaseActivityEvent[]): readonly LearnApprovalItem[] {
  return activity
    .filter((event) => {
      const text = activityText(event);
      return text.includes('approval') || text.includes('approved') || text.includes('rejected') || text.includes('human review');
    })
    .slice(0, 5)
    .map((event) => {
      const text = activityText(event);
      const decision: LearnApprovalItem['decision'] = text.includes('rejected')
        ? 'rejected'
        : text.includes('approved')
          ? 'approved'
          : 'pending';

      return {
        id: event.id,
        decision,
        detail: event.summary,
        trace: formatActivityTrace(event),
      };
    });
}

function buildLearnPromptSignals(activity: readonly PhaseActivityEvent[]): readonly LearnPromptSignal[] {
  return activity
    .filter((event) => {
      const text = activityText(event);
      return text.includes('prompt') || text.includes('model') || text.includes('eval') || text.includes('score') || text.includes('rollback') || text.includes('promotion');
    })
    .slice(0, 5)
    .map((event) => ({
      id: event.id,
      title: event.action,
      detail: event.summary,
      trace: formatActivityTrace(event),
    }));
}

function evolveSignalStatus(event: PhaseActivityEvent): EvolveSignal['status'] {
  const severity = event.severity?.toLowerCase();
  const text = activityText(event);
  if (event.success === false || severity === 'error' || severity === 'critical' || text.includes('blocked')) {
    return 'blocked';
  }
  if (severity === 'warning' || text.includes('review') || text.includes('approval') || text.includes('pending')) {
    return 'needs-review';
  }
  return 'ready';
}

function buildEvolveSignals(
  activity: readonly PhaseActivityEvent[],
  predicate: (event: PhaseActivityEvent) => boolean,
  fallbackTitle: string,
): readonly EvolveSignal[] {
  return activity
    .filter(predicate)
    .slice(0, 5)
    .map((event) => ({
      id: event.id,
      title: event.action || fallbackTitle,
      detail: event.summary,
      trace: formatActivityTrace(event),
      status: evolveSignalStatus(event),
    }));
}

function isEvolveProposalEvent(event: PhaseActivityEvent): boolean {
  const text = activityText(event);
  return text.includes('proposal') || text.includes('evolution plan') || text.includes('roadmap') || text.includes('backlog');
}

function isEvolveImpactEvent(event: PhaseActivityEvent): boolean {
  const text = activityText(event);
  return text.includes('impact') || text.includes('affected') || text.includes('surface') || text.includes('module') || text.includes('test');
}

function isEvolveDiffEvent(event: PhaseActivityEvent): boolean {
  const text = activityText(event);
  return text.includes('diff') || text.includes('patch') || text.includes('changed file') || text.includes('review');
}

function isEvolveApprovalEvent(event: PhaseActivityEvent): boolean {
  const text = activityText(event);
  return text.includes('approval') || text.includes('approved') || text.includes('rejected') || text.includes('human');
}

function isEvolveRerunEvent(event: PhaseActivityEvent): boolean {
  const text = activityText(event);
  return text.includes('rerun') || text.includes('re-run') || text.includes('revalidate') || text.includes('regenerate') || text.includes('handoff');
}

function resolveEvolveRerunStatus(
  blockers: readonly Blocker[],
  diffSignals: readonly EvolveSignal[],
  approvalSignals: readonly EvolveSignal[],
  rerunSignals: readonly EvolveSignal[],
): EvolveSignal['status'] {
  if (rerunSignals.some((signal) => signal.status === 'ready')) {
    return 'ready';
  }
  if (blockers.length > 0 || diffSignals.some((signal) => signal.status === 'blocked')) {
    return 'blocked';
  }
  if (approvalSignals.length > 0 && approvalSignals.every((signal) => signal.status === 'ready')) {
    return 'ready';
  }
  return 'needs-review';
}

function StatusPanel({ testId, title, content }: { testId: string; title: string; content: React.ReactNode }) {
  return (
    <Card variant="outlined" data-testid={testId}>
      <CardContent className="p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">{title}</p>
        <div className="mt-2 text-sm text-fg-muted">{content}</div>
      </CardContent>
    </Card>
  );
}

function PreviewSecurityPanel({ previewHealth }: { readonly previewHealth?: PreviewHealth }) {
  const security = previewHealth?.security;
  if (!security) {
    return null;
  }

  const missingScopes = security.tokenScopes.filter((scope) => scope.required && !scope.granted);
  return (
    <Card
      variant="outlined"
      data-testid="preview-security-status"
      className={security.safe ? 'border-success-border bg-success-bg/20' : 'border-destructive-border bg-destructive-bg/30'}
    >
      <CardContent className="p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Preview security</p>
            <p className="mt-2 text-sm text-fg">
              Trust: <span data-testid="preview-security-trust">{security.trustLevel}</span>
            </p>
          </div>
          <Badge variant={security.safe ? 'success' : 'destructive'} data-testid="preview-security-safe">
            {security.safe ? 'Safe' : 'Unsafe'}
          </Badge>
        </div>
        {security.expiresAt ? (
          <p className="mt-2 text-xs text-fg-muted" data-testid="preview-security-expiration">
            Token expires: <time dateTime={security.expiresAt}>{new Date(security.expiresAt).toLocaleString()}</time>
          </p>
        ) : null}
        {missingScopes.length > 0 ? (
          <p className="mt-2 text-sm text-destructive" data-testid="preview-security-missing-scopes">
            Missing required scopes: {missingScopes.map((scope) => scope.name).join(', ')}
          </p>
        ) : null}
        {security.issues.length > 0 ? (
          <div className="mt-3 space-y-1">
            {security.issues.map((issue) => (
              <p key={issue} className="text-sm text-destructive" data-testid="preview-security-issue">
                {issue}
              </p>
            ))}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function AgentGovernancePanel({ agentGovernance }: { readonly agentGovernance?: AgentGovernanceHealth }) {
  if (!agentGovernance) {
    return null;
  }

  return (
    <Card
      variant="outlined"
      data-testid="agent-governance-health"
      className={agentGovernance.isHealthy ? 'border-success-border bg-success-bg/20' : 'border-warning-border bg-warning-bg/20'}
    >
      <CardContent className="p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Agent governance</p>
            <p className="mt-2 text-sm text-fg">
              State: <span data-testid="agent-governance-state">{agentGovernance.governanceState}</span>
            </p>
            <p className="mt-1 text-sm text-fg-muted" data-testid="agent-learning-level">
              Learning: {agentGovernance.learningLevel}
            </p>
          </div>
          <Badge variant={agentGovernance.isHealthy ? 'success' : 'destructive'} data-testid="agent-governance-status">
            {agentGovernance.status}
          </Badge>
        </div>
        {agentGovernance.evidenceIds.length > 0 ? (
          <div className="mt-3 space-y-1" data-testid="agent-learning-evidence">
            {agentGovernance.evidenceIds.map((evidenceId) => (
              <p key={evidenceId} className="text-xs text-fg-muted">
                Evidence: {evidenceId}
              </p>
            ))}
          </div>
        ) : (
          <p className="mt-3 text-sm text-fg-muted">No agent learning evidence has been linked yet.</p>
        )}
        {agentGovernance.issues.length > 0 ? (
          <div className="mt-3 space-y-1">
            {agentGovernance.issues.map((issue) => (
              <p key={issue} className="text-sm text-warning-color" data-testid="agent-governance-issue">
                {issue}
              </p>
            ))}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function PhaseStatusPanels({
  phase,
  preview,
  previewHealth,
  agentGovernance,
  blockers,
  activity,
}: PhaseStatusPanelsProps): React.ReactNode {
  if (phase === 'intent') {
    const readinessValue = preview?.readiness;

    return (
      <div className="grid gap-4 md:grid-cols-2">
        <StatusPanel
          testId="intent-goal-clarity"
          title="Goal clarity"
          content={
            readinessValue != null
              ? `Intent quality is currently ${readinessValue}%. Confirm business outcomes before shaping architecture.`
              : 'Capture the core business outcomes, target users, and measurable success criteria.'
          }
        />
        <Card variant="outlined" data-testid="intent-artifact-snapshot">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Intent artifacts</p>
            {preview?.completedArtifacts?.length ? (
              <div className="mt-3 space-y-2">
                {preview.completedArtifacts.map((item) => (
                  <div key={item} className="flex items-center gap-2 text-sm text-fg" data-testid="intent-artifact">
                    <span className="inline-block h-2 w-2 rounded-full bg-success-color" aria-hidden="true" />
                    {item}
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted">No backed intent artifacts have been recorded yet.</p>
            )}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (phase === 'shape') {
    const requiredArtifacts = preview?.requiredArtifacts ?? [];

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <StatusPanel
            testId="shape-architecture-readiness"
            title="Architecture readiness"
            content={
              preview?.canAdvance
                ? 'Architecture shape is ready for validation review.'
                : `${blockers.length} blocker(s) are preventing lifecycle promotion.`
            }
          />
          <Card variant="outlined" data-testid="shape-required-artifacts">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Required shape artifacts</p>
              {requiredArtifacts.length > 0 ? (
                <div className="mt-3 space-y-2">
                  {requiredArtifacts.map((item) => (
                    <div key={item} className="flex items-center gap-2 text-sm text-fg" data-testid="shape-artifact">
                      <span className="inline-block h-2 w-2 rounded-full bg-info-color" aria-hidden="true" />
                      {item}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted">No additional shape artifacts are currently required.</p>
              )}
            </CardContent>
          </Card>
        </div>
        <Card variant="outlined" data-testid="shape-activity-summary">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recent shaping activity</p>
            {activity.length > 0 ? (
              <div className="mt-3 space-y-2">
                {activity.slice(0, 3).map((event) => (
                  <div key={event.id} className="text-sm text-fg" data-testid="shape-activity-event">
                    <p>{event.summary}</p>
                    <p className="mt-1 text-xs text-fg-muted" data-testid="activity-trace">
                      {formatActivityTrace(event)}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted">No recent shaping activity has been recorded.</p>
            )}
          </CardContent>
        </Card>
      </div>
    );
  }

  if (phase === 'validate') {
    const requiredApprovals = preview?.requiredArtifacts ?? [];

    return (
      <div className="grid gap-4 md:grid-cols-2">
        <Card variant="outlined" data-testid="validation-status">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Validation status</p>
            <p className="mt-2 text-lg font-semibold text-fg">
              {blockers.length === 0 ? 'Passed' : preview?.canAdvance ? 'Pending review' : 'Failed'}
            </p>
          </CardContent>
        </Card>
        <Card variant="outlined" data-testid="approval-gates">
          <CardContent className="p-4">
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
          </CardContent>
        </Card>
      </div>
    );
  }

  if (phase === 'generate') {
    const outputBundle = preview?.requiredArtifacts ?? [];
    const completedArtifacts = preview?.completedArtifacts ?? [];
    const isReady = blockers.length === 0 && preview?.canAdvance === true;
    const recentGenerateActivity = activity.slice(0, 3);
    const assuranceChecks = buildGenerateAssuranceChecks(activity);
    const artifactSignals = generateSignals(activity, isGenerateArtifactEvent);
    const diffSignals = generateSignals(activity, isGenerateDiffEvent);
    const failureSignals = [
      ...blockers.map((blocker) => ({
        id: blocker.id,
        summary: `${blocker.title ? `${blocker.title}: ` : ''}${blocker.description}`,
        trace: blocker.source ? `Source: ${blocker.source}` : '',
      })),
      ...generateSignals(activity, isGenerateFailureEvent),
    ].slice(0, 5);
    const hasFailedAssurance = assuranceChecks.some((check) => check.status === 'failed');

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Card variant="outlined" data-testid="codegen-preview-panel">
            <CardContent className="p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Codegen readiness</p>
                <Badge
                  variant={isReady ? 'success' : 'secondary'}
                  data-testid="codegen-readiness-badge"
                >
                  {isReady ? 'Ready' : 'Not ready'}
                </Badge>
              </div>
              <p className="mt-2 text-sm text-fg-muted">
                {isReady
                  ? 'Readiness signal confirmed. Review the planned output bundle and advance to run.'
                  : `${blockers.length} blocker(s) must be resolved before generating artifacts.`}
              </p>
              {preview?.readiness != null && (
                <p className="mt-2 text-xs text-fg-muted" data-testid="codegen-readiness-score">
                  Readiness score: <span className="font-medium text-fg">{preview.readiness}%</span>
                </p>
              )}
              <p className="sr-only" data-testid="readiness-accessibility-explanation">
                Readiness is the lifecycle gate score for this phase. A lower score means blockers, missing artifacts, or review requirements still need attention before promotion.
              </p>
            </CardContent>
          </Card>
          <Card variant="outlined" data-testid="generated-file-list">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Generated artifacts</p>
              {outputBundle.length > 0 ? (
                <div className="mt-3 space-y-2">
                  {outputBundle.map((item) => (
                    <div key={item} data-testid="generated-file" className="flex items-center gap-2 text-sm text-fg">
                      <span className="inline-block h-2 w-2 rounded-full bg-info-color" aria-hidden="true" />
                      {item}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="generated-file-empty">
                  Output plan is not ready yet.
                </p>
              )}
              {completedArtifacts.length > 0 ? (
                <div className="mt-4 space-y-2" data-testid="generated-completed-artifacts">
                  {completedArtifacts.map((item) => (
                    <div key={item} className="flex items-center gap-2 text-sm text-fg">
                      <span className="inline-block h-2 w-2 rounded-full bg-success-color" aria-hidden="true" />
                      {item}
                    </div>
                  ))}
                </div>
              ) : null}
              {artifactSignals.length > 0 ? (
                <div className="mt-4 space-y-2" data-testid="generated-artifact-events">
                  {artifactSignals.map((item) => (
                    <p key={item.id} className="text-xs text-fg-muted">
                      {item.summary}
                    </p>
                  ))}
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>
        <Card variant="outlined" data-testid="generate-assurance-panel">
          <CardContent className="p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Assurance status</p>
              <Badge variant={hasFailedAssurance ? 'destructive' : isReady ? 'success' : 'secondary'}>
                {hasFailedAssurance ? 'Failing' : isReady ? 'Passing' : 'Pending'}
              </Badge>
            </div>
            <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
              {assuranceChecks.map((check) => (
                <div
                  key={check.id}
                  className="rounded-lg border border-border bg-surface p-3"
                  data-testid="generate-assurance-check"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-medium text-fg">{check.label}</p>
                    <Badge
                      variant={
                        check.status === 'passed'
                          ? 'success'
                          : check.status === 'failed'
                            ? 'destructive'
                            : 'secondary'
                      }
                    >
                      {check.status}
                    </Badge>
                  </div>
                  <p className="mt-2 text-xs text-fg-muted">{check.detail}</p>
                  {check.evidenceId ? (
                    <p className="mt-1 text-xs text-fg-muted">Evidence: {check.evidenceId}</p>
                  ) : null}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
        <Card variant="outlined" data-testid="generate-diff-review">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Diff review</p>
            {diffSignals.length > 0 ? (
              <div className="mt-3 space-y-2">
                {diffSignals.map((item) => (
                  <div key={item.id} className="text-sm text-fg" data-testid="generate-diff-entry">
                    <p>{item.summary}</p>
                    {item.trace ? <p className="mt-1 text-xs text-fg-muted">{item.trace}</p> : null}
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted" data-testid="generate-diff-empty">
                No generated diff has been reported yet.
              </p>
            )}
          </CardContent>
        </Card>
        {failureSignals.length > 0 ? (
          <Card variant="outlined" data-testid="generate-failure-panel" className="border-destructive-border bg-destructive-bg/20">
            <CardContent className="p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-destructive">Generation failures</p>
                  <p className="mt-2 text-sm text-fg-muted" data-testid="generate-retry-guidance">
                    Retry generation after the listed blocker or failed assurance evidence is resolved.
                  </p>
                </div>
                <Badge variant="destructive">Action needed</Badge>
              </div>
              <div className="mt-3 space-y-2">
                {failureSignals.map((item) => (
                  <div key={item.id} className="text-sm text-fg" data-testid="generate-failure-entry">
                    <p>{item.summary}</p>
                    {item.trace ? <p className="mt-1 text-xs text-fg-muted">{item.trace}</p> : null}
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        ) : null}
        {recentGenerateActivity.length > 0 && (
          <Card variant="outlined" data-testid="generate-activity-timeline">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recent generate activity</p>
              <div className="mt-3 space-y-2">
                {recentGenerateActivity.map((event) => (
                  <div key={event.id} className="flex items-start gap-2 text-sm">
                    <span className="mt-0.5 inline-block h-2 w-2 shrink-0 rounded-full bg-surface-muted" aria-hidden="true" />
                    <span className="text-fg">
                      <span>{event.summary}</span>
                      <span className="mt-1 block text-xs text-fg-muted" data-testid="activity-trace">
                        {formatActivityTrace(event)}
                      </span>
                    </span>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    );
  }

  if (phase === 'run') {
    const requiredCapabilities = preview?.requiredArtifacts ?? [];
    const runTimeline = buildRunTimeline(activity);
    const runStatus = resolveRunStatus(activity, blockers, preview);
    const operations: readonly RunOperation[] = ['retry', 'rollback', 'promote'];

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Card variant="outlined" data-testid="capability-gates">
            <CardContent className="p-4">
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
            </CardContent>
          </Card>
          <StatusPanel
            testId="run-plan-panel"
            title="Run plan"
            content={
              <span data-testid="pipeline-readiness">
                {preview?.canAdvance ? 'Ready with operator review' : 'Not ready until blockers are cleared'}
              </span>
            }
          />
        </div>
        <Card variant="outlined" data-testid="platform-run-status">
          <CardContent className="p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Platform run status</p>
                <p className="mt-2 text-sm text-fg-muted">
                  Latest runtime truth from backed run, preview, deploy, and promotion events.
                </p>
              </div>
              <Badge
                variant={
                  runStatus === 'healthy'
                    ? 'success'
                    : runStatus === 'failed'
                      ? 'destructive'
                      : 'secondary'
                }
                data-testid="platform-run-health"
              >
                {runStatus}
              </Badge>
            </div>
            <div className="mt-4 grid gap-3 md:grid-cols-3" data-testid="run-operation-controls">
              {operations.map((operation) => {
                const state = runOperationState(operation, runStatus, activity);
                return (
                  <div key={operation} className="rounded-lg border border-border bg-surface p-3" data-testid={`run-operation-${operation}`}>
                    <p className="text-sm font-medium capitalize text-fg">{operation}</p>
                    <p className="mt-1 text-xs text-fg-muted">
                      {state === 'ready'
                        ? `${operation} is ready for the operator action surface.`
                        : state === 'available'
                          ? `${operation} evidence is already linked to this run.`
                          : `${operation} is blocked until run status changes.`}
                    </p>
                    <Badge className="mt-2" variant={state === 'blocked' ? 'secondary' : 'success'}>
                      {state}
                    </Badge>
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>
        <Card variant="outlined" data-testid="platform-run-timeline">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Run timeline</p>
            {runTimeline.length > 0 ? (
              <div className="mt-3 space-y-3">
                {runTimeline.map((event) => (
                  <div key={event.id} className="flex items-start gap-3 text-sm" data-testid="platform-run-event">
                    <span
                      className={[
                        'mt-0.5 inline-block h-2 w-2 shrink-0 rounded-full',
                        event.status === 'failed'
                          ? 'bg-destructive'
                          : event.status === 'degraded'
                            ? 'bg-warning-color'
                            : 'bg-success-color',
                      ].join(' ')}
                      aria-hidden="true"
                    />
                    <div>
                      <p className="text-fg">{event.summary}</p>
                      {event.trace ? <p className="mt-1 text-xs text-fg-muted">{event.trace}</p> : null}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted" data-testid="platform-run-timeline-empty">
                No platform run events have been reported yet.
              </p>
            )}
          </CardContent>
        </Card>
        <PreviewSecurityPanel previewHealth={previewHealth} />
      </div>
    );
  }

  if (phase === 'observe') {
    const recentEvents = activity.slice(0, 5);
    const previewDiagnostics = buildObservePreviewDiagnostics(activity, blockers, preview);
    const observeHealthItems = buildObserveHealthItems(activity, blockers, previewDiagnostics, agentGovernance);
    const observeRecommendations = buildObserveRecommendations(blockers, activity, previewDiagnostics, agentGovernance);
    const latestLoadTiming = previewDiagnostics.loadTimings[0];

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <StatusPanel
            testId="metrics-panel"
            title="Metrics summary"
            content={
              activity.length > 0
                ? `Tracking ${activity.length} recent backed lifecycle or audit events.`
                : 'No recent backed events have been recorded yet.'
            }
          />
          <StatusPanel
            testId="incidents-panel"
            title="Incidents"
            content={
              blockers.length > 0
                ? `${blockers.length} issue(s) need review before promotion.`
                : 'No active incident-level blockers are surfaced here.'
            }
          />
        </div>
        <Card variant="outlined" data-testid="observe-preview-diagnostics">
          <CardContent className="p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Preview runtime observability</p>
                <p className="mt-2 text-sm text-fg-muted">
                  Runtime errors, console output, policy refusals, reload timing, and user actions reported by backed Observe activity.
                </p>
              </div>
              <Badge
                variant={
                  previewDiagnostics.health === 'healthy'
                    ? 'success'
                    : previewDiagnostics.health === 'down'
                      ? 'destructive'
                      : 'secondary'
                }
                data-testid="observe-preview-health"
              >
                Preview health: {OBSERVE_HEALTH_LABEL[previewDiagnostics.health]}
              </Badge>
            </div>
            {preview?.checkedAt ? (
              <p className="mt-2 text-xs text-fg-muted">
                Readiness checked:{' '}
                <time dateTime={preview.checkedAt}>{new Date(preview.checkedAt).toLocaleString()}</time>
              </p>
            ) : null}
            <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              <div className="rounded-xl border border-border bg-surface p-3" data-testid="observe-runtime-errors">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Runtime errors</p>
                {previewDiagnostics.runtimeErrors.length > 0 ? (
                  <div className="mt-2 space-y-2">
                    {previewDiagnostics.runtimeErrors.map((item) => (
                      <p key={item.id} className="text-sm text-destructive" data-testid="preview-runtime-error">
                        {item.detail}
                      </p>
                    ))}
                  </div>
                ) : (
                  <p className="mt-2 text-sm text-fg-muted">No preview runtime errors reported.</p>
                )}
              </div>
              <div className="rounded-xl border border-border bg-surface p-3" data-testid="observe-console-logs">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Console logs</p>
                {previewDiagnostics.consoleLogs.length > 0 ? (
                  <div className="mt-2 space-y-2">
                    {previewDiagnostics.consoleLogs.map((item) => (
                      <p key={item.id} className="text-sm text-fg" data-testid="preview-console-log">
                        {item.detail}
                      </p>
                    ))}
                  </div>
                ) : (
                  <p className="mt-2 text-sm text-fg-muted">No preview console output reported.</p>
                )}
              </div>
              <div className="rounded-xl border border-border bg-surface p-3" data-testid="observe-policy-blocks">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Policy blocks</p>
                {previewDiagnostics.policyBlocks.length > 0 ? (
                  <div className="mt-2 space-y-2">
                    {previewDiagnostics.policyBlocks.map((item) => (
                      <p key={item.id} className="text-sm text-warning-color" data-testid="preview-policy-block">
                        {item.detail}
                      </p>
                    ))}
                  </div>
                ) : (
                  <p className="mt-2 text-sm text-fg-muted">No preview policy blocks reported.</p>
                )}
              </div>
              <div className="rounded-xl border border-border bg-surface p-3" data-testid="observe-load-timing">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Reload latency</p>
                {latestLoadTiming?.latencyMs != null ? (
                  <p className="mt-2 text-sm text-fg" data-testid="preview-load-latency">
                    Latest preview reload completed in {latestLoadTiming.latencyMs}ms.
                  </p>
                ) : (
                  <p className="mt-2 text-sm text-fg-muted">No preview reload timing has been reported yet.</p>
                )}
              </div>
              <div className="rounded-xl border border-border bg-surface p-3 md:col-span-2" data-testid="observe-user-action-trace">
                <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">User-action trace</p>
                {previewDiagnostics.userActionTrace.length > 0 ? (
                  <div className="mt-2 space-y-2">
                    {previewDiagnostics.userActionTrace.map((item) => (
                      <p key={item.id} className="text-sm text-fg" data-testid="preview-user-action">
                        {item.actor}: {item.detail}
                      </p>
                    ))}
                  </div>
                ) : (
                  <p className="mt-2 text-sm text-fg-muted">No preview user actions have been reported yet.</p>
                )}
              </div>
            </div>
          </CardContent>
        </Card>
        <div className="grid gap-4 lg:grid-cols-[1fr_1fr]" data-testid="observe-health-and-recommendations">
          <Card variant="outlined" data-testid="observe-health-matrix">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Kernel, app, and agent health</p>
              <div className="mt-3 space-y-3">
                {observeHealthItems.map((item) => (
                  <div key={item.id} className="rounded-lg border border-border bg-surface p-3" data-testid="observe-health-item">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-medium text-fg">{item.label}</p>
                      <Badge
                        variant={
                          item.status === 'healthy'
                            ? 'success'
                            : item.status === 'failed'
                              ? 'destructive'
                              : 'secondary'
                        }
                      >
                        {item.status}
                      </Badge>
                    </div>
                    <p className="mt-2 text-xs text-fg-muted">{item.detail}</p>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
          <Card variant="outlined" data-testid="observe-recommendations">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recommendations</p>
              {observeRecommendations.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {observeRecommendations.map((recommendation) => (
                    <div key={recommendation.id} className="rounded-lg border border-border bg-surface p-3" data-testid="observe-recommendation">
                      <p className="text-sm font-medium text-fg">{recommendation.title}</p>
                      <p className="mt-2 text-xs text-fg-muted">{recommendation.detail}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="observe-recommendations-empty">
                  No remediation recommendations are currently reported.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
        <PreviewSecurityPanel previewHealth={previewHealth} />
        <AgentGovernancePanel agentGovernance={agentGovernance} />
        {recentEvents.length > 0 && (
          <Card variant="outlined" data-testid="observe-signal-timeline">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recent signal timeline</p>
              <div className="mt-3 space-y-3">
                {recentEvents.map((event) => (
                  <div key={event.id} className="flex items-start gap-3 text-sm" data-testid="signal-event">
                    <span
                      className={[
                        'mt-0.5 inline-block h-2 w-2 shrink-0 rounded-full',
                        event.severity?.toLowerCase() === 'error' || event.success === false
                          ? 'bg-destructive'
                          : event.severity?.toLowerCase() === 'warning'
                            ? 'bg-warning-color'
                            : 'bg-success-color',
                      ].join(' ')}
                      aria-hidden="true"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="text-fg">{event.summary}</p>
                      <p className="text-xs text-fg-muted">{event.source} | {event.action}</p>
                      <p className="text-xs text-fg-muted" data-testid="activity-trace">
                        {formatActivityTrace(event)}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    );
  }

  if (phase === 'learn') {
    const learnEvidence = buildLearnEvidence(activity);
    const learnRecommendations = buildLearnRecommendations(blockers, activity, agentGovernance);
    const approvalItems = buildLearnApprovals(activity);
    const promptSignals = buildLearnPromptSignals(activity);

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Card variant="outlined" data-testid="learn-evidence-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Learning evidence</p>
              {learnEvidence.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {learnEvidence.map((item) => (
                    <div key={item.id} className="rounded-lg border border-border bg-surface p-3" data-testid="learn-evidence-item">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-fg">{item.summary}</p>
                        <Badge variant="secondary">{item.category}</Badge>
                      </div>
                      {item.trace ? (
                        <p className="mt-2 text-xs text-fg-muted" data-testid="activity-trace">
                          {item.trace}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="learn-evidence-empty">
                  No learning evidence has been attached to this lifecycle cycle yet.
                </p>
              )}
            </CardContent>
          </Card>
          <Card variant="outlined" data-testid="learn-recommendations-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Recommendations</p>
              {learnRecommendations.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {learnRecommendations.map((item) => (
                    <div key={item.id} className="rounded-lg border border-border bg-surface p-3" data-testid="learn-recommendation">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-fg">{item.title}</p>
                        <Badge
                          variant={
                            item.status === 'ready'
                              ? 'success'
                              : item.status === 'blocked'
                                ? 'destructive'
                                : 'secondary'
                          }
                        >
                          {item.status}
                        </Badge>
                      </div>
                      <p className="mt-2 text-xs text-fg-muted">{item.detail}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="learn-recommendations-empty">
                  No learning recommendations are currently reported.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
        <div className="grid gap-4 lg:grid-cols-[1fr_1fr]">
          <Card variant="outlined" data-testid="learn-human-approval-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Human approval</p>
              {approvalItems.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {approvalItems.map((item) => (
                    <div key={item.id} className="rounded-lg border border-border bg-surface p-3" data-testid="learn-approval-item">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm text-fg">{item.detail}</p>
                        <Badge variant={item.decision === 'approved' ? 'success' : item.decision === 'rejected' ? 'destructive' : 'secondary'}>
                          {item.decision}
                        </Badge>
                      </div>
                      {item.trace ? (
                        <p className="mt-2 text-xs text-fg-muted" data-testid="activity-trace">
                          {item.trace}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="learn-approval-empty">
                  No human approval decisions have been linked yet.
                </p>
              )}
            </CardContent>
          </Card>
          <Card variant="outlined" data-testid="learn-prompt-learning-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Prompt and model learning</p>
              {promptSignals.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {promptSignals.map((item) => (
                    <div key={item.id} className="rounded-lg border border-border bg-surface p-3" data-testid="learn-prompt-signal">
                      <p className="text-sm font-medium text-fg">{item.title}</p>
                      <p className="mt-2 text-xs text-fg-muted">{item.detail}</p>
                      {item.trace ? (
                        <p className="mt-2 text-xs text-fg-muted" data-testid="activity-trace">
                          {item.trace}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="learn-prompt-empty">
                  No prompt or model learning evidence has been reported yet.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <StatusPanel
            testId="reusable-patterns"
            title="Reusable patterns"
            content="Promote stable learnings into repeatable delivery patterns once they have enough backing evidence."
          />
          <AgentGovernancePanel agentGovernance={agentGovernance} />
        </div>
      </div>
    );
  }

  if (phase === 'evolve') {
    const proposalSignals = buildEvolveSignals(activity, isEvolveProposalEvent, 'Evolution proposal');
    const impactSignals = buildEvolveSignals(activity, isEvolveImpactEvent, 'Impact analysis');
    const diffSignals = buildEvolveSignals(activity, isEvolveDiffEvent, 'Diff review');
    const approvalSignals = buildEvolveSignals(activity, isEvolveApprovalEvent, 'Approval decision');
    const rerunSignals = buildEvolveSignals(activity, isEvolveRerunEvent, 'Re-run handoff');
    const rerunStatus = resolveEvolveRerunStatus(blockers, diffSignals, approvalSignals, rerunSignals);

    return (
      <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2">
          <Card variant="outlined" data-testid="evolve-proposals-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Evolution proposals</p>
              {proposalSignals.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {proposalSignals.map((signal) => (
                    <div key={signal.id} className="rounded-lg border border-border bg-surface p-3" data-testid="evolve-proposal">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-fg">{signal.detail}</p>
                        <Badge variant={signal.status === 'ready' ? 'success' : signal.status === 'blocked' ? 'destructive' : 'secondary'}>
                          {signal.status}
                        </Badge>
                      </div>
                      {signal.trace ? (
                        <p className="mt-2 text-xs text-fg-muted" data-testid="activity-trace">
                          {signal.trace}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="evolve-proposals-empty">
                  No evolution proposals have been linked to this cycle yet.
                </p>
              )}
            </CardContent>
          </Card>
          <Card variant="outlined" data-testid="evolve-impact-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Impact analysis</p>
              {impactSignals.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {impactSignals.map((signal) => (
                    <div key={signal.id} className="rounded-lg border border-border bg-surface p-3" data-testid="evolve-impact-item">
                      <p className="text-sm font-medium text-fg">{signal.title}</p>
                      <p className="mt-2 text-xs text-fg-muted">{signal.detail}</p>
                      {signal.trace ? (
                        <p className="mt-2 text-xs text-fg-muted" data-testid="activity-trace">
                          {signal.trace}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="evolve-impact-empty">
                  No impacted surfaces, modules, or tests have been reported yet.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
        <div className="grid gap-4 lg:grid-cols-[1fr_1fr]">
          <Card variant="outlined" data-testid="evolve-diff-review-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Diff review</p>
              {diffSignals.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {diffSignals.map((signal) => (
                    <div key={signal.id} className="rounded-lg border border-border bg-surface p-3" data-testid="evolve-diff-item">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-fg">{signal.title}</p>
                        <Badge variant={signal.status === 'ready' ? 'success' : signal.status === 'blocked' ? 'destructive' : 'secondary'}>
                          {signal.status}
                        </Badge>
                      </div>
                      <p className="mt-2 text-xs text-fg-muted">{signal.detail}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="evolve-diff-empty">
                  No generated evolve diff is currently ready for review.
                </p>
              )}
            </CardContent>
          </Card>
          <Card variant="outlined" data-testid="evolve-approval-panel">
            <CardContent className="p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Approval and rollback</p>
              {approvalSignals.length > 0 ? (
                <div className="mt-3 space-y-3">
                  {approvalSignals.map((signal) => (
                    <div key={signal.id} className="rounded-lg border border-border bg-surface p-3" data-testid="evolve-approval-item">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-fg">{signal.detail}</p>
                        <Badge variant={signal.status === 'ready' ? 'success' : signal.status === 'blocked' ? 'destructive' : 'secondary'}>
                          {signal.status}
                        </Badge>
                      </div>
                      {signal.trace ? (
                        <p className="mt-2 text-xs text-fg-muted" data-testid="activity-trace">
                          {signal.trace}
                        </p>
                      ) : null}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-fg-muted" data-testid="evolve-approval-empty">
                  No approval, rejection, or rollback decision has been attached yet.
                </p>
              )}
            </CardContent>
          </Card>
        </div>
        <Card variant="outlined" data-testid="evolve-rerun-panel">
          <CardContent className="p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Re-run readiness</p>
              <Badge variant={rerunStatus === 'ready' ? 'success' : rerunStatus === 'blocked' ? 'destructive' : 'secondary'}>
                {rerunStatus}
              </Badge>
            </div>
            {rerunSignals.length > 0 ? (
              <div className="mt-3 space-y-3">
                {rerunSignals.map((signal) => (
                  <div key={signal.id} className="rounded-lg border border-border bg-surface p-3" data-testid="evolve-rerun-item">
                    <p className="text-sm font-medium text-fg">{signal.title}</p>
                    <p className="mt-2 text-xs text-fg-muted">{signal.detail}</p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-3 text-sm text-fg-muted" data-testid="evolve-rerun-empty">
                Re-run is waiting for approved diff evidence and a clear impact analysis.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    );
  }

  return null;
}
