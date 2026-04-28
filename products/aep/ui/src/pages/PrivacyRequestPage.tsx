/**
 * PrivacyRequestPage — operator workbench for GDPR/CCPA fulfilment requests.
 *
 * @doc.type page
 * @doc.purpose GDPR and CCPA intake, triage, and fulfilment workbench for tenant operators
 * @doc.layer frontend
 */
import React from 'react';
import { useMutation } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import {
  type ComplianceReport,
  type PrivacyPortabilityExport,
  type PrivacyRequestType,
  requestCcpaOptOut,
  requestGdprAccess,
  requestGdprErasure,
  requestGdprPortability,
} from '@/api/aep.api';
import { EmptyState } from '@/components/core/EmptyState';
import { ConfidenceExplanation } from '@/components/shared/ConfidenceExplanation';
import { useCapabilities } from '@/hooks/useCapabilities';
import { getAiConfidenceTier } from '@/lib/ai-assist';
import { getGovernanceUrl } from '@/lib/routes';
import { tenantIdAtom } from '@/stores/tenant.store';

type PrivacyRequestKind = PrivacyRequestType;

interface PrivacyOperationConfig {
  id: PrivacyRequestKind;
  label: string;
  shortLabel: string;
  description: string;
  identifierLabel: string;
  slaLabel: string;
}

interface PrivacyTriageResult {
  suggestedOperation: PrivacyRequestKind;
  confidence: number;
  rationale: string;
  matchedSignals: string[];
}

interface PrivacySubmissionResult {
  operation: PrivacyOperationConfig;
  submittedAt: string;
  report?: ComplianceReport;
  portabilityExport?: PrivacyPortabilityExport;
}

const PRIVACY_OPERATIONS: readonly PrivacyOperationConfig[] = [
  {
    id: 'gdpr_access',
    label: 'GDPR access request',
    shortLabel: 'Access',
    description: 'Collect and return the subject record footprint currently known to the platform.',
    identifierLabel: 'Subject ID',
    slaLabel: 'Target fulfilment window: 30 days',
  },
  {
    id: 'gdpr_erasure',
    label: 'GDPR erasure request',
    shortLabel: 'Erasure',
    description: 'Delete the subject footprint across configured collections and surface any residual warnings.',
    identifierLabel: 'Subject ID',
    slaLabel: 'Target fulfilment window: 30 days',
  },
  {
    id: 'gdpr_portability',
    label: 'GDPR portability export',
    shortLabel: 'Portability',
    description: 'Produce a portable export bundle for the requested subject.',
    identifierLabel: 'Subject ID',
    slaLabel: 'Target fulfilment window: 30 days',
  },
  {
    id: 'ccpa_opt_out',
    label: 'CCPA opt-out request',
    shortLabel: 'Opt-out',
    description: 'Register a sale/share opt-out marker for the consumer record.',
    identifierLabel: 'Consumer ID',
    slaLabel: 'Target fulfilment window: same-day confirmation',
  },
] as const;

function getOperationConfig(operation: PrivacyRequestKind): PrivacyOperationConfig {
  return PRIVACY_OPERATIONS.find((item) => item.id === operation) ?? PRIVACY_OPERATIONS[0];
}

