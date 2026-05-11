/**
 * Generation Review Panel
 *
 * Displays side-by-side diffs, risk assessment, provenance, and action buttons
 * for reviewing AI-generated artifacts.
 *
 * @doc.type component
 * @doc.purpose Review UI for generated artifacts
 * @doc.layer product
 * @doc.pattern Review Component
 */

import React, { useState } from 'react';
import { Button } from '../ui/Button';

export interface DiffRegion {
  readonly artifactId: string;
  readonly regionId: string;
  readonly oldStartLine: number;
  readonly oldEndLine: number;
  readonly newStartLine: number;
  readonly newEndLine: number;
  readonly regionType: 'added' | 'removed' | 'modified' | 'context';
  readonly content: string;
}

export interface DiffOwnership {
  readonly actorId: string;
  readonly actorType: 'ai' | 'user' | 'system';
  readonly sourceId: string;
  readonly sessionId: string | null;
  readonly generationRunId: string | null;
  readonly timestamp: string;
  readonly metadata: Record<string, string>;
}

export interface ArtifactDiff {
  readonly artifactId: string;
  readonly changeType: 'added' | 'modified' | 'deleted';
  readonly oldContentRef: string | null;
  readonly newContentRef: string | null;
  readonly diffText: string;
  readonly diffRegions: readonly DiffRegion[];
  readonly ownership: DiffOwnership | null;
}

export interface RiskAssessment {
  readonly level: 'low' | 'medium' | 'high' | 'critical';
  readonly score: number;
  readonly factors: readonly string[];
  readonly recommendations: readonly string[];
}

export interface ReviewProvenance {
  readonly sessionId: string | null;
  readonly traceId: string | null;
  readonly source: string;
  readonly clientVersion: string | null;
  readonly decisionTimestamp: string;
  readonly metadata: Record<string, string>;
}

export interface GenerationReviewData {
  readonly runId: string;
  readonly diffs: readonly ArtifactDiff[];
  readonly riskAssessment: RiskAssessment;
  readonly provenance: ReviewProvenance;
  readonly canRollback: boolean;
  readonly rollbackReason: string | null;
}

interface GenerationReviewPanelProps {
  readonly data: GenerationReviewData | null;
  readonly isPending: boolean;
  readonly onApply: () => void;
  readonly onReject: () => void;
  readonly onRollback: () => void;
}

const RISK_LEVEL_CONFIG = {
  low: {
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
    borderColor: 'border-success-border',
    icon: '✓',
  },
  medium: {
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
    borderColor: 'border-warning-border',
    icon: '⚠',
  },
  high: {
    color: 'text-fg',
    bgColor: 'bg-destructive/10',
    borderColor: 'border-destructive',
    icon: '!',
  },
  critical: {
    color: 'text-destructive',
    bgColor: 'bg-destructive/10',
    borderColor: 'border-destructive',
    icon: '⛔',
  },
} as const;

