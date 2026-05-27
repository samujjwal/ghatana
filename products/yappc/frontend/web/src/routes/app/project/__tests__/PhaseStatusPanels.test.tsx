import React from 'react';
import { render, screen, within } from '@/test-utils/test-utils';
import { describe, expect, it } from 'vitest';

import { PhaseStatusPanels } from '../PhaseStatusPanels';

describe('PhaseStatusPanels', () => {
  it('renders intent-native status panels with artifact snapshot', () => {
    render(
      <PhaseStatusPanels
        phase="intent"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'INTENT',
          nextPhase: 'SHAPE',
          canAdvance: false,
          readiness: 72,
          blockers: ['Missing goals'],
          requiredArtifacts: ['Intent brief'],
          completedArtifacts: ['Problem statement'],
          estimatedReadyIn: '2h',
          estimatedReadyInHours: 2,
          predictionConfidence: 0.7,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('intent-goal-clarity')).toHaveTextContent('Intent quality is currently 72%');
    expect(screen.getByTestId('intent-artifact-snapshot')).toBeInTheDocument();
    expect(screen.getByTestId('intent-artifact')).toHaveTextContent('Problem statement');
  });

  it('renders shape-native status panels with readiness and activity', () => {
    render(
      <PhaseStatusPanels
        phase="shape"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'SHAPE',
          nextPhase: 'VALIDATE',
          canAdvance: false,
          readiness: 68,
          blockers: ['Schema missing'],
          requiredArtifacts: ['Component map', 'Data contracts'],
          completedArtifacts: ['Intent brief'],
          estimatedReadyIn: '3h',
          estimatedReadyInHours: 3,
          predictionConfidence: 0.75,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[{ id: 'b1', severity: 'warning', description: 'Schema missing', source: 'governance' }]}
        activity={[
          { id: 'e1', source: 'audit', action: 'shape.updated', summary: 'Updated architecture nodes', timestamp: '2026-05-04T10:00:00.000Z', actor: 'user', severity: null, success: true },
        ]}
      />,
    );

    expect(screen.getByTestId('shape-architecture-readiness')).toHaveTextContent('1 blocker(s) are preventing lifecycle promotion.');
    expect(screen.getAllByTestId('shape-artifact')).toHaveLength(2);
    expect(screen.getByTestId('shape-activity-event')).toHaveTextContent('Updated architecture nodes');
  });

  it('renders validation gates with required approvals when validate phase data exists', () => {
    render(
      <PhaseStatusPanels
        phase="validate"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'VALIDATE',
          nextPhase: 'GENERATE',
          canAdvance: true,
          readiness: 90,
          blockers: [],
          requiredArtifacts: ['Security review'],
          completedArtifacts: ['Requirements packet'],
          estimatedReadyIn: 'Ready now',
          estimatedReadyInHours: 0,
          predictionConfidence: 0.8,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('validation-status')).toHaveTextContent(/passed/i);
    expect(screen.getByTestId('approval-gates')).toBeInTheDocument();
    expect(screen.getByTestId('required-approval')).toHaveTextContent('Security review');
  });

  it('renders observe summary panels when no activity or incidents are available', () => {
    render(
      <PhaseStatusPanels
        phase="observe"
        preview={null}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('metrics-panel')).toHaveTextContent('No recent backed events have been recorded yet.');
    expect(screen.getByTestId('incidents-panel')).toHaveTextContent('No active incident-level blockers are surfaced here.');
  });

  it('renders generate readiness badge as ready when no blockers and preview confirms advance', () => {
    render(
      <PhaseStatusPanels
        phase="generate"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'GENERATE',
          nextPhase: 'RUN',
          canAdvance: true,
          readiness: 100,
          blockers: [],
          requiredArtifacts: ['Dashboard.tsx', 'schema.graphql'],
          completedArtifacts: [],
          estimatedReadyIn: null,
          estimatedReadyInHours: null,
          predictionConfidence: 0.95,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('codegen-readiness-badge')).toHaveTextContent(/ready/i);
    expect(screen.getByTestId('codegen-readiness-score')).toHaveTextContent('100%');
    expect(screen.getByTestId('readiness-accessibility-explanation')).toHaveTextContent(
      /readiness is the lifecycle gate score/i,
    );
    const files = screen.getAllByTestId('generated-file');
    expect(files).toHaveLength(2);
    expect(files[0]).toHaveTextContent('Dashboard.tsx');
  });

  it('renders generate readiness badge as not ready when blockers exist', () => {
    render(
      <PhaseStatusPanels
        phase="generate"
        preview={null}
        blockers={[{ id: 'b1', severity: 'error', description: 'Missing schema', source: 'governance' }]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('codegen-readiness-badge')).toHaveTextContent(/not ready/i);
    expect(screen.getByTestId('generated-file-empty')).toBeInTheDocument();
  });

  it('renders recent activity timeline for generate phase', () => {
    render(
      <PhaseStatusPanels
        phase="generate"
        preview={null}
        blockers={[]}
        activity={[
          { id: 'e1', source: 'lifecycle', action: 'artifact.created', summary: 'Schema artifact created', timestamp: '2026-05-04T10:00:00.000Z', actor: 'system', severity: null, success: true },
          { id: 'e2', source: 'audit', action: 'artifact.validated', summary: 'Validation passed', timestamp: '2026-05-04T09:00:00.000Z', actor: 'operator', severity: null, success: true },
        ]}
      />,
    );

    expect(screen.getByTestId('generate-activity-timeline')).toBeInTheDocument();
    expect(within(screen.getByTestId('generate-activity-timeline')).getByText('Schema artifact created')).toBeInTheDocument();
  });

  it('renders generate artifacts, assurance checks, diffs, failures, and retry guidance', () => {
    render(
      <PhaseStatusPanels
        phase="generate"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'GENERATE',
          nextPhase: 'RUN',
          canAdvance: false,
          readiness: 72,
          blockers: ['Security scan failed'],
          requiredArtifacts: ['src/routes/Dashboard.tsx'],
          completedArtifacts: ['src/routes/Dashboard.test.tsx'],
          estimatedReadyIn: 'Needs retry',
          estimatedReadyInHours: 1,
          predictionConfidence: 0.66,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[
          {
            id: 'blocker-security',
            title: 'Security assurance failed',
            severity: 'high',
            source: 'assurance',
            description: 'Dependency scan found a critical issue.',
          },
        ]}
        activity={[
          {
            id: 'compile-pass',
            source: 'assurance',
            action: 'generate.compile.passed',
            summary: 'Compile passed for generated workspace.',
            timestamp: '2026-05-04T10:00:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
          {
            id: 'test-pass',
            source: 'assurance',
            action: 'generate.tests.passed',
            summary: 'Vitest generated suite passed.',
            timestamp: '2026-05-04T10:01:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
          {
            id: 'security-fail',
            source: 'assurance',
            action: 'generate.security.failed',
            summary: 'Security scan failed on generated dependency.',
            timestamp: '2026-05-04T10:02:00.000Z',
            actor: 'system',
            severity: 'error',
            success: false,
          },
          {
            id: 'diff-ready',
            source: 'lifecycle',
            action: 'generate.diff.ready',
            summary: 'Diff ready with 3 changed files.',
            timestamp: '2026-05-04T10:03:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
          {
            id: 'artifact-created',
            source: 'lifecycle',
            action: 'artifact.created',
            summary: 'Generated file created: src/routes/Dashboard.tsx.',
            timestamp: '2026-05-04T10:04:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
        ]}
      />,
    );

    expect(screen.getByTestId('generated-completed-artifacts')).toHaveTextContent('Dashboard.test.tsx');
    expect(screen.getByTestId('generated-artifact-events')).toHaveTextContent('Generated file created');
    expect(screen.getByTestId('generate-assurance-panel')).toHaveTextContent('Failing');
    expect(screen.getAllByTestId('generate-assurance-check')).toHaveLength(6);
    expect(screen.getByTestId('generate-diff-review')).toHaveTextContent('3 changed files');
    expect(screen.getByTestId('generate-failure-panel')).toHaveTextContent('Security assurance failed');
    expect(screen.getByTestId('generate-failure-panel')).toHaveTextContent('Security scan failed');
    expect(screen.getByTestId('generate-retry-guidance')).toHaveTextContent(/retry generation/i);
  });

  it('renders signal timeline for observe phase with recent events', () => {
    render(
      <PhaseStatusPanels
        phase="observe"
        preview={null}
        blockers={[]}
        activity={[
          { id: 'e1', source: 'lifecycle', action: 'health.check', summary: 'Health check passed', timestamp: '2026-05-04T10:00:00.000Z', actor: null, severity: null, success: true },
          { id: 'e2', source: 'audit', action: 'alert.fired', summary: 'Error rate spike', timestamp: '2026-05-04T09:30:00.000Z', actor: null, severity: 'error', success: false },
        ]}
      />,
    );

    expect(screen.getByTestId('observe-signal-timeline')).toBeInTheDocument();
    const events = screen.getAllByTestId('signal-event');
    expect(events).toHaveLength(2);
    expect(events[0]).toHaveTextContent('Health check passed');
    expect(events[1]).toHaveTextContent('Error rate spike');
  });

  it('surfaces preview runtime diagnostics in the observe phase', () => {
    render(
      <PhaseStatusPanels
        phase="observe"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'OBSERVE',
          nextPhase: 'LEARN',
          canAdvance: false,
          readiness: 64,
          blockers: ['Preview CSP policy denied asset load'],
          requiredArtifacts: [],
          completedArtifacts: [],
          estimatedReadyIn: 'Needs review',
          estimatedReadyInHours: 1,
          predictionConfidence: 0.72,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[
          {
            id: 'policy-1',
            title: 'Preview policy blocked request',
            description: 'Sandbox policy denied https://tracker.example/script.js.',
            severity: 'high',
          },
        ]}
        activity={[
          {
            id: 'runtime-1',
            source: 'audit',
            action: 'preview.runtime.error',
            summary: 'TypeError: Cannot read properties of undefined in live preview.',
            timestamp: '2026-05-04T10:04:00.000Z',
            actor: null,
            severity: 'error',
            success: false,
          },
          {
            id: 'console-1',
            source: 'audit',
            action: 'preview.console.warning',
            summary: 'Console warning: image missing alt text.',
            timestamp: '2026-05-04T10:03:00.000Z',
            actor: null,
            severity: 'warning',
            success: true,
          },
          {
            id: 'load-1',
            source: 'lifecycle',
            action: 'preview.reload.completed',
            summary: 'Preview reload completed in 842ms.',
            timestamp: '2026-05-04T10:02:00.000Z',
            actor: 'operator-1',
            severity: null,
            success: true,
          },
          {
            id: 'action-1',
            source: 'audit',
            action: 'observe.preview.refresh',
            summary: 'Operator refreshed preview after rollback.',
            timestamp: '2026-05-04T10:01:00.000Z',
            actor: 'operator-1',
            severity: null,
            success: true,
          },
        ]}
      />,
    );

    expect(screen.getByTestId('observe-preview-diagnostics')).toBeInTheDocument();
    expect(screen.getByTestId('observe-preview-health')).toHaveTextContent('Preview health: Down');
    expect(screen.getByTestId('preview-runtime-error')).toHaveTextContent('TypeError');
    expect(screen.getByTestId('preview-console-log')).toHaveTextContent('Console warning');
    expect(screen.getByTestId('preview-policy-block')).toHaveTextContent('Sandbox policy denied');
    expect(screen.getByTestId('preview-load-latency')).toHaveTextContent('842ms');
    expect(screen.getAllByTestId('preview-user-action')[0]).toHaveTextContent('operator-1');
  });

  it('surfaces observe kernel app agent health and remediation recommendations', () => {
    render(
      <PhaseStatusPanels
        phase="observe"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'OBSERVE',
          nextPhase: 'LEARN',
          canAdvance: false,
          readiness: 40,
          blockers: ['Kernel lifecycle truth degraded'],
          requiredArtifacts: [],
          completedArtifacts: [],
          estimatedReadyIn: 'Needs remediation',
          estimatedReadyInHours: 2,
          predictionConfidence: 0.5,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        agentGovernance={{
          isHealthy: false,
          status: 'degraded',
          governanceState: 'policy-review-required',
          learningLevel: 'quarantined',
          evidenceIds: ['agent-evidence-1'],
          issues: ['Agent policy approval expired.'],
        }}
        blockers={[
          {
            id: 'kernel-truth',
            title: 'Kernel lifecycle truth degraded',
            description: 'Kernel truth source is stale.',
            severity: 'high',
            source: 'kernel',
          },
        ]}
        activity={[
          {
            id: 'kernel-event',
            source: 'kernel',
            action: 'kernel.truth.degraded',
            summary: 'Kernel lifecycle truth degraded for product unit.',
            timestamp: '2026-05-04T10:00:00.000Z',
            actor: 'system',
            severity: 'warning',
            success: true,
          },
          {
            id: 'preview-error',
            source: 'audit',
            action: 'preview.runtime.error',
            summary: 'Preview runtime error while loading deployment.',
            timestamp: '2026-05-04T10:01:00.000Z',
            actor: null,
            severity: 'error',
            success: false,
          },
        ]}
      />,
    );

    expect(screen.getByTestId('observe-health-matrix')).toHaveTextContent('Kernel health');
    expect(screen.getByTestId('observe-health-matrix')).toHaveTextContent('App health');
    expect(screen.getByTestId('observe-health-matrix')).toHaveTextContent('Agent health');
    expect(screen.getAllByTestId('observe-health-item')).toHaveLength(3);
    expect(screen.getByTestId('observe-recommendations')).toHaveTextContent('Kernel lifecycle truth degraded');
    expect(screen.getByTestId('observe-recommendations')).toHaveTextContent('Repair preview runtime');
    expect(screen.getByTestId('observe-recommendations')).toHaveTextContent('Agent policy approval expired');
  });

  it('surfaces preview security status in run and observe phases', () => {
    const previewHealth = {
      isHealthy: false,
      status: 'unsafe',
      issues: ['Preview trust level is untrusted'],
      security: {
        trustLevel: 'untrusted',
        tokenScopes: [
          { id: 'preview:read', name: 'Preview read', required: true, granted: true },
          { id: 'preview:inspect', name: 'Preview inspect', required: true, granted: false },
        ],
        expiresAt: '2026-05-04T11:00:00.000Z',
        expired: false,
        safe: false,
        issues: ['Preview trust level is untrusted', '1 required preview token scope(s) are not granted'],
      },
    };

    const { rerender } = render(
      <PhaseStatusPanels
        phase="run"
        preview={null}
        previewHealth={previewHealth}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('preview-security-status')).toBeInTheDocument();
    expect(screen.getByTestId('preview-security-safe')).toHaveTextContent('Unsafe');
    expect(screen.getByTestId('preview-security-trust')).toHaveTextContent('untrusted');
    expect(screen.getByTestId('preview-security-missing-scopes')).toHaveTextContent('Preview inspect');

    rerender(
      <PhaseStatusPanels
        phase="observe"
        preview={null}
        previewHealth={previewHealth}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('preview-security-status')).toBeInTheDocument();
    expect(screen.getAllByTestId('preview-security-issue')[0]).toHaveTextContent('Preview trust level is untrusted');
  });

  it('renders run status, timeline, health, retry, rollback, and promote readiness', () => {
    render(
      <PhaseStatusPanels
        phase="run"
        preview={{
          projectId: 'proj-1',
          currentPhase: 'RUN',
          nextPhase: 'OBSERVE',
          canAdvance: false,
          readiness: 48,
          blockers: ['Runtime health degraded'],
          requiredArtifacts: ['Preview token', 'Run evidence'],
          completedArtifacts: [],
          estimatedReadyIn: 'Needs operator action',
          estimatedReadyInHours: 1,
          predictionConfidence: 0.6,
          checkedAt: '2026-05-04T10:00:00.000Z',
        }}
        blockers={[
          {
            id: 'run-blocker-1',
            title: 'Runtime health degraded',
            description: 'Preview runtime returned 500.',
            severity: 'high',
            source: 'platform-run',
          },
        ]}
        activity={[
          {
            id: 'run-failed',
            source: 'platform-run',
            action: 'run.workflow.failed',
            summary: 'Run workflow failed during preview deploy.',
            timestamp: '2026-05-04T10:00:00.000Z',
            actor: 'system',
            severity: 'error',
            success: false,
          },
          {
            id: 'retry-linked',
            source: 'platform-run',
            action: 'run.retry.available',
            summary: 'Retry evidence linked for failed run.',
            timestamp: '2026-05-04T10:01:00.000Z',
            actor: 'operator',
            severity: null,
            success: true,
          },
        ]}
      />,
    );

    expect(screen.getByTestId('platform-run-status')).toHaveTextContent('Platform run status');
    expect(screen.getByTestId('platform-run-health')).toHaveTextContent('failed');
    expect(screen.getByTestId('platform-run-timeline')).toHaveTextContent('Run workflow failed');
    expect(screen.getAllByTestId('platform-run-event')).toHaveLength(2);
    expect(screen.getByTestId('run-operation-retry')).toHaveTextContent('available');
    expect(screen.getByTestId('run-operation-rollback')).toHaveTextContent('ready');
    expect(screen.getByTestId('run-operation-promote')).toHaveTextContent('blocked');
  });

  it('surfaces agent governance status and learning evidence in observe and learn phases', () => {
    const agentGovernance = {
      isHealthy: true,
      status: 'healthy',
      governanceState: 'policy-approved',
      learningLevel: 'evidence-backed',
      evidenceIds: ['learn-run-1', 'learn-approval-1'],
      issues: [],
    };

    const { rerender } = render(
      <PhaseStatusPanels
        phase="observe"
        preview={null}
        agentGovernance={agentGovernance}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('agent-governance-health')).toBeInTheDocument();
    expect(screen.getByTestId('agent-governance-state')).toHaveTextContent('policy-approved');
    expect(screen.getByTestId('agent-learning-level')).toHaveTextContent('evidence-backed');
    expect(screen.getByTestId('agent-learning-evidence')).toHaveTextContent('learn-run-1');

    rerender(
      <PhaseStatusPanels
        phase="learn"
        preview={null}
        agentGovernance={agentGovernance}
        blockers={[]}
        activity={[]}
      />,
    );

    expect(screen.getByTestId('agent-governance-status')).toHaveTextContent('healthy');
    expect(screen.getByTestId('agent-learning-evidence')).toHaveTextContent('learn-approval-1');
  });

  it('renders learn evidence, recommendations, approvals, prompt learning, and agent learning', () => {
    render(
      <PhaseStatusPanels
        phase="learn"
        preview={null}
        agentGovernance={{
          isHealthy: false,
          status: 'degraded',
          governanceState: 'needs-human-review',
          learningLevel: 'quarantined',
          evidenceIds: ['agent-learn-1'],
          issues: ['Agent output requires approval before promotion.'],
        }}
        blockers={[
          {
            id: 'policy-blocker',
            title: 'Policy learning review',
            description: 'Capture denied policy evidence before promoting this learning.',
            severity: 'high',
            source: 'governance',
          },
        ]}
        activity={[
          {
            id: 'run-failure-learning',
            source: 'platform-run',
            action: 'learn.evidence.created',
            summary: 'Failed run evidence created a remediation recommendation.',
            timestamp: '2026-05-04T10:00:00.000Z',
            actor: 'system',
            severity: 'error',
            success: false,
          },
          {
            id: 'human-approval',
            source: 'approval',
            action: 'learn.approval.approved',
            summary: 'Human approval accepted the rollback learning.',
            timestamp: '2026-05-04T10:01:00.000Z',
            actor: 'owner',
            severity: null,
            success: true,
          },
          {
            id: 'prompt-score',
            source: 'prompt-registry',
            action: 'prompt.evaluation.scored',
            summary: 'Prompt model evaluation scored 0.91 and promoted the active version.',
            timestamp: '2026-05-04T10:02:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
        ]}
      />,
    );

    expect(screen.getByTestId('learn-evidence-panel')).toHaveTextContent('Failed run evidence');
    expect(screen.getAllByTestId('learn-evidence-item')).toHaveLength(3);
    expect(screen.getByTestId('learn-recommendations-panel')).toHaveTextContent('Policy learning review');
    expect(screen.getByTestId('learn-recommendations-panel')).toHaveTextContent('Convert failure into learning');
    expect(screen.getByTestId('learn-human-approval-panel')).toHaveTextContent('approved');
    expect(screen.getByTestId('learn-prompt-learning-panel')).toHaveTextContent('Prompt model evaluation scored 0.91');
    expect(screen.getByTestId('agent-governance-health')).toHaveTextContent('needs-human-review');
    expect(screen.getByTestId('agent-learning-evidence')).toHaveTextContent('agent-learn-1');
  });

  it('renders evolve proposals, impact, diff review, approval, and re-run readiness', () => {
    render(
      <PhaseStatusPanels
        phase="evolve"
        preview={null}
        blockers={[]}
        activity={[
          {
            id: 'proposal-created',
            source: 'evolution',
            action: 'evolve.proposal.created',
            summary: 'Evolution proposal prop-1 links observe outage evidence to a login remediation plan.',
            timestamp: '2026-05-04T10:00:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
          {
            id: 'impact-ready',
            source: 'evolution',
            action: 'evolve.impact.ready',
            summary: 'Impact analysis affects web surface, auth module, and preview smoke tests.',
            timestamp: '2026-05-04T10:01:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
          {
            id: 'diff-review',
            source: 'patch-review',
            action: 'evolve.diff.review_requested',
            summary: 'Diff review is pending for 4 changed files with rollback available.',
            timestamp: '2026-05-04T10:02:00.000Z',
            actor: 'owner',
            severity: 'warning',
            success: true,
          },
          {
            id: 'approval',
            source: 'approval',
            action: 'evolve.approval.approved',
            summary: 'Human approval accepted the evolve diff.',
            timestamp: '2026-05-04T10:03:00.000Z',
            actor: 'owner',
            severity: null,
            success: true,
          },
          {
            id: 'handoff',
            source: 'evolution',
            action: 'evolve.handoff.dispatched',
            summary: 'Approved evolve proposal handed off for revalidate and regenerate.',
            timestamp: '2026-05-04T10:04:00.000Z',
            actor: 'system',
            severity: null,
            success: true,
          },
        ]}
      />,
    );

    expect(screen.getByTestId('evolve-proposals-panel')).toHaveTextContent('Evolution proposal prop-1');
    expect(screen.getByTestId('evolve-impact-panel')).toHaveTextContent('auth module');
    expect(screen.getByTestId('evolve-diff-review-panel')).toHaveTextContent('4 changed files');
    expect(screen.getByTestId('evolve-approval-panel')).toHaveTextContent('Human approval accepted');
    expect(screen.getByTestId('evolve-rerun-panel')).toHaveTextContent('ready');
    expect(screen.getByTestId('evolve-rerun-panel')).toHaveTextContent('revalidate and regenerate');
  });
});