function classifyPrivacyRequest(text: string): PrivacyTriageResult | null {
  const normalized = text.trim().toLowerCase();
  if (!normalized) {
    return null;
  }

  const scores: Record<PrivacyRequestKind, number> = {
    gdpr_access: 0,
    gdpr_erasure: 0,
    gdpr_portability: 0,
    ccpa_opt_out: 0,
  };
  const matchedSignals: string[] = [];

  const signalMap: Array<{ keywords: string[]; operation: PrivacyRequestKind; weight: number; note: string }> = [
    { keywords: ['access', 'view', 'see my data', 'copy of my data'], operation: 'gdpr_access', weight: 0.3, note: 'Matched access/view language' },
    { keywords: ['delete', 'erase', 'remove me', 'right to be forgotten'], operation: 'gdpr_erasure', weight: 0.45, note: 'Matched deletion or erasure language' },
    { keywords: ['portable', 'export', 'download my data', 'transfer'], operation: 'gdpr_portability', weight: 0.45, note: 'Matched portability or export language' },
    { keywords: ['opt out', 'do not sell', 'do not share', 'ccpa'], operation: 'ccpa_opt_out', weight: 0.5, note: 'Matched CCPA opt-out language' },
  ];

  for (const signal of signalMap) {
    if (signal.keywords.some((keyword) => normalized.includes(keyword))) {
      scores[signal.operation] += signal.weight;
      matchedSignals.push(signal.note);
    }
  }

  if (normalized.includes('privacy') || normalized.includes('personal data')) {
    scores.gdpr_access += 0.1;
    scores.gdpr_erasure += 0.1;
    scores.gdpr_portability += 0.1;
    matchedSignals.push('Matched general privacy or personal-data wording');
  }

  const ranked = Object.entries(scores).sort((left, right) => right[1] - left[1]) as Array<[PrivacyRequestKind, number]>;
  const [suggestedOperation, topScore] = ranked[0];
  const secondScore = ranked[1]?.[1] ?? 0;
  const confidence = Math.max(0.35, Math.min(0.92, 0.45 + topScore - secondScore / 2));
  const operation = getOperationConfig(suggestedOperation);

  return {
    suggestedOperation,
    confidence,
    rationale:
      matchedSignals.length > 0
        ? `${operation.label} is recommended because the intake matched ${matchedSignals.length} request signal${matchedSignals.length === 1 ? '' : 's'}. Operator review is still required before submission.`
        : 'No strong request signal was detected, so this recommendation should be treated as advisory only.',
    matchedSignals,
  };
}

