/**
 * GovernancePage — AEP governance dashboard (policies, compliance, tenancy, audit).
 *
 * @doc.type page
 * @doc.purpose AEP governance dashboard — policies, compliance, tenancy, audit
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import {
  activateGovernanceKillSwitch,
  deactivateGovernanceKillSwitch,
  getGovernanceOpsSummary,
  getGovernanceAuditSummary,
  getGovernanceComplianceSummary,
  getGovernanceTenancySummary,
  listConsentDecisions,
  type ConsentDecisionRecord,
  type ConsentDecisionStatus,
  listPolicies,
  type GovernanceAuditEntry,
  type LearnedPolicy,
  type PolicyStatus,
} from '@/api/aep.api';
import { isFeatureEnabled } from '@/lib/feature-flags';
import { Button } from '@ghatana/design-system';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';
import { PageState } from '@/components/shared/PageState';
import { Link } from 'react-router';
import { getEditPipelineUrl, getRunDetailUrl, getAgentRegistryUrl } from '@/lib/routes';
import { useAuth } from '@/context/AuthContext';

type GovSection = 'policies' | 'compliance' | 'tenancy' | 'audit' | 'consent' | 'operations';

const POLICY_STATUS_COLORS: Record<PolicyStatus, string> = {
  PENDING_REVIEW: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-300',
  APPROVED: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300',
  REJECTED: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300',
  ACTIVE: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  DEPRECATED: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
};

const SECTIONS: { id: GovSection; label: string; icon: string }[] = [
  {
    id: 'policies',
    label: 'Policies',
    icon: 'M3 6l3 1m0 0l-3 9a5.002 5.002 0 006.001 0M6 7l3 9M6 7l6-2m6 2l3-1m-3 1l-3 9a5.002 5.002 0 006.001 0M18 7l3 9m-3-9l-6-2m0-2v2m0 16V5m0 16H9m3 0h3',
  },
  {
    id: 'compliance',
    label: 'Compliance',
    icon: 'M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z',
  },
  {
    id: 'tenancy',
    label: 'Tenancy',
    icon: 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-2 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4',
  },
  {
    id: 'audit',
    label: 'Audit',
    icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01',
  },
  {
    id: 'consent',
    label: 'Consent',
    icon: 'M12 11c0-1.657 1.343-3 3-3h1a3 3 0 110 6h-1m-3-3v6m-4-9a3 3 0 100 6h1m3-3H8',
  },
  {
    id: 'operations',
    label: 'Operations',
    icon: 'M3 13h2l1 4h12l1-8H6m0 0L5 5H3',
  },
];

function formatOptionalTimestamp(value: string | null | undefined): string {
  if (!value) {
    return 'Unavailable';
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? 'Unavailable' : parsed.toLocaleString();
}

function formatPercent(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) {
    return 'Unavailable';
  }
  return `${(value * 100).toFixed(0)}%`;
}

function getPolicyAdvisor(policy: LearnedPolicy): { title: string; summary: string; tone: string } {
  if (policy.autoPromoted) {
    return {
      title: 'Auto-promoted',
      summary: 'This policy was promoted automatically. Review provenance and rollback pointer before leaving it fully active.',
      tone: 'border-green-200 bg-green-50 text-green-900 dark:border-green-900 dark:bg-green-950/40 dark:text-green-200',
    };
  }
  if (policy.autoPromotable) {
    return {
      title: 'Hybrid promotion advisor',
      summary: 'Confidence is high enough to recommend promotion, but a human should still review lineage and rollback readiness.',
      tone: 'border-indigo-200 bg-indigo-50 text-indigo-900 dark:border-indigo-900 dark:bg-indigo-950/40 dark:text-indigo-200',
    };
  }
  if (policy.status === 'PENDING_REVIEW') {
    return {
      title: 'Manual review required',
      summary: 'This candidate should stay in human review until confidence, evidence quality, and rollback readiness are confirmed.',
      tone: 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200',
    };
  }
  return {
    title: 'Historical decision',
    summary: 'Use the policy timeline and provenance block to understand how this decision was reached and what it replaced.',
    tone: 'border-gray-200 bg-gray-50 text-gray-700 dark:border-gray-800 dark:bg-gray-950 dark:text-gray-300',
  };
}

function getKillSwitchImpactPreview(active: boolean, globalActive: boolean, mode: string): string[] {
  const bullets = [
    active || globalActive
      ? 'Traffic for this tenant is already constrained; deactivation will resume normal execution paths.'
      : 'Activation will pause normal execution for the tenant while preserving governance, audit, and review visibility.',
    `Current degradation mode is ${mode}; kill-switch actions should be coordinated with that runtime posture rather than treated independently.`,
    'Operators should capture an incident reference and, when step-up auth is configured, provide an MFA code so the backend can attach an audit-chain entry.',
  ];
  if (!active && !globalActive) {
    bullets.push('Prefer pipeline and HITL containment first when the issue is isolated, then use the kill-switch when broad tenant protection is required.');
  }
  return bullets;
}

function PolicyFlagChip({ label, tone }: { label: string; tone: string }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium ${tone}`}>
      {label}
    </span>
  );
}

function downloadCsv(filename: string, rows: string[][]): void {
  const csv = rows
    .map((row) => row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(','))
    .join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function PolicyStatusBadge({ status }: { status: PolicyStatus }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${POLICY_STATUS_COLORS[status]}`}>
      {status.replace('_', ' ')}
    </span>
  );
}

function GovernanceBoundaryPanel({
  title,
  summary,
  bullets,
  locked = false,
}: {
  title: string;
  summary: string;
  bullets: string[];
  locked?: boolean;
}) {
  return (
    <div className="p-6">
      <div className="max-w-3xl rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-950">
        <div className={`rounded-lg border px-4 py-3 text-sm ${locked
          ? 'border-gray-300 bg-gray-50 text-gray-700 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300'
          : 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200'}`}>
          <p className="font-medium">{title}</p>
          <p className="mt-1">{summary}</p>
        </div>

        <div className="mt-5">
          <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Current boundary</h3>
          <ul className="list-disc space-y-2 pl-5 text-sm text-gray-700 dark:text-gray-300">
            {bullets.map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

function PoliciesPanel({ tenantId }: { tenantId: string }) {
  const [selectedPolicyId, setSelectedPolicyId] = useState<string | null>(null);
  const { data: policies, isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'policies', tenantId],
    queryFn: () => listPolicies(tenantId),
    staleTime: 60_000,
  });

  const activeCount = policies?.filter((policy) => policy.status === 'ACTIVE').length;
  const pendingCount = policies?.filter((policy) => policy.status === 'PENDING_REVIEW').length;
  const autoPromotableCount = policies?.filter((policy) => policy.autoPromotable).length;
  const autoPromotedCount = policies?.filter((policy) => policy.autoPromoted).length;

  if (isLoading) {
    return <PageState mode="loading" title="Loading policies…" description="Fetching governance policies for this tenant." className="h-full" />;
  }
  if (isError) {
    return <PageState mode="unavailable" title="Failed to load policies" description="The policy service is not reachable." onRetry={() => void refetch()} className="h-full" />;
  }
  if (!policies || policies.length === 0) {
    return <PageState mode="empty" title="No policies registered" description="Policies will appear once they are configured for this tenant." className="h-full" />;
  }

  const selectedPolicy = policies.find((policy) => policy.id === selectedPolicyId) ?? policies[0];
  const skillTimeline = policies
    .filter((policy) => policy.skillId === selectedPolicy.skillId)
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime());
  const advisor = getPolicyAdvisor(selectedPolicy);

  return (
    <div className="p-6">
      <div className="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[
          { label: 'Total policies', value: policies.length },
          { label: 'Active', value: activeCount },
          { label: 'Pending review', value: pendingCount },
          { label: 'Auto-promotable', value: autoPromotableCount },
          { label: 'Auto-promoted', value: autoPromotedCount },
          { label: 'Deprecated', value: policies.filter((policy) => policy.status === 'DEPRECATED').length },
        ].map((item) => (
          <div key={item.label} className="rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
            <p className="text-xs text-gray-500 dark:text-gray-400">{item.label}</p>
            <p className="mt-0.5 text-2xl font-semibold text-gray-900 dark:text-white">{item.value}</p>
          </div>
        ))}
      </div>

      <div className="overflow-auto rounded-lg border border-gray-200 dark:border-gray-800">
        <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-800">
          <thead className="bg-gray-50 dark:bg-gray-900">
            <tr>
              {['Policy', 'Skill', 'Version', 'Confidence', 'Status', 'Advisor', 'Actions'].map((header) => (
                <th key={header} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white dark:divide-gray-900 dark:bg-gray-950">
            {policies.map((policy) => (
              <tr
                key={policy.id}
                className={[
                  'transition-colors hover:bg-gray-50 dark:hover:bg-gray-900',
                  selectedPolicy.id === policy.id ? 'bg-indigo-50/40 dark:bg-indigo-950/20' : '',
                ].join(' ')}
              >
                <td className="px-4 py-3">
                  <button
                    type="button"
                    onClick={() => setSelectedPolicyId(policy.id)}
                    className="text-left"
                  >
                    <p className="font-medium text-gray-900 dark:text-white">{policy.name}</p>
                    <p className="mt-1 font-mono text-[11px] text-gray-500 dark:text-gray-400">{policy.id}</p>
                  </button>
                </td>
                <td className="px-4 py-3 text-gray-900 dark:text-white">{policy.skillId}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">v{policy.version}</td>
                <td className="px-4 py-3 text-gray-900 dark:text-white">
                  <span className={`font-semibold ${policy.confidenceScore >= 0.8 ? 'text-green-600 dark:text-green-400' : policy.confidenceScore >= 0.6 ? 'text-amber-600 dark:text-amber-400' : 'text-red-600 dark:text-red-400'}`}>
                    {(policy.confidenceScore * 100).toFixed(0)}%
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-2">
                    <PolicyStatusBadge status={policy.status} />
                    {policy.autoPromotable && (
                      <PolicyFlagChip
                        label="Auto-promotable"
                        tone="bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-300"
                      />
                    )}
                    {policy.autoPromoted && (
                      <PolicyFlagChip
                        label="Auto-promoted"
                        tone="bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300"
                      />
                    )}
                  </div>
                </td>
                <td className="px-4 py-3 text-xs text-gray-600 dark:text-gray-300">
                  {getPolicyAdvisor(policy).title}
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-2">
                    {policy.status === 'PENDING_REVIEW' && policy.reviewId && (
                      <Link
                        to={`/operate/reviews?id=${encodeURIComponent(policy.reviewId)}`}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        Review →
                      </Link>
                    )}
                    {policy.relatedPipelineId && (
                      <Link
                        to={getEditPipelineUrl(policy.relatedPipelineId)}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        Pipeline →
                      </Link>
                    )}
                    {policy.relatedRunId && (
                      <Link
                        to={getRunDetailUrl(policy.relatedRunId)}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        Run →
                      </Link>
                    )}
                    {policy.relatedAgentId && (
                      <Link
                        to={`${getAgentRegistryUrl()}/${encodeURIComponent(policy.relatedAgentId)}`}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        Agent →
                      </Link>
                    )}
                    <button
                      type="button"
                      onClick={() => setSelectedPolicyId(policy.id)}
                      className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                    >
                      Details →
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1.1fr)_minmax(320px,0.9fr)]">
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <div className={`rounded-lg border px-4 py-3 text-sm ${advisor.tone}`}>
            <p className="font-medium">{advisor.title}</p>
            <p className="mt-1">{advisor.summary}</p>
          </div>

          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
              <p className="text-xs text-gray-500 dark:text-gray-400">Activation mode</p>
              <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">
                {selectedPolicy.provenance?.activationMode ?? 'Unavailable'}
              </p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
              <p className="text-xs text-gray-500 dark:text-gray-400">Rollback target</p>
              <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">
                {selectedPolicy.provenance?.rollbackPointerId ?? 'Unavailable'}
              </p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
              <p className="text-xs text-gray-500 dark:text-gray-400">Promoted at</p>
              <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">
                {formatOptionalTimestamp(selectedPolicy.provenance?.promotedAt ?? selectedPolicy.decidedAt)}
              </p>
            </div>
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
              <p className="text-xs text-gray-500 dark:text-gray-400">Approver</p>
              <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">
                {selectedPolicy.provenance?.approverId ?? selectedPolicy.reviewerId ?? 'Unavailable'}
              </p>
            </div>
          </div>

          <div className="mt-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Gate evidence</h3>
            <p className="mt-2 text-sm text-gray-700 dark:text-gray-300">
              {(selectedPolicy.gateResult?.reason ?? selectedPolicy.description) || 'No evaluation summary available.'}
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <PolicyFlagChip
                label={`Gate ${selectedPolicy.gateResult?.gateName ?? 'unknown'}`}
                tone="bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300"
              />
              <PolicyFlagChip
                label={`Score ${formatPercent(selectedPolicy.gateResult?.score)}`}
                tone="bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300"
              />
              <PolicyFlagChip
                label={`Threshold ${formatPercent(selectedPolicy.gateResult?.threshold)}`}
                tone="bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300"
              />
            </div>
          </div>

          <div className="mt-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Source episodes</h3>
            <p className="mt-2 text-sm text-gray-700 dark:text-gray-300">
              {selectedPolicy.provenance?.sourceEpisodeIds.length ?? 0} source episodes contributed to this proposal.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              {(selectedPolicy.provenance?.sourceEpisodeIds ?? []).slice(0, 6).map((episodeId) => (
                <span key={episodeId} className="inline-flex rounded-full border border-gray-200 px-3 py-1 text-xs font-mono text-gray-600 dark:border-gray-700 dark:text-gray-300">
                  {episodeId}
                </span>
              ))}
            </div>
          </div>

          <div className="mt-5">
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Evaluation metrics</h3>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              {Object.entries(selectedPolicy.provenance?.evaluationMetrics ?? {}).map(([metric, value]) => (
                <div key={metric} className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
                  <p className="text-xs text-gray-500 dark:text-gray-400">{metric}</p>
                  <p className="mt-1 text-sm font-semibold text-gray-900 dark:text-white">
                    {metric.toLowerCase().includes('rate') ? formatPercent(value) : value}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Policy timeline for {selectedPolicy.skillId}</h3>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            Review and promotion history across policy candidates for this skill.
          </p>
          <div className="mt-4 space-y-3">
            {skillTimeline.map((policy) => (
              <div key={policy.id} className="rounded-lg border border-gray-200 px-4 py-3 dark:border-gray-800">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold text-gray-900 dark:text-white">v{policy.version} • {policy.name}</p>
                    <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                      Created {formatOptionalTimestamp(policy.createdAt)} • Decided {formatOptionalTimestamp(policy.decidedAt)}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <PolicyStatusBadge status={policy.status} />
                    {policy.autoPromoted && (
                      <PolicyFlagChip
                        label="Auto-promoted"
                        tone="bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300"
                      />
                    )}
                  </div>
                </div>
                <p className="mt-2 text-sm text-gray-700 dark:text-gray-300">
                  {policy.reviewerRationale ?? policy.provenance?.approverRationale ?? policy.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function CompliancePanel({ tenantId }: { tenantId: string }) {
  const { data: summary, isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'compliance', tenantId],
    queryFn: () => getGovernanceComplianceSummary(tenantId),
  });

  const [showAdvanced, setShowAdvanced] = useState(false);

  if (isLoading) {
    return <PageState mode="loading" title="Loading compliance…" description="Fetching compliance summary." className="h-full" />;
  }
  if (isError) {
    return <PageState mode="unavailable" title="Failed to load compliance data" description="The compliance service is not reachable." onRetry={() => void refetch()} className="h-full" />;
  }
  if (!summary) {
    return <PageState mode="degraded" title="No compliance data" description="Compliance metrics will populate once scans are run." className="h-full" />;
  }

  const freshnessTone = summary.soc2.freshness.status === 'FRESH'
    ? 'border-green-200 bg-green-50 text-green-900 dark:border-green-900 dark:bg-green-950/40 dark:text-green-200'
    : summary.soc2.freshness.status === 'STALE'
    ? 'border-red-200 bg-red-50 text-red-900 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200'
    : 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200';

  return (
    <div className="space-y-6 p-6">
      <div className={`rounded-lg border px-4 py-3 text-sm ${freshnessTone}`}>
        <p className="font-medium">
          SOC 2 report availability: {summary.soc2.freshness.reportAvailable ? 'Ready to render' : 'Blocked until evidence is refreshed'}
        </p>
        <p className="mt-1">{summary.soc2.freshness.message}</p>
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[
          { label: 'Configured', value: summary.configured ? 'Yes' : 'No' },
          { label: 'Operations', value: summary.supportedOperations.length },
          { label: 'Collections', value: summary.registeredCollections.length },
          { label: 'SOC2 controls', value: summary.soc2.controlCount },
          { label: 'Evidence age', value: summary.soc2.freshness.evidenceAgeDays ?? 'Unavailable' },
        ].map((item) => (
          <div key={item.label} className="rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
            <p className="text-xs text-gray-500 dark:text-gray-400">{item.label}</p>
            <p className="mt-0.5 text-2xl font-semibold text-gray-900 dark:text-white">{item.value}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Supported operations</h3>
          <div className="flex flex-wrap gap-2">
            {summary.supportedOperations.map((operation) => (
              <span key={operation} className="inline-flex items-center rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 dark:bg-blue-950 dark:text-blue-300">
                {operation.replace('_', ' ')}
              </span>
            ))}
          </div>

          <h4 className="mb-2 mt-5 text-sm font-semibold text-gray-900 dark:text-white">Registered collections</h4>
          <ul className="space-y-2 text-sm text-gray-700 dark:text-gray-300">
            {summary.registeredCollections.map((collection) => (
              <li key={collection} className="font-mono text-xs">{collection}</li>
            ))}
          </ul>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">SOC 2 posture</h3>
          <p className="text-sm text-gray-700 dark:text-gray-300">{summary.soc2.title}</p>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            Generated{' '}
            {Number.isNaN(new Date(summary.soc2.generatedAt).getTime())
              ? 'not available'
              : new Date(summary.soc2.generatedAt).toLocaleString()}
          </p>
          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
            Newest evidence: {formatOptionalTimestamp(summary.soc2.freshness.newestEvidenceAt)}
          </p>
          <p className={[
            'mt-3 inline-flex rounded-full px-3 py-1 text-xs font-semibold',
            summary.soc2.overallStatus === 'PASS'
              ? 'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300'
              : summary.soc2.overallStatus === 'FAIL'
              ? 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'
              : summary.soc2.overallStatus === 'PENDING'
              ? 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300'
              : 'bg-gray-50 text-gray-700 dark:bg-gray-950 dark:text-gray-300',
          ].join(' ')}>
            {summary.soc2.overallStatus}
          </p>

          <div className="mt-4">
            <Button
              onClick={() => setShowAdvanced(!showAdvanced)}
              variant="text"
              className="text-xs text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300 font-medium"
            >
              {showAdvanced ? 'Hide' : 'Show'} advanced controls
            </Button>
            {showAdvanced && (
              <div className="mt-3 space-y-2">
                {summary.soc2.controls.map((control) => (
                  <div key={control.controlId} className="flex items-start justify-between gap-3 rounded border border-gray-100 px-3 py-2 text-sm dark:border-gray-800">
                    <div className="min-w-0">
                      <p className="font-medium text-gray-900 dark:text-white">{control.controlId}</p>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{control.description}</p>
                      {(control.evidenceUrl || control.auditEntryId) && (
                        <div className="mt-1 flex gap-2 flex-wrap">
                          {control.evidenceUrl && (
                            <a
                              href={control.evidenceUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
                            >
                              Evidence →
                            </a>
                          )}
                          {control.auditEntryId && (
                            <span className="text-xs text-gray-400 font-mono">#{control.auditEntryId}</span>
                          )}
                        </div>
                      )}
                    </div>
                    <span className={[
                      'text-xs font-semibold',
                      control.status === 'PASS'
                        ? 'text-green-600 dark:text-green-400'
                        : control.status === 'FAIL'
                        ? 'text-red-600 dark:text-red-400'
                        : control.status === 'PENDING'
                        ? 'text-amber-600 dark:text-amber-400'
                        : 'text-gray-600 dark:text-gray-400',
                    ].join(' ')}>
                      {control.status}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function TenancyPanel({ tenantId }: { tenantId: string }) {
  const { roles } = useAuth();
  const [reason, setReason] = useState('');
  const [incidentId, setIncidentId] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['aep', 'governance', 'tenancy', tenantId],
    queryFn: () => getGovernanceTenancySummary(tenantId),
    staleTime: 15_000,
  });

  const activateMutation = useMutation({
    mutationFn: () =>
      activateGovernanceKillSwitch({
        tenantId,
        reason: reason.trim() || 'manual activation from governance cockpit',
        incidentId: incidentId.trim() || 'manual',
        mfaCode: mfaCode.trim() || undefined,
        userId: roles[0] ?? 'unknown',
      }),
    onSuccess: (result) => {
      setActionMessage(`Kill-switch activated${result.auditId ? ` with audit ${result.auditId}` : ''}.`);
      void refetch();
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: () =>
      deactivateGovernanceKillSwitch({
        tenantId,
        reason: reason.trim() || 'manual deactivation from governance cockpit',
        userId: roles[0] ?? 'unknown',
      }),
    onSuccess: (result) => {
      setActionMessage(`Kill-switch deactivated${result.auditId ? ` with audit ${result.auditId}` : ''}.`);
      void refetch();
    },
  });

  if (isLoading) {
    return <PageState mode="loading" title="Loading tenancy controls…" description="Fetching tenant isolation settings." className="h-full" />;
  }

  if (!data) {
    return <PageState mode="degraded" title="No tenancy data" description="Tenant isolation settings could not be loaded. The backend may be unavailable." className="h-full" />;
  }

  const impactPreview = getKillSwitchImpactPreview(data.active, data.globalActive, data.mode);

  return (
    <div className="space-y-6 p-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {[
          {
            label: 'Tenant kill-switch',
            value: data.active ? 'Active' : 'Inactive',
            tone: data.active ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400',
          },
          {
            label: 'Global kill-switch',
            value: data.globalActive ? 'Active' : 'Inactive',
            tone: data.globalActive ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400',
          },
          { label: 'Degradation mode', value: data.mode, tone: 'text-gray-900 dark:text-white' },
        ].map((item) => (
          <div key={item.label} className="rounded-lg border border-gray-200 bg-white px-4 py-4 dark:border-gray-800 dark:bg-gray-950">
            <p className="text-xs text-gray-500 dark:text-gray-400">{item.label}</p>
            <p className={`mt-1 text-xl font-semibold ${item.tone}`}>{item.value}</p>
          </div>
        ))}
      </div>

      <div className="max-w-3xl rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
        <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Runtime isolation posture</h3>
        <ul className="list-disc space-y-2 pl-5 text-sm text-gray-700 dark:text-gray-300">
          <li>Kill-switch state is loaded from the live governance backend for tenant <span className="font-mono">{tenantId}</span>.</li>
          <li>Degradation mode reflects the current runtime policy, not a UI placeholder.</li>
          <li>Editable tenant grants and per-capability quotas still require dedicated management endpoints before the UI can mutate them.</li>
        </ul>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Kill-switch impact preview</h3>
          <p className="mt-2 text-sm text-gray-700 dark:text-gray-300">
            Advisory preview for operators before activation or deactivation. Review remains required.
          </p>
          <ul className="mt-4 list-disc space-y-2 pl-5 text-sm text-gray-700 dark:text-gray-300">
            {impactPreview.map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Kill-switch controls</h3>
          <p className="mt-2 text-sm text-gray-700 dark:text-gray-300">
            When backend step-up verification is enabled, provide the MFA code so the action can be chained to the audit trail.
          </p>

          <div className="mt-4 grid gap-4">
            <label className="block">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-200">Reason</span>
              <input
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                className="mt-2 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
                placeholder="security incident, platform containment, manual recovery"
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-200">Incident ID</span>
              <input
                value={incidentId}
                onChange={(event) => setIncidentId(event.target.value)}
                className="mt-2 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
                placeholder="INC-2026-0428"
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-200">MFA code</span>
              <input
                value={mfaCode}
                onChange={(event) => setMfaCode(event.target.value)}
                className="mt-2 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-900 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
                placeholder="Optional when step-up auth is enforced"
              />
            </label>
          </div>

          {actionMessage && (
            <div className="mt-4 rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-800 dark:border-green-900 dark:bg-green-950/40 dark:text-green-200">
              {actionMessage}
            </div>
          )}
          {(activateMutation.isError || deactivateMutation.isError) && (
            <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
              {(activateMutation.error instanceof Error ? activateMutation.error.message : null)
                ?? (deactivateMutation.error instanceof Error ? deactivateMutation.error.message : 'Kill-switch action failed.')}
            </div>
          )}

          <div className="mt-5 flex flex-wrap gap-3">
            <Button
              onClick={() => activateMutation.mutate()}
              variant="primary"
              className="rounded px-4 py-2 text-sm font-medium"
              disabled={activateMutation.isPending || deactivateMutation.isPending}
            >
              {activateMutation.isPending ? 'Activating…' : 'Activate kill-switch'}
            </Button>
            <Button
              onClick={() => deactivateMutation.mutate()}
              variant="secondary"
              className="rounded px-4 py-2 text-sm font-medium"
              disabled={activateMutation.isPending || deactivateMutation.isPending}
            >
              {deactivateMutation.isPending ? 'Deactivating…' : 'Deactivate kill-switch'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

function AuditPanel({ tenantId }: { tenantId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['aep', 'governance', 'audit', tenantId],
    queryFn: () => getGovernanceAuditSummary(tenantId, 20),
    staleTime: 10_000,
    refetchInterval: 10_000,
  });

  if (isLoading) {
    return <PageState mode="loading" title="Loading audit evidence…" description="Fetching audit records for this tenant." className="h-full" />;
  }

  if (!data) {
    return <PageState mode="degraded" title="No audit data" description="Audit records could not be loaded. The backend may be unavailable." className="h-full" />;
  }

  return (
    <div className="space-y-4 p-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[
          { label: 'Ledger configured', value: data.configured ? 'Yes' : 'No' },
          { label: 'Entries', value: data.count },
          { label: 'Tenant', value: tenantId },
          { label: 'Last refresh', value: new Date(data.timestamp).toLocaleTimeString() },
        ].map((item) => (
          <div key={item.label} className="rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
            <p className="text-xs text-gray-500 dark:text-gray-400">{item.label}</p>
            <p className="mt-1 break-all text-sm font-semibold text-gray-900 dark:text-white">{item.value}</p>
          </div>
        ))}
      </div>

      {data.entries.length === 0 ? (
        <p className="py-10 text-center text-sm text-gray-400">No audit evidence has been recorded for this tenant yet.</p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-800">
          <table className="min-w-full divide-y divide-gray-200 bg-white text-sm dark:divide-gray-800 dark:bg-gray-950">
            <thead className="bg-gray-50 dark:bg-gray-900">
              <tr>
                {['Event', 'Run', 'Pipeline', 'Status', 'Timestamp'].map((header) => (
                  <th key={header} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-900">
              {data.entries.map((entry: GovernanceAuditEntry, index) => (
                <tr key={`${entry.eventType}-${entry.timestamp}-${index}`}>
                  <td className="px-4 py-3 font-mono text-xs text-gray-700 dark:text-gray-300">{entry.eventType}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500 dark:text-gray-400">{entry.runId ?? 'n/a'}</td>
                  <td className="px-4 py-3 text-gray-900 dark:text-white">{entry.pipelineId ?? 'n/a'}</td>
                  <td className="px-4 py-3 text-gray-700 dark:text-gray-300">{entry.status ?? 'n/a'}</td>
                  <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{new Date(entry.timestamp).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function ConsentPanel({ tenantId }: { tenantId: string }) {
  const [statusFilter, setStatusFilter] = useState<ConsentDecisionStatus | 'all'>('all');
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'governance', 'consent', tenantId],
    queryFn: () => listConsentDecisions(tenantId, 200, 0),
    staleTime: 15_000,
  });

  if (isLoading) {
    return <PageState mode="loading" title="Loading consent history…" description="Fetching tenant consent records." className="h-full" />;
  }
  if (isError) {
    return <PageState mode="unavailable" title="Failed to load consent records" description="The consent service is not reachable." onRetry={() => void refetch()} className="h-full" />;
  }
  if (!data) {
    return <PageState mode="degraded" title="No consent data" description="Consent records are unavailable for this tenant." className="h-full" />;
  }

  const filteredItems = data.items.filter((item) => statusFilter === 'all' || item.status === statusFilter);
  const grantedCount = data.items.filter((item) => item.status === 'granted').length;
  const deniedCount = data.items.filter((item) => item.status === 'denied').length;
  const withdrawnCount = data.items.filter((item) => item.status === 'withdrawn').length;

  const handleExport = (): void => {
    downloadCsv(`aep-consent-${tenantId}.csv`, [
      ['consentId', 'userId', 'status', 'purposes', 'decidedAt'],
      ...filteredItems.map((item) => [
        item.consentId,
        item.userId,
        item.status,
        item.purposes.join(';'),
        item.decidedAt,
      ]),
    ]);
  };

  return (
    <div className="space-y-6 p-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[
          { label: 'Total decisions', value: data.count },
          { label: 'Granted', value: grantedCount },
          { label: 'Denied', value: deniedCount },
          { label: 'Withdrawn', value: withdrawnCount },
        ].map((item) => (
          <div key={item.label} className="rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
            <p className="text-xs text-gray-500 dark:text-gray-400">{item.label}</p>
            <p className="mt-1 text-2xl font-semibold text-gray-900 dark:text-white">{item.value}</p>
          </div>
        ))}
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-gray-900 dark:text-white">Consent change history</h3>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
              Server-side consent is the authoritative record for tenant <span className="font-mono">{tenantId}</span>.
            </p>
          </div>
          <Button
            onClick={handleExport}
            variant="secondary"
            className="rounded px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
          >
            Export CSV
          </Button>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          {(['all', 'granted', 'denied', 'withdrawn'] as const).map((status) => (
            <Button
              key={status}
              onClick={() => setStatusFilter(status)}
              variant="text"
              className={[
                'rounded-full border px-3 py-1 text-xs font-medium',
                statusFilter === status
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700 dark:border-indigo-400 dark:bg-indigo-950 dark:text-indigo-300'
                  : 'border-gray-200 text-gray-600 dark:border-gray-700 dark:text-gray-300',
              ].join(' ')}
            >
              {status}
            </Button>
          ))}
        </div>

        {filteredItems.length === 0 ? (
          <div className="mt-6">
            <EmptyState
              title="No consent records for this filter"
              description="Adjust the status filter or wait for new consent decisions to arrive."
            />
          </div>
        ) : (
          <div className="mt-5 overflow-auto rounded-lg border border-gray-200 dark:border-gray-800">
            <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-800">
              <thead className="bg-gray-50 dark:bg-gray-900">
                <tr>
                  {['User', 'Status', 'Purposes', 'Changed at', 'Consent ID'].map((header) => (
                    <th key={header} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                      {header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 bg-white dark:divide-gray-900 dark:bg-gray-950">
                {filteredItems.map((item: ConsentDecisionRecord) => (
                  <tr key={item.consentId}>
                    <td className="px-4 py-3 text-gray-900 dark:text-white">{item.userId}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-700 dark:bg-gray-800 dark:text-gray-300">
                        {item.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-700 dark:text-gray-300">{item.purposes.length > 0 ? item.purposes.join(', ') : 'All purposes'}</td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{formatOptionalTimestamp(item.decidedAt)}</td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-500 dark:text-gray-400">{item.consentId}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function OperationsPanel({ tenantId }: { tenantId: string }) {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'governance', 'operations', tenantId],
    queryFn: () => getGovernanceOpsSummary(tenantId),
    staleTime: 30_000,
  });

  if (isLoading) {
    return <PageState mode="loading" title="Loading operations telemetry…" description="Fetching backup, DR, and export status." className="h-full" />;
  }
  if (isError) {
    return <PageState mode="unavailable" title="Failed to load operations telemetry" description="The governance ops summary is not reachable." onRetry={() => void refetch()} className="h-full" />;
  }
  if (!data) {
    return <PageState mode="degraded" title="No operations telemetry" description="Backup and DR signals are unavailable for this tenant." className="h-full" />;
  }

  return (
    <div className="space-y-6 p-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {[
          { label: 'Last backup', value: formatOptionalTimestamp(data.lastBackupAt) },
          { label: 'Backup count', value: data.backupCount },
          { label: 'DR readiness', value: data.drReadiness },
          { label: 'Export queue', value: data.exportQueueDepth ?? 'Unavailable' },
          { label: 'Trusted proxy alerts', value: data.trustedProxyForwardedRejectedCount },
        ].map((item) => (
          <div key={item.label} className="rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-950">
            <p className="text-xs text-gray-500 dark:text-gray-400">{item.label}</p>
            <p className="mt-1 break-words text-sm font-semibold text-gray-900 dark:text-white">{item.value}</p>
          </div>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Backup and DR posture</h3>
          <ul className="space-y-2 text-sm text-gray-700 dark:text-gray-300">
            <li>Latest backup status: <span className="font-medium">{data.latestBackupStatus}</span></li>
            <li>Automated backups scheduled: <span className="font-medium">{data.automatedBackupsScheduled ? 'Yes' : 'No'}</span></li>
            <li>Last DR drill: <span className="font-medium">{formatOptionalTimestamp(data.lastDrDrillAt)}</span></li>
          </ul>
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950">
          <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Trusted proxy alerting</h3>
          <div className={[
            'rounded-lg border px-3 py-2 text-sm',
            data.trustedProxyAlertState === 'ALERT'
              ? 'border-red-200 bg-red-50 text-red-900 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200'
              : data.trustedProxyAlertState === 'UNAVAILABLE'
              ? 'border-gray-200 bg-gray-50 text-gray-700 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-300'
              : 'border-green-200 bg-green-50 text-green-900 dark:border-green-900 dark:bg-green-950/40 dark:text-green-200',
          ].join(' ')}>
            <p className="font-medium">State: {data.trustedProxyAlertState}</p>
            <p className="mt-1">
              Accepted forwarded headers: {data.trustedProxyForwardedAcceptedCount} | Rejected forwarded headers: {data.trustedProxyForwardedRejectedCount}
            </p>
          </div>
          {Object.keys(data.trustedProxyRejectionReasons).length > 0 && (
            <div className="mt-3">
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Rejection reasons</h4>
              <div className="flex flex-wrap gap-2">
                {Object.entries(data.trustedProxyRejectionReasons).map(([reason, count]) => (
                  <span key={reason} className="inline-flex rounded-full border border-gray-200 px-3 py-1 text-xs text-gray-700 dark:border-gray-700 dark:text-gray-300">
                    {reason}: {count}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-950 lg:col-span-2">
          <h3 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">Operational notes</h3>
          <ul className="list-disc space-y-2 pl-5 text-sm text-gray-700 dark:text-gray-300">
            {data.notes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

export function GovernancePage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const [section, setSection] = useState<GovSection>('policies');

  return (
    <div className="flex h-full flex-col">
      <header className="flex-shrink-0 border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-800 dark:bg-gray-950">
        <h1 className="text-base font-semibold text-gray-900 dark:text-white">Governance</h1>
        <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          Policies, compliance, tenancy isolation, and audit controls for tenant <span className="font-mono font-medium">{tenantId}</span>.
        </p>
      </header>

      <div className="flex flex-shrink-0 gap-0 border-b border-gray-200 bg-white px-4 dark:border-gray-800 dark:bg-gray-950">
        {SECTIONS.map((sectionItem) => (
          <Button
            key={sectionItem.id}
            onClick={() => setSection(sectionItem.id)}
            variant="text"
            className={[
              'flex items-center gap-1.5 border-b-2 px-4 py-3 text-sm font-medium transition-colors -mb-px',
              section === sectionItem.id
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200',
            ].join(' ')}
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8} aria-hidden>
              <path strokeLinecap="round" strokeLinejoin="round" d={sectionItem.icon} />
            </svg>
            {sectionItem.label}
          </Button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-900">
        {section === 'policies' && <PoliciesPanel tenantId={tenantId} />}
        {section === 'compliance' && isFeatureEnabled('COMPLIANCE_REPORTS') ? (
          <CompliancePanel tenantId={tenantId} />
        ) : section === 'compliance' ? (
          <GovernanceBoundaryPanel
            title="Compliance reports not enabled"
            summary="Compliance reporting is disabled for this tenant or deployment profile."
            bullets={[
              'Feature-flag enablement is required before the UI can attempt to load compliance records.',
              'No placeholder compliance data is shown while the feature is disabled.',
            ]}
            locked
          />
        ) : null}
        {section === 'tenancy' && isFeatureEnabled('TENANT_MANAGEMENT') ? (
          <TenancyPanel tenantId={tenantId} />
        ) : section === 'tenancy' ? (
          <GovernanceBoundaryPanel
            title="Tenant management not enabled"
            summary="Tenant-management controls are disabled for this deployment or tenant tier."
            bullets={[
              'No editable tenancy state is rendered while the feature flag is disabled.',
              'Runtime enforcement remains on the backend regardless of UI availability.',
            ]}
            locked
          />
        ) : null}
        {section === 'audit' && isFeatureEnabled('AUDIT_LOG') ? (
          <AuditPanel tenantId={tenantId} />
        ) : section === 'audit' ? (
          <GovernanceBoundaryPanel
            title="Audit log not enabled"
            summary="Audit-log viewing is disabled for this tenant or deployment profile."
            bullets={[
              'No synthetic audit records are shown while the feature is disabled.',
              'Enable the audit-log feature and backend feed before using this tab as an operational source of truth.',
            ]}
            locked
          />
        ) : null}
        {section === 'consent' ? <ConsentPanel tenantId={tenantId} /> : null}
        {section === 'operations' ? <OperationsPanel tenantId={tenantId} /> : null}
      </div>
    </div>
  );
}
