import React from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';

interface PolicyRule {
  id: string;
  name: string;
  condition: string;
  action: 'block' | 'warn' | 'audit';
  enabled: boolean;
}

interface PolicyDetail {
  id: string;
  name: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  description: string;
  category: string;
  enforcementMode: 'enforce' | 'audit' | 'disabled';
  rules: PolicyRule[];
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  appliedTo: string[];
}

const SEVERITY_STYLES: Record<PolicyDetail['severity'], string> = {
  critical: 'bg-red-900/30 text-red-400 border-red-800',
  high: 'bg-orange-900/30 text-orange-400 border-orange-800',
  medium: 'bg-yellow-900/30 text-yellow-400 border-yellow-800',
  low: 'bg-blue-900/30 text-blue-400 border-blue-800',
};

const ENFORCEMENT_STYLES: Record<PolicyDetail['enforcementMode'], string> = {
  enforce: 'bg-green-900/30 text-green-400',
  audit: 'bg-yellow-900/30 text-yellow-400',
  disabled: 'bg-zinc-800 text-zinc-500',
};

const ACTION_STYLES: Record<PolicyRule['action'], string> = {
  block: 'text-red-400',
  warn: 'text-yellow-400',
  audit: 'text-blue-400',
};

/**
 * PolicyDetailPage — Displays security policy details including severity, description, and rules.
 *
 * @doc.type component
 * @doc.purpose Security policy detail view with rules list
 * @doc.layer product
 */
const PolicyDetailPage: React.FC = () => {
  const { policyId } = useParams<{ policyId: string }>();

  const { data: policy, isLoading, error } = useQuery<PolicyDetail>({
    queryKey: ['policy-detail', policyId],
    queryFn: async () => {
      const res = await fetch(`/api/policies/${policyId}`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
      });
      if (!res.ok) throw new Error('Failed to load policy');
      return res.json() as Promise<PolicyDetail>;
    },
    enabled: !!policyId,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          {error instanceof Error ? error.message : 'Failed to load policy'}
        </div>
      </div>
    );
  }

  const severity = policy?.severity ?? 'medium';
  const rules = policy?.rules ?? [];

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/security/policies" className="text-zinc-400 hover:text-zinc-200 transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-zinc-100">{policy?.name ?? 'Untitled Policy'}</h1>
              <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border ${SEVERITY_STYLES[severity]}`}>
                {severity.toUpperCase()}
              </span>
              <span className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${ENFORCEMENT_STYLES[policy?.enforcementMode ?? 'audit']}`}>
                {policy?.enforcementMode ?? 'audit'}
              </span>
            </div>
            <p className="text-sm text-zinc-400 mt-1">Policy ID: {policyId} &middot; Category: {policy?.category ?? 'General'}</p>
          </div>
        </div>
        <div className="flex gap-3">
          <button className="px-4 py-2 border border-zinc-700 text-zinc-300 hover:bg-zinc-800 text-sm font-medium rounded-lg transition-colors">Disable</button>
          <button className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors">Edit Policy</button>
        </div>
      </div>

      {/* Description Card */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-5">
        <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wide mb-2">Description</h2>
        <p className="text-zinc-300 text-sm leading-relaxed">{policy?.description ?? 'No description provided.'}</p>
        <div className="mt-4 flex gap-6 text-xs text-zinc-500">
          <span>Created by <span className="text-zinc-400">{policy?.createdBy ?? 'Unknown'}</span></span>
          <span>Created {policy?.createdAt ? new Date(policy.createdAt).toLocaleDateString() : '—'}</span>
          <span>Updated {policy?.updatedAt ? new Date(policy.updatedAt).toLocaleDateString() : '—'}</span>
        </div>
      </div>

      {/* Applied To */}
      {policy?.appliedTo && policy.appliedTo.length > 0 && (
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-5">
          <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wide mb-3">Applied To</h2>
          <div className="flex flex-wrap gap-2">
            {policy.appliedTo.map((target) => (
              <span key={target} className="px-3 py-1 text-xs font-medium bg-zinc-800 text-zinc-300 rounded-full border border-zinc-700">
                {target}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Rules List */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-lg">
        <div className="px-5 py-4 border-b border-zinc-800 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-300 uppercase tracking-wide">Rules ({rules.length})</h2>
          <button className="text-xs text-blue-400 hover:text-blue-300 transition-colors">+ Add Rule</button>
        </div>
        {rules.length === 0 ? (
          <div className="p-8 text-center text-zinc-500 text-sm">No rules defined for this policy.</div>
        ) : (
          <div className="divide-y divide-zinc-800">
            {rules.map((rule) => (
              <div key={rule.id} className="px-5 py-4 flex items-center justify-between hover:bg-zinc-800/50 transition-colors">
                <div className="flex items-center gap-3 min-w-0">
                  <div className={`w-2 h-2 rounded-full flex-shrink-0 ${rule.enabled ? 'bg-green-500' : 'bg-zinc-600'}`} />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-zinc-200 truncate">{rule.name}</p>
                    <p className="text-xs text-zinc-500 mt-0.5 font-mono truncate">{rule.condition}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4 flex-shrink-0">
                  <span className={`text-xs font-medium uppercase ${ACTION_STYLES[rule.action]}`}>{rule.action}</span>
                  <span className={`text-xs ${rule.enabled ? 'text-green-400' : 'text-zinc-500'}`}>
                    {rule.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default PolicyDetailPage;