function formatJsonPayload(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

export function PrivacyRequestPage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const { capabilities, isLoading } = useCapabilities();
  const [requestSummary, setRequestSummary] = React.useState('');
  const [selectedOperation, setSelectedOperation] = React.useState<PrivacyRequestKind>('gdpr_access');
  const [subjectId, setSubjectId] = React.useState('');
  const [consumerId, setConsumerId] = React.useState('');
  const [lastResult, setLastResult] = React.useState<PrivacySubmissionResult | null>(null);
  const triage = classifyPrivacyRequest(requestSummary);
  const activeOperation = getOperationConfig(selectedOperation);
  const identifierValue = selectedOperation === 'ccpa_opt_out' ? consumerId : subjectId;

  const requestMutation = useMutation({
    mutationFn: async (operation: PrivacyRequestKind): Promise<PrivacySubmissionResult> => {
      const config = getOperationConfig(operation);
      if (operation === 'ccpa_opt_out') {
        const report = await requestCcpaOptOut(consumerId.trim(), tenantId);
        return {
          operation: config,
          submittedAt: new Date().toISOString(),
          report,
        };
      }
      if (operation === 'gdpr_portability') {
        const portabilityExport = await requestGdprPortability(subjectId.trim(), tenantId);
        return {
          operation: config,
          submittedAt: new Date().toISOString(),
          portabilityExport,
        };
      }
      const report =
        operation === 'gdpr_erasure'
          ? await requestGdprErasure(subjectId.trim(), tenantId)
          : await requestGdprAccess(subjectId.trim(), tenantId);
      return {
        operation: config,
        submittedAt: new Date().toISOString(),
        report,
      };
    },
    onSuccess: (result) => {
      setLastResult(result);
    },
  });

  if (isLoading) {
    return (
      <div className="flex h-full flex-col overflow-y-auto bg-gray-50 p-6 dark:bg-gray-950">
        <EmptyState
          title="Loading privacy controls…"
          description="Checking whether GDPR and CCPA fulfilment is enabled for this runtime."
        />
      </div>
    );
  }

  if (!capabilities.gdprCompliance) {
    return (
      <div className="flex h-full flex-col overflow-y-auto bg-gray-50 p-6 dark:bg-gray-950">
        <EmptyState
          title="Privacy fulfilment not available"
          description="This runtime does not currently expose the GDPR / CCPA compliance endpoints needed for request fulfilment."
        />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-gray-50 p-6 dark:bg-gray-950">
      <div className="mb-6 flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Privacy Requests</h1>
          <p className="mt-1 max-w-3xl text-sm text-gray-500 dark:text-gray-400">
            Operator workbench for GDPR and CCPA fulfilment. Requests execute against the live compliance endpoints today; a dedicated verification queue and chained audit timeline are still pending.
          </p>
        </div>
        <div className="rounded-full bg-white px-4 py-2 text-xs font-medium text-gray-500 shadow-sm dark:bg-gray-900 dark:text-gray-300">
          Tenant: {tenantId}
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(340px,0.85fr)]">
        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Intake and routing</h2>
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                Capture the request, review the AI triage recommendation, then submit the verified route.
              </p>
            </div>
            <span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-medium text-indigo-700 dark:bg-indigo-950/40 dark:text-indigo-300">
              Hybrid review required
            </span>
          </div>

          <label className="mt-5 block">
            <span className="text-sm font-medium text-gray-700 dark:text-gray-200">Request summary</span>
            <textarea
              value={requestSummary}
              onChange={(event) => setRequestSummary(event.target.value)}
              placeholder="Paste or summarise the privacy request here."
              className="mt-2 min-h-32 w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 shadow-sm outline-none ring-0 transition focus:border-indigo-400 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
            />
          </label>

          <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            {PRIVACY_OPERATIONS.map((operation) => (
              <button
                key={operation.id}
                type="button"
                onClick={() => setSelectedOperation(operation.id)}
                className={[
                  'rounded-xl border px-4 py-3 text-left transition',
                  selectedOperation === operation.id
                    ? 'border-indigo-300 bg-indigo-50 dark:border-indigo-700 dark:bg-indigo-950/40'
                    : 'border-gray-200 bg-gray-50 hover:border-gray-300 dark:border-gray-800 dark:bg-gray-950',
                ].join(' ')}
              >
                <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{operation.shortLabel}</p>
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{operation.slaLabel}</p>
              </button>
            ))}
          </div>

          {triage && (
            <div className="mt-5 rounded-2xl border border-indigo-200 bg-indigo-50/70 p-4 dark:border-indigo-900 dark:bg-indigo-950/30">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                    Suggested route: {getOperationConfig(triage.suggestedOperation).label}
                  </p>
                  <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{triage.rationale}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedOperation(triage.suggestedOperation)}
                  className="rounded-lg border border-indigo-300 px-3 py-2 text-xs font-medium text-indigo-700 hover:bg-indigo-100 dark:border-indigo-700 dark:text-indigo-300 dark:hover:bg-indigo-950"
                >
                  Use recommendation
                </button>
              </div>
              <ConfidenceExplanation
                className="mt-3"
                tier={getAiConfidenceTier(triage.confidence)}
                score={triage.confidence}
                reasoning={triage.rationale}
              />
              {triage.matchedSignals.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-2">
                  {triage.matchedSignals.map((signal) => (
                    <span
                      key={signal}
                      className="rounded-full bg-white px-2.5 py-1 text-[11px] font-medium text-gray-600 shadow-sm dark:bg-gray-900 dark:text-gray-300"
                    >
                      {signal}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="mt-5 grid gap-4 md:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
            <label className="block">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-200">{activeOperation.identifierLabel}</span>
              <input
                value={identifierValue}
                onChange={(event) =>
                  selectedOperation === 'ccpa_opt_out'
                    ? setConsumerId(event.target.value)
                    : setSubjectId(event.target.value)
                }
                placeholder={selectedOperation === 'ccpa_opt_out' ? 'consumer-123' : 'subject-123'}
                className="mt-2 h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-sm text-gray-900 shadow-sm outline-none transition focus:border-indigo-400 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
              />
            </label>
            <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-950">
              <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{activeOperation.label}</p>
              <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">{activeOperation.description}</p>
              <p className="mt-3 text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                {activeOperation.slaLabel}
              </p>
            </div>
          </div>

          {requestMutation.isError && (
            <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300">
              {requestMutation.error instanceof Error ? requestMutation.error.message : 'Failed to submit request.'}
            </div>
          )}

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={() => requestMutation.mutate(selectedOperation)}
              disabled={requestMutation.isPending || identifierValue.trim().length === 0}
              className="rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {requestMutation.isPending ? 'Submitting…' : `Submit ${activeOperation.shortLabel} request`}
            </button>
            <a
              href={getGovernanceUrl()}
              className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400"
            >
              Return to governance →
            </a>
          </div>
        </section>

        <section className="space-y-6">
          <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
            <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Verification status</h2>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
              Dedicated operator verification queue, chained audit linkage, and persisted SLA timers are not yet backed by the server. This workbench executes the verified compliance endpoint directly and makes that limitation explicit.
            </p>
            <div className="mt-4 grid gap-3">
              <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 dark:border-amber-900 dark:bg-amber-950/30">
                <p className="text-sm font-semibold text-amber-800 dark:text-amber-200">Queue pending</p>
                <p className="mt-1 text-sm text-amber-700 dark:text-amber-300">
                  Shift handoff or external ticketing is still needed for secondary verification.
                </p>
              </div>
              <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
                <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">Audit chain pending</p>
                <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                  The backend returns fulfilment results today, but does not yet expose a dedicated chained privacy-request timeline in the cockpit.
                </p>
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
            <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Latest fulfilment result</h2>
            {!lastResult ? (
              <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">
                Submit a verified request to review the latest fulfilment report or portability export here.
              </p>
            ) : (
              <div className="mt-4 space-y-4">
                <div>
                  <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{lastResult.operation.label}</p>
                  <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                    Submitted at {new Date(lastResult.submittedAt).toLocaleString()}
                  </p>
                </div>

                {lastResult.report && (
                  <>
                    <div className="grid gap-3 md:grid-cols-2">
                      <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
                        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Status</p>
                        <p className="mt-2 text-sm font-medium text-gray-900 dark:text-gray-100">
                          {lastResult.report.success ? 'Fulfilled' : 'Needs follow-up'}
                        </p>
                      </div>
                      <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
                        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Records affected</p>
                        <p className="mt-2 text-sm font-medium text-gray-900 dark:text-gray-100">{lastResult.report.total}</p>
                      </div>
                    </div>
                    <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
                      <p className="text-sm text-gray-700 dark:text-gray-200">{lastResult.report.message}</p>
                      {Object.keys(lastResult.report.breakdown).length > 0 && (
                        <div className="mt-3 flex flex-wrap gap-2">
                          {Object.entries(lastResult.report.breakdown).map(([key, value]) => (
                            <span
                              key={key}
                              className="rounded-full bg-white px-2.5 py-1 text-[11px] font-medium text-gray-700 shadow-sm dark:bg-gray-900 dark:text-gray-300"
                            >
                              {key}: {value}
                            </span>
                          ))}
                        </div>
                      )}
                      {lastResult.report.warnings.length > 0 && (
                        <ul className="mt-3 space-y-1 text-sm text-amber-700 dark:text-amber-300">
                          {lastResult.report.warnings.map((warning) => (
                            <li key={warning}>• {warning}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </>
                )}

                {lastResult.portabilityExport && (
                  <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-950">
                    <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">Portable export payload</p>
                    <pre className="mt-3 overflow-x-auto rounded-xl bg-gray-950 p-4 text-xs leading-relaxed text-gray-100">
                      {formatJsonPayload(lastResult.portabilityExport)}
                    </pre>
                  </div>
                )}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
