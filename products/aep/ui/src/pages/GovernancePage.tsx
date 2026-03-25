/**
 * GovernancePage — AEP governance dashboard (policies, compliance, tenancy, audit).
 *
 * The Govern area surfaces cross-cutting controls that span pipelines, agents,
 * and learning:
 *   - Policy registry: promoted, pending-review, deprecated policies
 *   - Compliance reports: GDPR access/erasure/portability, CCPA opt-out, SOC2
 *   - Tenant management: isolation configuration, capability grants
 *   - Audit log: chronological record of significant control-plane events
 *
 * Currently shows the capability overview while data wiring is completed via
 * Phase 5 (learning governance) and Phase 6 (SLO + audit trail).
 *
 * @doc.type page
 * @doc.purpose AEP governance dashboard — policies, compliance, tenancy, audit
 * @doc.layer frontend
 */
import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import { listPolicies, type LearnedPolicy, type PolicyStatus } from '@/api/aep.api';

// ─── Types ────────────────────────────────────────────────────────────

type GovSection = 'policies' | 'compliance' | 'tenancy' | 'audit';

// ─── Policy status badge ──────────────────────────────────────────────

const POLICY_STATUS_COLORS: Record<PolicyStatus, string> = {
  PENDING_REVIEW: 'bg-amber-100  text-amber-800  dark:bg-amber-900  dark:text-amber-300',
  APPROVED:       'bg-blue-100   text-blue-800   dark:bg-blue-900   dark:text-blue-300',
  REJECTED:       'bg-red-100    text-red-800    dark:bg-red-900    dark:text-red-300',
  ACTIVE:         'bg-green-100  text-green-800  dark:bg-green-900  dark:text-green-300',
  DEPRECATED:     'bg-gray-100   text-gray-600   dark:bg-gray-800   dark:text-gray-400',
};

function PolicyStatusBadge({ status }: { status: PolicyStatus }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${POLICY_STATUS_COLORS[status]}`}>
      {status.replace('_', ' ')}
    </span>
  );
}

// ─── Section tab ──────────────────────────────────────────────────────

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

// ─── Policy table ─────────────────────────────────────────────────────

function PoliciesPanel({ tenantId }: { tenantId: string }) {
  const { data: policies = [], isLoading } = useQuery({
    queryKey: ['aep', 'policies', tenantId],
    queryFn: () => listPolicies(tenantId),
    staleTime: 30_000,
  });

  const activeCount  = policies.filter((p) => p.status === 'ACTIVE').length;
  const pendingCount = policies.filter((p) => p.status === 'PENDING_REVIEW').length;

  return (
    <div className="p-6">
      {/* KPIs */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        {[
          { label: 'Total policies',   value: policies.length },
          { label: 'Active',           value: activeCount },
          { label: 'Pending review',   value: pendingCount },
          { label: 'Deprecated',       value: policies.filter((p) => p.status === 'DEPRECATED').length },
        ].map((kpi) => (
          <div key={kpi.label} className="rounded-lg border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-4 py-3">
            <p className="text-xs text-gray-500 dark:text-gray-400">{kpi.label}</p>
            <p className="text-2xl font-semibold text-gray-900 dark:text-white mt-0.5">{kpi.value}</p>
          </div>
        ))}
      </div>

      {/* Table */}
      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-10">Loading policies…</p>
      ) : policies.length === 0 ? (
        <p className="text-sm text-gray-400 text-center py-10">No policies yet. They appear here once learning episodes are reviewed and promoted.</p>
      ) : (
        <div className="overflow-auto rounded-lg border border-gray-200 dark:border-gray-800">
          <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-800 text-sm">
            <thead className="bg-gray-50 dark:bg-gray-900">
              <tr>
                {['Policy ID', 'Skill', 'Version', 'Confidence', 'Status'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="bg-white dark:bg-gray-950 divide-y divide-gray-100 dark:divide-gray-900">
              {policies.map((p) => (
                <tr key={p.id} className="hover:bg-gray-50 dark:hover:bg-gray-900 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-gray-600 dark:text-gray-300">{p.id}</td>
                  <td className="px-4 py-3 text-gray-900 dark:text-white">{p.skillId}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-500">v{p.version}</td>
                  <td className="px-4 py-3 text-gray-900 dark:text-white">
                    <span className={`font-semibold ${p.confidenceScore >= 0.8 ? 'text-green-600 dark:text-green-400' : p.confidenceScore >= 0.6 ? 'text-amber-600 dark:text-amber-400' : 'text-red-600 dark:text-red-400'}`}>
                      {(p.confidenceScore * 100).toFixed(0)}%
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <PolicyStatusBadge status={p.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ─── Coming-soon section ──────────────────────────────────────────────

function ComingSoonSection({ title, description }: { title: string; description: string }) {
  return (
    <div className="p-6 flex flex-col items-center justify-center py-20 gap-3 text-center">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        className="h-10 w-10 text-gray-300 dark:text-gray-700"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={1.2}
        aria-hidden
      >
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
      </svg>
      <p className="text-sm font-semibold text-gray-600 dark:text-gray-400">{title}</p>
      <p className="text-xs text-gray-400 dark:text-gray-600 max-w-sm">{description}</p>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────

/**
 * Governance page: policy registry, compliance, tenancy, and audit controls.
 */
export function GovernancePage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const [section, setSection] = useState<GovSection>('policies');

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <header className="flex-shrink-0 px-6 py-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950">
        <h1 className="text-base font-semibold text-gray-900 dark:text-white">Governance</h1>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          Policies, compliance, tenancy isolation, and audit controls for tenant <span className="font-mono font-medium">{tenantId}</span>.
        </p>
      </header>

      {/* Section tabs */}
      <div className="flex-shrink-0 flex gap-0 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950 px-4">
        {SECTIONS.map((sec) => (
          <button
            key={sec.id}
            onClick={() => setSection(sec.id)}
            className={[
              'flex items-center gap-1.5 px-4 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
              section === sec.id
                ? 'border-indigo-500 text-indigo-600 dark:text-indigo-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200',
            ].join(' ')}
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8} aria-hidden>
              <path strokeLinecap="round" strokeLinejoin="round" d={sec.icon} />
            </svg>
            {sec.label}
          </button>
        ))}
      </div>

      {/* Section content */}
      <div className="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-900">
        {section === 'policies' && <PoliciesPanel tenantId={tenantId} />}
        {section === 'compliance' && (
          <ComingSoonSection
            title="Compliance reports"
            description="GDPR access/erasure/portability operations, CCPA opt-out records, and SOC2 evidence packages will appear here once the compliance service API is wired to this view."
          />
        )}
        {section === 'tenancy' && (
          <ComingSoonSection
            title="Tenant management"
            description="Per-tenant isolation settings, capability grants, rate limits, and agent-type restrictions will be configurable here in a future release."
          />
        )}
        {section === 'audit' && (
          <ComingSoonSection
            title="Audit log"
            description="A chronological, tamper-evident record of all control-plane events — policy promotions, agent registrations, compliance operations, and configuration changes — will stream here via the run ledger once Phase 6 is complete."
          />
        )}
      </div>
    </div>
  );
}
