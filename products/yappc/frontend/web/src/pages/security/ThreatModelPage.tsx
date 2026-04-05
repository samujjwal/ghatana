import React from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';

type RiskLevel = 'critical' | 'high' | 'medium' | 'low';
type MitigationStatus = 'implemented' | 'in-progress' | 'planned' | 'not-started';

interface ThreatMitigation {
  id: string;
  title: string;
  status: MitigationStatus;
  owner: string;
}

interface Threat {
  id: string;
  title: string;
  description: string;
  category: string;
  riskScore: number;
  riskLevel: RiskLevel;
  likelihood: number;
  impact: number;
  attackVector: string;
  mitigations: ThreatMitigation[];
}

interface ThreatModel {
  id: string;
  name: string;
  description: string;
  projectId: string;
  updatedAt: string;
  threats: Threat[];
}

const RISK_STYLES: Record<RiskLevel, { badge: string; bar: string }> = {
  critical: { badge: 'bg-red-900/30 text-red-400 border-red-800', bar: 'bg-red-500' },
  high: { badge: 'bg-orange-900/30 text-orange-400 border-orange-800', bar: 'bg-orange-500' },
  medium: { badge: 'bg-yellow-900/30 text-yellow-400 border-yellow-800', bar: 'bg-yellow-500' },
  low: { badge: 'bg-blue-900/30 text-blue-400 border-blue-800', bar: 'bg-blue-500' },
};

const MITIGATION_STYLES: Record<MitigationStatus, string> = {
  implemented: 'text-green-400',
  'in-progress': 'text-yellow-400',
  planned: 'text-blue-400',
  'not-started': 'text-zinc-500',
};

const MITIGATION_ICONS: Record<MitigationStatus, string> = {
  implemented: '\u2713',
  'in-progress': '\u25CB',
  planned: '\u2022',
  'not-started': '\u2014',
};

/**
 * ThreatModelPage — Displays threat cards with risk scores and mitigations.
 *
 * @doc.type component
 * @doc.purpose Threat modeling view with risk assessment and mitigation tracking
 * @doc.layer product
 */
const ThreatModelPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  const { data: model, isLoading, error } = useQuery<ThreatModel>({
    queryKey: ['threat-model', projectId],
    queryFn: async () => {
      const res = await fetch(`/api/threat-models/${projectId}`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
      });
      if (!res.ok) throw new Error('Failed to load threat model');
      return res.json() as Promise<ThreatModel>;
    },
    enabled: !!projectId,
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
          {error instanceof Error ? error.message : 'Failed to load threat model'}
        </div>
      </div>
    );
  }

  const threats = model?.threats ?? [];
  const riskCounts: Record<RiskLevel, number> = { critical: 0, high: 0, medium: 0, low: 0 };
  for (const t of threats) {
    riskCounts[t.riskLevel]++;
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/security/threat-models" className="text-zinc-400 hover:text-zinc-200 transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-zinc-100">{model?.name ?? 'Threat Model'}</h1>
            <p className="text-sm text-zinc-400 mt-1">
              {model?.description ?? ''} &middot; Updated {model?.updatedAt ? new Date(model.updatedAt).toLocaleDateString() : '—'}
            </p>
          </div>
        </div>
        <div className="flex gap-3">
          <button className="px-4 py-2 border border-zinc-700 text-zinc-300 hover:bg-zinc-800 text-sm font-medium rounded-lg transition-colors">Export</button>
          <button className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors">Add Threat</button>
        </div>
      </div>

      {/* Risk Summary */}
      <div className="grid grid-cols-4 gap-4">
        {(['critical', 'high', 'medium', 'low'] as const).map((level) => (
          <div key={level} className={`rounded-lg border p-4 text-center ${RISK_STYLES[level].badge}`}>
            <p className="text-3xl font-bold">{riskCounts[level]}</p>
            <p className="text-xs uppercase tracking-wide mt-1 opacity-75">{level} Risk</p>
          </div>
        ))}
      </div>

      {/* Threat Cards */}
      {threats.length === 0 ? (
        <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-12 text-center">
          <p className="text-zinc-500">No threats identified yet.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {threats.map((threat) => {
            const style = RISK_STYLES[threat.riskLevel];
            const mitigatedCount = threat.mitigations.filter((m) => m.status === 'implemented').length;
            return (
              <div key={threat.id} className="bg-zinc-900 border border-zinc-800 rounded-lg overflow-hidden">
                {/* Threat Header */}
                <div className="px-5 py-4 flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-3 mb-1">
                      <h3 className="text-base font-semibold text-zinc-100 truncate">{threat.title}</h3>
                      <span className={`flex-shrink-0 px-2 py-0.5 text-xs font-semibold rounded-full border ${style.badge}`}>
                        {threat.riskLevel.toUpperCase()}
                      </span>
                    </div>
                    <p className="text-sm text-zinc-400 line-clamp-2">{threat.description}</p>
                    <div className="flex items-center gap-4 mt-2 text-xs text-zinc-500">
                      <span>Category: <span className="text-zinc-400">{threat.category}</span></span>
                      <span>Attack Vector: <span className="text-zinc-400">{threat.attackVector}</span></span>
                    </div>
                  </div>

                  {/* Risk Score Gauge */}
                  <div className="flex-shrink-0 w-20 text-center">
                    <div className="relative w-14 h-14 mx-auto">
                      <svg viewBox="0 0 36 36" className="w-full h-full">
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="#3f3f46" strokeWidth="3" />
                        <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" className={style.bar.replace('bg-', 'stroke-').replace('stroke-', 'stroke-current ')} strokeWidth="3" strokeDasharray={`${threat.riskScore}, 100`} />
                      </svg>
                      <span className="absolute inset-0 flex items-center justify-center text-sm font-bold text-zinc-200">{threat.riskScore}</span>
                    </div>
                    <p className="text-[10px] text-zinc-500 mt-1 uppercase">Risk Score</p>
                  </div>
                </div>

                {/* Likelihood / Impact bar */}
                <div className="px-5 pb-2 flex gap-6">
                  <div className="flex-1">
                    <div className="flex justify-between text-xs text-zinc-500 mb-1">
                      <span>Likelihood</span>
                      <span>{threat.likelihood}/10</span>
                    </div>
                    <div className="h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                      <div className={`h-full rounded-full ${style.bar}`} style={{ width: `${threat.likelihood * 10}%` }} />
                    </div>
                  </div>
                  <div className="flex-1">
                    <div className="flex justify-between text-xs text-zinc-500 mb-1">
                      <span>Impact</span>
                      <span>{threat.impact}/10</span>
                    </div>
                    <div className="h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                      <div className={`h-full rounded-full ${style.bar}`} style={{ width: `${threat.impact * 10}%` }} />
                    </div>
                  </div>
                </div>

                {/* Mitigations */}
                {threat.mitigations.length > 0 && (
                  <div className="border-t border-zinc-800 px-5 py-3">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-medium text-zinc-400 uppercase tracking-wide">Mitigations</span>
                      <span className="text-xs text-zinc-500">{mitigatedCount}/{threat.mitigations.length} implemented</span>
                    </div>
                    <div className="space-y-1.5">
                      {threat.mitigations.map((m) => (
                        <div key={m.id} className="flex items-center justify-between text-sm">
                          <div className="flex items-center gap-2 min-w-0">
                            <span className={`flex-shrink-0 text-xs ${MITIGATION_STYLES[m.status]}`}>{MITIGATION_ICONS[m.status]}</span>
                            <span className="text-zinc-300 truncate">{m.title}</span>
                          </div>
                          <span className="text-xs text-zinc-500 flex-shrink-0 ml-3">{m.owner}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default ThreatModelPage;
