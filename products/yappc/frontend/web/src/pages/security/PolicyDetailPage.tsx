import React from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { Button } from '../../components/ui/Button';

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
  critical: 'bg-destructive-bg/30 text-destructive border-destructive-border',
  high: 'bg-warning-bg/30 text-warning-color border-warning-border',
  medium: 'bg-warning-bg/30 text-warning-color border-warning-border',
  low: 'bg-info-bg/30 text-info-color border-info-border',
};

const ENFORCEMENT_STYLES: Record<PolicyDetail['enforcementMode'], string> = {
  enforce: 'bg-success-bg/30 text-success-color',
  audit: 'bg-warning-bg/30 text-warning-color',
  disabled: 'bg-surface text-fg-muted',
};

const ACTION_STYLES: Record<PolicyRule['action'], string> = {
  block: 'text-destructive',
  warn: 'text-warning-color',
  audit: 'text-info-color',
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
      if (!res.ok) {
        throw new Error(await readErrorResponse(res, 'Failed to load policy'));
      }
      return parseJsonResponse<PolicyDetail>(res, 'policy detail');
    },
    enabled: !!policyId,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-destructive-bg/20 border border-destructive-border rounded-lg p-4 text-destructive">
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
          <Link to="/security/policies" className="text-fg-muted hover:text-fg-muted transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-fg-muted">{policy?.name ?? 'Untitled Policy'}</h1>
              <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border ${SEVERITY_STYLES[severity]}`}>
                {severity.toUpperCase()}
              </span>
              <span className={`px-2.5 py-0.5 text-xs font-medium rounded-full ${ENFORCEMENT_STYLES[policy?.enforcementMode ?? 'audit']}`}>
                {policy?.enforcementMode ?? 'audit'}
              </span>
            </div>
            <p className="text-sm text-fg-muted mt-1">Policy ID: {policyId} &middot; Category: {policy?.category ?? 'General'}</p>
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="outline" className="px-4 py-2 border border-border text-fg-muted hover:bg-surface text-sm font-medium rounded-lg transition-colors">Disable</Button>
          <Button variant="solid" className="px-4 py-2 bg-primary hover:bg-info-bg text-white text-sm font-medium rounded-lg transition-colors">Edit Policy</Button>
        </div>
      </div>

      {/* Description Card */}
      <div className="bg-surface border border-border rounded-lg p-5">
        <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide mb-2">Description</h2>
        <p className="text-fg-muted text-sm leading-relaxed">{policy?.description ?? 'No description provided.'}</p>
        <div className="mt-4 flex gap-6 text-xs text-fg-muted">
          <span>Created by <span className="text-fg-muted">{policy?.createdBy ?? 'Unknown'}</span></span>
          <span>Created {policy?.createdAt ? new Date(policy.createdAt).toLocaleDateString() : '—'}</span>
          <span>Updated {policy?.updatedAt ? new Date(policy.updatedAt).toLocaleDateString() : '—'}</span>
        </div>
      </div>

      {/* Applied To */}
      {policy?.appliedTo && policy.appliedTo.length > 0 && (
        <div className="bg-surface border border-border rounded-lg p-5">
          <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide mb-3">Applied To</h2>
          <div className="flex flex-wrap gap-2">
            {policy.appliedTo.map((target) => (
              <span key={target} className="px-3 py-1 text-xs font-medium bg-surface text-fg-muted rounded-full border border-border">
                {target}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Rules List */}
      <div className="bg-surface border border-border rounded-lg">
        <div className="px-5 py-4 border-b border-border flex items-center justify-between">
          <h2 className="text-sm font-semibold text-fg-muted uppercase tracking-wide">Rules ({rules.length})</h2>
          <Button variant="link" size="small" className="text-xs text-info-color hover:text-info-color transition-colors">+ Add Rule</Button>
        </div>
        {rules.length === 0 ? (
          <div className="p-8 text-center text-fg-muted text-sm">No rules defined for this policy.</div>
        ) : (
          <div className="divide-y divide-zinc-800">
            {rules.map((rule) => (
              <div key={rule.id} className="px-5 py-4 flex items-center justify-between hover:bg-surface/50 transition-colors">
                <div className="flex items-center gap-3 min-w-0">
                  <div className={`w-2 h-2 rounded-full flex-shrink-0 ${rule.enabled ? 'bg-success-bg' : 'bg-surface-muted'}`} />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-fg-muted truncate">{rule.name}</p>
                    <p className="text-xs text-fg-muted mt-0.5 font-mono truncate">{rule.condition}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4 flex-shrink-0">
                  <span className={`text-xs font-medium uppercase ${ACTION_STYLES[rule.action]}`}>{rule.action}</span>
                  <span className={`text-xs ${rule.enabled ? 'text-success-color' : 'text-fg-muted'}`}>
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
