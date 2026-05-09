import React from 'react';
import { Link, useParams, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useAiActionLog, useAiActionDetail } from '@/hooks/useAiActionLog';
import { ApiError } from '@/lib/http-client';
import { PageStateNotice } from '@/components/PageStateNotice';
import { AIProvenancePanel } from '@/components/AIProvenancePanel';
import type { AiActionLogEntry } from '@/types/ai-action';

type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

type ProvenanceCompleteness = 'COMPLETE' | 'PARTIAL' | 'MISSING';

function deriveRiskLevel(entry: AiActionLogEntry): RiskLevel {
  const confidence = entry.confidence ?? 0;

  if (entry.status === 'BLOCKED' || entry.status === 'REJECTED' || confidence < 0.4) {
    return 'HIGH';
  }

  if (entry.status === 'PROPOSED' || confidence < 0.7) {
    return 'MEDIUM';
  }

  return 'LOW';
}

function riskText(level: RiskLevel): string {
  if (level === 'HIGH') {
    return 'High risk: blocked/rejected action or low confidence output.';
  }
  if (level === 'MEDIUM') {
    return 'Moderate risk: proposed output or moderate confidence requiring review.';
  }
  return 'Low risk: executed/approved output with stronger confidence.';
}

function provenanceCompleteness(entry: AiActionLogEntry): ProvenanceCompleteness {
  const score = [
    entry.provider != null && entry.provider.trim().length > 0,
    entry.modelVersion != null && entry.modelVersion.trim().length > 0,
    entry.confidence != null,
    entry.policyChecks.length > 0,
    entry.evidenceLinks.length > 0,
    entry.actor.trim().length > 0,
  ].filter(Boolean).length;

  if (score >= 6) {
    return 'COMPLETE';
  }
  if (score >= 3) {
    return 'PARTIAL';
  }
  return 'MISSING';
}

