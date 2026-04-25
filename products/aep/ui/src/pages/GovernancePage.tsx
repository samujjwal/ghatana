/**
 * GovernancePage — AEP governance dashboard (policies, compliance, tenancy, audit).
 *
 * @doc.type page
 * @doc.purpose AEP governance dashboard — policies, compliance, tenancy, audit
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import {
  getGovernanceAuditSummary,
  getGovernanceComplianceSummary,
  getGovernanceTenancySummary,
  listPolicies,
  type GovernanceAuditEntry,
  type PolicyStatus,
} from '@/api/aep.api';
import { isFeatureEnabled } from '@/lib/feature-flags';
import { Button } from '@ghatana/design-system';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';
import { PageState } from '@/components/shared/PageState';
import { Link } from 'react-router';
import { getEditPipelineUrl, getRunDetailUrl, getAgentRegistryUrl } from '@/lib/routes';

type GovSection = 'policies' | 'compliance' | 'tenancy' | 'audit';

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
];

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
  const { data: policies, isLoading, isError, refetch } = useQuery({
    queryKey: ['aep', 'policies', tenantId],
    queryFn: () => listPolicies(tenantId),
    staleTime: 60_000,
  });

  const activeCount = policies?.filter((policy) => policy.status === 'ACTIVE').length;
  const pendingCount = policies?.filter((policy) => policy.status === 'PENDING_REVIEW').length;

  if (isLoading) {
    return <PageState mode="loading" title="Loading policies…" description="Fetching governance policies for this tenant." className="h-full" />;
  }
  if (isError) {
    return <PageState mode="unavailable" title="Failed to load policies" description="The policy service is not reachable." onRetry={() => void refetch()} className="h-full" />;
  }
  if (!policies || policies.length === 0) {
    return <PageState mode="empty" title="No policies registered" description="Policies will appear once they are configured for this tenant." className="h-full" />;
  }

  return (
    <div className="p-6">
      <div className="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[
          { label: 'Total policies', value: policies.length },
          { label: 'Active', value: activeCount },
          { label: 'Pending review', value: pendingCount },
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
              {['Policy ID', 'Skill', 'Version', 'Confidence', 'Status', 'Actions'].map((header) => (
                <th key={header} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white dark:divide-gray-900 dark:bg-gray-950">
            {policies.map((policy) => (
              <tr key={policy.id} className="transition-colors hover:bg-gray-50 dark:hover:bg-gray-900">
                <td className="px-4 py-3 font-mono text-xs text-gray-600 dark:text-gray-300">{policy.id}</td>
                <td className="px-4 py-3 text-gray-900 dark:text-white">{policy.skillId}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">v{policy.version}</td>
                <td className="px-4 py-3 text-gray-900 dark:text-white">
                  <span className={`font-semibold ${policy.confidenceScore >= 0.8 ? 'text-green-600 dark:text-green-400' : policy.confidenceScore >= 0.6 ? 'text-amber-600 dark:text-amber-400' : 'text-red-600 dark:text-red-400'}`}>
                    {(policy.confidenceScore * 100).toFixed(0)}%
                  </span>
                </td>
                <td className="px-4 py-3">
                  <PolicyStatusBadge status={policy.status} />
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
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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

  return (
    <div className="space-y-6 p-6">
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[
          { label: 'Configured', value: summary.configured ? 'Yes' : 'No' },
          { label: 'Operations', value: summary.supportedOperations.length },
          { label: 'Collections', value: summary.registeredCollections.length },
          { label: 'SOC2 controls', value: summary.soc2.controlCount },
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
  const { data, isLoading } = useQuery({
    queryKey: ['aep', 'governance', 'tenancy', tenantId],
    queryFn: () => getGovernanceTenancySummary(tenantId),
    staleTime: 15_000,
  });

  if (isLoading) {
    return <PageState mode="loading" title="Loading tenancy controls…" description="Fetching tenant isolation settings." className="h-full" />;
  }

  if (!data) {
    return <PageState mode="degraded" title="No tenancy data" description="Tenant isolation settings could not be loaded. The backend may be unavailable." className="h-full" />;
  }

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
      </div>
    </div>
  );
}