export function GenerationReviewPanel({
  data,
  isPending,
  onApply,
  onReject,
  onRollback,
}: GenerationReviewPanelProps) {
  const [selectedDiff, setSelectedDiff] = useState<ArtifactDiff | null>(null);
  const [showProvenance, setShowProvenance] = useState(false);

  if (!data) {
    return (
      <div className="rounded-xl border border-border bg-surface-muted p-6 text-center">
        <p className="text-sm text-fg-muted">No generation data available for review.</p>
      </div>
    );
  }

  const riskConfig = RISK_LEVEL_CONFIG[data.riskAssessment.level];

  return (
    <div
      className="space-y-4"
      data-testid="generation-review-panel"
    >
      {/* Risk Assessment Section */}
      <section
        className={`rounded-xl border ${riskConfig.borderColor} ${riskConfig.bgColor} p-4`}
        data-testid="risk-assessment"
      >
        <div className="flex items-center gap-3">
          <span className="text-2xl" aria-hidden="true">{riskConfig.icon}</span>
          <div>
            <h3 className="font-semibold text-fg">
              Risk Level: {data.riskAssessment.level.toUpperCase()}
            </h3>
            <p className="text-sm text-fg-muted">
              Score: {data.riskAssessment.score}/100
            </p>
          </div>
        </div>
        {data.riskAssessment.factors.length > 0 && (
          <div className="mt-3">
            <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
              Risk Factors
            </p>
            <ul className="mt-1 space-y-1 text-sm text-fg">
              {data.riskAssessment.factors.map((factor, index) => (
                <li key={index} className="flex items-start gap-2">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-current opacity-50" />
                  <span>{factor}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
        {data.riskAssessment.recommendations.length > 0 && (
          <div className="mt-3">
            <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
              Recommendations
            </p>
            <ul className="mt-1 space-y-1 text-sm text-fg">
              {data.riskAssessment.recommendations.map((rec, index) => (
                <li key={index} className="flex items-start gap-2">
                  <span className="mt-1 h-1.5 w-1.5 rounded-full bg-current opacity-50" />
                  <span>{rec}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      {/* Diff Summary */}
      <section
        className="rounded-xl border border-border bg-surface-raised p-4"
        data-testid="diff-summary"
      >
        <h3 className="font-semibold text-fg">Changes Overview</h3>
        <p className="mt-1 text-sm text-fg-muted">
          {data.diffs.length} artifact(s) changed
        </p>
        <div className="mt-3 space-y-2">
          {data.diffs.map((diff) => (
            <button
              key={diff.artifactId}
              type="button"
              onClick={() => setSelectedDiff(diff)}
              className={`w-full rounded-lg border p-3 text-left transition-colors ${
                selectedDiff?.artifactId === diff.artifactId
                  ? 'border-primary bg-primary/5'
                  : 'border-border bg-surface-muted hover:border-border-hover'
              }`}
              data-testid={`diff-item-${diff.artifactId}`}
            >
              <div className="flex items-center justify-between">
                <span className="font-medium text-fg">{diff.artifactId}</span>
                <span
                  className={`text-xs font-semibold uppercase ${
                    diff.changeType === 'added'
                      ? 'text-success-color'
                      : diff.changeType === 'deleted'
                        ? 'text-destructive'
                        : 'text-warning-color'
                  }`}
                >
                  {diff.changeType}
                </span>
              </div>
              {diff.ownership && (
                <div className="mt-1 text-xs text-fg-muted">
                  Generated by {diff.ownership.actorType}
                  {diff.ownership.sourceId && ` (${diff.ownership.sourceId})`}
                </div>
              )}
            </button>
          ))}
        </div>
      </section>

      {/* Side-by-Side Diff View */}
      {selectedDiff && (
        <section
          className="rounded-xl border border-border bg-surface-raised p-4"
          data-testid="diff-view"
        >
          <div className="mb-4 flex items-center justify-between">
            <h3 className="font-semibold text-fg">{selectedDiff.artifactId}</h3>
            <Button
              type="button"
              variant="ghost"
              size="small"
              onClick={() => setSelectedDiff(null)}
            >
              Close
            </Button>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-lg border border-border bg-surface-muted p-3">
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Original
              </p>
              <pre className="mt-2 overflow-x-auto text-xs text-fg">
                {selectedDiff.oldContentRef || '<deleted>'}
              </pre>
            </div>
            <div className="rounded-lg border border-border bg-surface-muted p-3">
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Generated
              </p>
              <pre className="mt-2 overflow-x-auto text-xs text-fg">
                {selectedDiff.newContentRef || '<new file>'}
              </pre>
            </div>
          </div>
          {selectedDiff.diffRegions.length > 0 && (
            <div className="mt-4">
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Diff Regions
              </p>
              <div className="mt-2 space-y-2">
                {selectedDiff.diffRegions.map((region, index) => (
                  <div
                    key={index}
                    className={`rounded border p-2 text-xs ${
                      region.regionType === 'added'
                        ? 'border-success-border bg-success-bg/30'
                        : region.regionType === 'removed'
                          ? 'border-destructive bg-destructive/10'
                          : 'border-warning-border bg-warning-bg/30'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-fg">
                        {region.regionType.toUpperCase()}
                      </span>
                      <span className="text-fg-muted">
                        Lines {region.oldStartLine}-{region.oldEndLine} →{' '}
                        {region.newStartLine}-{region.newEndLine}
                      </span>
                    </div>
                    <pre className="mt-1 overflow-x-auto text-fg-muted">
                      {region.content}
                    </pre>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      {/* Provenance Section */}
      <section
        className="rounded-xl border border-border bg-surface-raised p-4"
        data-testid="provenance"
      >
        <button
          type="button"
          onClick={() => setShowProvenance(!showProvenance)}
          className="flex w-full items-center justify-between"
          aria-expanded={showProvenance}
        >
          <h3 className="font-semibold text-fg">Provenance & Audit Trail</h3>
          <span className="text-fg-muted">{showProvenance ? '▼' : '▶'}</span>
        </button>
        {showProvenance && (
          <div className="mt-4 space-y-3 text-sm">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Session
              </p>
              <p className="text-fg">{data.provenance.sessionId || 'N/A'}</p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Trace ID
              </p>
              <p className="text-fg">{data.provenance.traceId || 'N/A'}</p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Source
              </p>
              <p className="text-fg">{data.provenance.source}</p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Client Version
              </p>
              <p className="text-fg">{data.provenance.clientVersion || 'N/A'}</p>
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                Decision Timestamp
              </p>
              <p className="text-fg">
                {new Date(data.provenance.decisionTimestamp).toLocaleString()}
              </p>
            </div>
            {Object.keys(data.provenance.metadata).length > 0 && (
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-fg-muted">
                  Additional Metadata
                </p>
                <dl className="mt-1 space-y-1">
                  {Object.entries(data.provenance.metadata).map(([key, value]) => (
                    <div key={key} className="flex gap-2">
                      <dt className="font-medium text-fg-muted">{key}:</dt>
                      <dd className="text-fg">{value}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            )}
          </div>
        )}
      </section>

      {/* Action Buttons */}
      <section
        className="rounded-xl border border-border bg-surface-raised p-4"
        data-testid="review-actions"
      >
        <h3 className="font-semibold text-fg">Review Decision</h3>
        <p className="mt-1 text-sm text-fg-muted">
          Apply approved diffs, reject unsafe changes, or roll back an already-applied generation run.
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            tone="success"
            size="small"
            className="border-success-border bg-success-bg text-success-color"
            data-testid="generate-apply"
            disabled={isPending}
            onClick={onApply}
          >
            Apply Changes
          </Button>
          <Button
            type="button"
            variant="outline"
            tone="warning"
            size="small"
            className="border-warning-border bg-warning-bg text-warning-color"
            data-testid="generate-reject"
            disabled={isPending}
            onClick={onReject}
          >
            Reject Changes
          </Button>
          {data.canRollback && (
            <Button
              type="button"
              variant="outline"
              tone="danger"
              size="small"
              className="border-destructive bg-destructive-bg text-destructive"
              data-testid="generate-rollback"
              disabled={isPending}
              onClick={onRollback}
              title={data.rollbackReason || undefined}
            >
              Roll Back
            </Button>
          )}
        </div>
        {!data.canRollback && data.rollbackReason && (
          <p className="mt-2 text-xs text-fg-muted">
            Rollback not available: {data.rollbackReason}
          </p>
        )}
      </section>
    </div>
  );
}