export function AiActionLogPage(): React.ReactElement {
  const { workspaceId, actionId } = useParams<{ workspaceId: string; actionId?: string }>();
  const { isAuthenticated } = useAuth();

  const { entries, isLoading, isError, error } = useAiActionLog(workspaceId ?? null);
  const detail = useAiActionDetail(workspaceId ?? null, actionId ?? null);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <section data-testid="ai-action-log-page" className="max-w-6xl mx-auto px-4 py-8 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">AI Action Log</h1>
        <Link
          to={`/workspaces/${workspaceId}/dashboard`}
          className="text-sm text-blue-600 hover:underline"
        >
          Back to dashboard
        </Link>
      </div>

      {isLoading && (
        <PageStateNotice
          testId="ai-action-log-page-loading"
          tone="loading"
          message="Loading…"
        />
      )}
      {isError && (
        <PageStateNotice
          testId="ai-action-log-page-error"
          tone="error"
          message={error instanceof ApiError ? error.getUserMessage() : 'Failed to load action log'}
        />
      )}

      {!isLoading && !isError && entries.length === 0 && (
        <PageStateNotice testId="ai-action-log-page-empty" tone="empty" message="No actions recorded." />
      )}

      {!isLoading && !isError && entries.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <section className="border rounded-lg p-4">
            <h2 className="text-sm font-semibold text-gray-700 mb-2">Timeline</h2>
            <ul className="space-y-2">
              {entries.map((entry) => (
                <li key={entry.actionId} data-testid={`ai-action-log-item-${entry.actionId}`} className="border rounded p-2 text-sm">
                  <div className="font-medium">{entry.summary}</div>
                  <div className="text-xs text-gray-500">
                    {entry.status} · {entry.actionType} · correlation {entry.correlationId}
                  </div>
                  <Link
                    to={`/workspaces/${workspaceId}/ai-actions/${entry.actionId}`}
                    data-testid={`ai-action-log-detail-link-${entry.actionId}`}
                    className="text-xs text-blue-600 hover:underline"
                  >
                    View details
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          <section className="border rounded-lg p-4" data-testid="ai-action-log-detail-panel">
            <h2 className="text-sm font-semibold text-gray-700 mb-2">Details</h2>
            {!actionId && (
              <PageStateNotice
                testId="ai-action-log-detail-empty"
                tone="empty"
                message="Select an action from the timeline."
              />
            )}
            {detail.isLoading && (
              <PageStateNotice
                testId="ai-action-log-detail-loading"
                tone="loading"
                message="Loading detail…"
              />
            )}
            {detail.isError && (
              <PageStateNotice
                testId="ai-action-log-detail-error"
                tone="error"
                message="Failed to load detail."
              />
            )}
            {detail.entry && (() => {
              const level = deriveRiskLevel(detail.entry);
              const completeness = provenanceCompleteness(detail.entry);
              const isRedacted = detail.entry.details === 'REDACTED';

              return (
                <div className="text-xs space-y-3">
                  {isRedacted && (
                    <PageStateNotice
                      testId="ai-action-detail-redacted"
                      tone="warning"
                      message="Sensitive details were redacted for your permission level."
                    />
                  )}
                  <div className="space-y-2" data-testid="ai-action-log-detail-fields">
                    <div><strong>Summary:</strong> {detail.entry.summary}</div>
                    <div><strong>Details:</strong> {detail.entry.details}</div>
                    <div><strong>Execution state:</strong> {detail.entry.status}</div>
                    <div><strong>Action type:</strong> {detail.entry.actionType}</div>
                    <div><strong>Correlation:</strong> {detail.entry.correlationId}</div>
                    <div><strong>Actor:</strong> {detail.entry.actor}</div>
                    <div><strong>Initiated by:</strong> {detail.entry.initiatedByAi ? 'AI agent' : 'Human user'}</div>
                    <div><strong>Provider:</strong> {detail.entry.provider ?? 'Not provided'}</div>
                    <div><strong>Model version:</strong> {detail.entry.modelVersion ?? 'Not provided'}</div>
                    <div><strong>Human edited:</strong> {detail.entry.humanEdited ? 'Yes' : 'No'}</div>
                    <div>
                      <strong>Confidence:</strong>{' '}
                      {detail.entry.confidence == null
                        ? 'Not provided'
                        : `${Math.round(detail.entry.confidence * 100)}%`}
                    </div>
                    <div data-testid="ai-action-risk-level">
                      <strong>Derived risk signal:</strong> {level} ({riskText(level)})
                    </div>
                    <div data-testid="ai-action-provenance-completeness">
                      <strong>Provenance completeness:</strong> {completeness}
                    </div>
                  </div>

                  {completeness !== 'COMPLETE' && (
                    <PageStateNotice
                      testId="ai-action-provenance-warning"
                      tone="warning"
                      message="This action is missing some provenance metadata and should be reviewed before execution."
                    />
                  )}

                  <div>
                    <strong>Policy checks:</strong>
                    {detail.entry.policyChecks.length === 0 ? (
                      <p className="mt-1 text-gray-500" data-testid="ai-action-policy-checks-empty">
                        No policy checks were attached to this action.
                      </p>
                    ) : (
                      <ul className="mt-1 list-disc list-inside space-y-1" data-testid="ai-action-policy-checks">
                        {detail.entry.policyChecks.map((policyCheck) => (
                          <li key={policyCheck}>{policyCheck}</li>
                        ))}
                      </ul>
                    )}
                  </div>

                  <div>
                    <strong>Evidence links:</strong>
                    {detail.entry.evidenceLinks.length === 0 ? (
                      <p className="mt-1 text-gray-500" data-testid="ai-action-evidence-empty">
                        No evidence links were attached to this action.
                      </p>
                    ) : (
                      <ul className="mt-1 list-disc list-inside space-y-1" data-testid="ai-action-evidence-links">
                        {detail.entry.evidenceLinks.map((link) => (
                          <li key={link}>
                            <a
                              href={link}
                              target="_blank"
                              rel="noreferrer"
                              className="text-blue-600 hover:underline"
                            >
                              {link}
                            </a>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  <AIProvenancePanel
                    provider={detail.entry.provider}
                    modelVersion={detail.entry.modelVersion ?? 'model-version-not-provided'}
                    generatedAt={detail.entry.occurredAt}
                    generatedBy={detail.entry.actor}
                    rationale={detail.entry.summary}
                    assumptions={detail.entry.details}
                    riskAssessment={riskText(level)}
                    evidence={detail.entry.evidenceLinks}
                    confidence={detail.entry.confidence}
                  />
                </div>
              );
            })()}
          </section>
        </div>
      )}
    </section>
  );
}
