import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { atom, useAtom } from 'jotai';

// ── Types ─────────────────────────────────────────────────────────────────────

type PluginTier = 'T1' | 'T2' | 'T3';
type CertStatus = 'VALID' | 'REVOKED' | 'EXPIRED' | 'UNCERTIFIED';

interface Plugin {
  pluginId: string;
  name: string;
  description: string;
  author: string;
  tier: PluginTier;
  domain: string;   // e.g. RISK | REPORTING | EXECUTION | COMPLIANCE | REFERENCE_DATA
  rating: number;   // 0-5
  installCount: number;
  certStatus: CertStatus;
  certExpiresAt?: string;
  latestVersion: string;
  licenseRequired?: string;  // feature gate e.g. ALGO_TRADING
}

interface PluginReview {
  reviewId: string;
  authorName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

// ── Atoms ─────────────────────────────────────────────────────────────────────

const tierFilterAtom      = atom<PluginTier | 'ALL'>('ALL');
const domainFilterAtom    = atom<string>('ALL');
const selectedPluginAtom  = atom<string | null>(null);
const searchQueryAtom     = atom('');

// ── API fetchers ──────────────────────────────────────────────────────────────

const fetchPlugins = async (): Promise<Plugin[]> => {
  const res = await fetch('/api/marketplace/plugins');
  if (!res.ok) throw new Error('Failed to load plugins');
  return res.json();
};

const fetchPluginReviews = async (pluginId: string): Promise<PluginReview[]> => {
  const res = await fetch(`/api/marketplace/plugins/${pluginId}/reviews`);
  if (!res.ok) throw new Error('Failed to load reviews');
  return res.json();
};

const initiateInstall = async (pluginId: string) => {
  const res = await fetch(`/api/marketplace/plugins/${pluginId}/install`, { method: 'POST' });
  if (!res.ok) throw new Error('Install failed');
  return res.json();
};

// ── Sub-components ────────────────────────────────────────────────────────────

function TierBadge({ tier }: { tier: PluginTier }) {
  const styles: Record<PluginTier, string> = {
    T1: 'bg-green-100 text-green-800',
    T2: 'bg-blue-100 text-blue-800',
    T3: 'bg-purple-100 text-purple-800',
  };
  const labels: Record<PluginTier, string> = {
    T1: 'T1 · Minimal',
    T2: 'T2 · Sandboxed',
    T3: 'T3 · Configurable',
  };
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${styles[tier]}`}>
      {labels[tier]}
    </span>
  );
}

function CertBadge({ status }: { status: CertStatus }) {
  const styles: Record<CertStatus, string> = {
    VALID:       'bg-green-100 text-green-700',
    EXPIRED:     'bg-yellow-100 text-yellow-700',
    REVOKED:     'bg-red-100 text-red-700',
    UNCERTIFIED: 'bg-gray-100 text-gray-500',
  };
  return (
    <span className={`text-xs px-2 py-0.5 rounded ${styles[status]}`}>
      {status}
    </span>
  );
}

function StarRating({ rating }: { rating: number }) {
  return (
    <span className="flex items-center gap-0.5">
      {Array.from({ length: 5 }, (_, i) => (
        <svg key={i} className={`w-3.5 h-3.5 ${i < Math.round(rating) ? 'text-yellow-400' : 'text-gray-200'}`}
          fill="currentColor" viewBox="0 0 20 20">
          <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
        </svg>
      ))}
      <span className="text-xs text-gray-500 ml-1">{rating.toFixed(1)}</span>
    </span>
  );
}

function PluginCard({ plugin, onClick }: { plugin: Plugin; onClick: () => void }) {
  return (
    <div
      onClick={onClick}
      className="bg-white border border-gray-200 rounded-xl p-5 hover:border-blue-300 hover:shadow-md cursor-pointer transition-all"
    >
      <div className="flex items-start justify-between mb-3">
        <div>
          <h3 className="font-semibold text-gray-900">{plugin.name}</h3>
          <p className="text-xs text-gray-400 mt-0.5">by {plugin.author}</p>
        </div>
        <TierBadge tier={plugin.tier} />
      </div>
      <p className="text-sm text-gray-600 line-clamp-2 mb-3">{plugin.description}</p>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <StarRating rating={plugin.rating} />
          <span className="text-xs text-gray-400">·</span>
          <span className="text-xs text-gray-400">{plugin.installCount.toLocaleString()} installs</span>
        </div>
        <CertBadge status={plugin.certStatus} />
      </div>
      {plugin.licenseRequired && (
        <div className="mt-2 text-xs text-orange-600 bg-orange-50 rounded px-2 py-1">
          Requires: {plugin.licenseRequired}
        </div>
      )}
    </div>
  );
}

function PluginDetailDrawer({ pluginId, onClose }: { pluginId: string; onClose: () => void }) {
  const { data: plugins } = useQuery<Plugin[]>({ queryKey: ['plugins'], queryFn: fetchPlugins });
  const { data: reviews } = useQuery<PluginReview[]>({
    queryKey: ['reviews', pluginId],
    queryFn: () => fetchPluginReviews(pluginId),
  });
  const installMutation = useMutation({ mutationFn: () => initiateInstall(pluginId) });

  const plugin = plugins?.find(p => p.pluginId === pluginId);
  if (!plugin) return null;

  const certDaysLeft = plugin.certExpiresAt
    ? Math.ceil((new Date(plugin.certExpiresAt).getTime() - Date.now()) / 86_400_000)
    : null;

  return (
    <div className="fixed inset-y-0 right-0 w-[460px] bg-white shadow-xl border-l border-gray-200 z-30 flex flex-col">
      {/* Header */}
      <div className="flex items-start justify-between p-5 border-b border-gray-200">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <TierBadge tier={plugin.tier} />
            <CertBadge status={plugin.certStatus} />
          </div>
          <h2 className="text-lg font-semibold text-gray-900">{plugin.name}</h2>
          <p className="text-sm text-gray-400">by {plugin.author} · v{plugin.latestVersion}</p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-5 space-y-6">
        <p className="text-sm text-gray-700">{plugin.description}</p>

        {/* Stats */}
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-gray-50 rounded-lg p-3 text-center">
            <p className="text-lg font-bold text-gray-900">{plugin.rating.toFixed(1)}</p>
            <p className="text-xs text-gray-400">Rating</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-3 text-center">
            <p className="text-lg font-bold text-gray-900">{plugin.installCount.toLocaleString()}</p>
            <p className="text-xs text-gray-400">Installs</p>
          </div>
          <div className="bg-gray-50 rounded-lg p-3 text-center">
            <p className={`text-lg font-bold ${certDaysLeft !== null && certDaysLeft < 30 ? 'text-orange-500' : 'text-gray-900'}`}>
              {certDaysLeft !== null ? `${certDaysLeft}d` : '—'}
            </p>
            <p className="text-xs text-gray-400">Cert expires</p>
          </div>
        </div>

        {/* Cert info */}
        <div className="bg-blue-50 rounded-lg p-4 text-sm text-blue-800">
          <p className="font-semibold mb-1">Certificate Details</p>
          <p>Status: <span className="font-medium">{plugin.certStatus}</span></p>
          {plugin.certExpiresAt && (
            <p>Expires: {new Date(plugin.certExpiresAt).toLocaleDateString()}</p>
          )}
          <p>Tier policy: <span className="font-medium">{plugin.tier}</span></p>
        </div>

        {/* Reviews */}
        <div>
          <p className="text-sm font-semibold text-gray-700 mb-3">
            Reviews ({reviews?.length ?? 0})
          </p>
          {(reviews ?? []).map(r => (
            <div key={r.reviewId} className="border-b border-gray-100 pb-3 mb-3 last:border-0 last:mb-0">
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm font-medium text-gray-800">{r.authorName}</span>
                <StarRating rating={r.rating} />
              </div>
              <p className="text-sm text-gray-600">{r.comment}</p>
              <p className="text-xs text-gray-400 mt-0.5">{new Date(r.createdAt).toLocaleDateString()}</p>
            </div>
          ))}
          {(reviews?.length ?? 0) === 0 && (
            <p className="text-sm text-gray-400">No reviews yet</p>
          )}
        </div>
      </div>

      {/* Install CTA */}
      <div className="border-t border-gray-200 p-4 bg-white">
        {plugin.licenseRequired && (
          <p className="text-xs text-orange-600 mb-2">
            Requires <strong>{plugin.licenseRequired}</strong> feature license
          </p>
        )}
        <button
          onClick={() => installMutation.mutate()}
          disabled={plugin.certStatus !== 'VALID' || installMutation.isPending}
          className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-gray-200 disabled:text-gray-400 text-white font-medium py-2.5 rounded-lg text-sm transition-colors"
        >
          {installMutation.isPending ? 'Starting install…' :
           installMutation.isSuccess ? 'Install started ✓' :
           plugin.certStatus !== 'VALID' ? 'Certificate invalid — cannot install' :
           'Install Plugin'}
        </button>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

const DOMAINS = ['ALL', 'RISK', 'REPORTING', 'EXECUTION', 'COMPLIANCE', 'REFERENCE_DATA'];
const TIERS: Array<PluginTier | 'ALL'> = ['ALL', 'T1', 'T2', 'T3'];

export default function PluginMarketplacePage() {
  const [tierFilter, setTierFilter]     = useAtom(tierFilterAtom);
  const [domainFilter, setDomainFilter] = useAtom(domainFilterAtom);
  const [selectedPlugin, setSelectedPlugin] = useAtom(selectedPluginAtom);
  const [searchQuery, setSearchQuery]   = useAtom(searchQueryAtom);

  const { data: plugins = [], isLoading } = useQuery<Plugin[]>({
    queryKey: ['plugins'],
    queryFn: fetchPlugins,
    placeholderData: (prev) => prev,
  });

  const filtered = plugins.filter(p =>
    (tierFilter   === 'ALL' || p.tier === tierFilter) &&
    (domainFilter === 'ALL' || p.domain === domainFilter) &&
    (searchQuery === '' ||
      p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.author.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.description.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between max-w-7xl mx-auto">
          <div>
            <h1 className="text-xl font-semibold text-gray-900">Plugin Marketplace</h1>
            <p className="text-sm text-gray-500">{plugins.length} certified plugins available</p>
          </div>
          <div className="relative">
            <svg className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
            </svg>
            <input
              type="text"
              placeholder="Search plugins…"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="pl-10 pr-4 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 w-64"
            />
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white border-b border-gray-100 px-6 py-3 max-w-7xl mx-auto flex items-center gap-6">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-gray-500">Tier:</span>
          {TIERS.map(t => (
            <button key={t} onClick={() => setTierFilter(t)}
              className={`text-xs px-2.5 py-1 rounded-full border ${
                tierFilter === t ? 'bg-blue-600 text-white border-blue-600' : 'border-gray-200 text-gray-600 hover:border-gray-300'
              }`}>
              {t}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-gray-500">Domain:</span>
          {DOMAINS.map(d => (
            <button key={d} onClick={() => setDomainFilter(d)}
              className={`text-xs px-2.5 py-1 rounded-full border ${
                domainFilter === d ? 'bg-blue-600 text-white border-blue-600' : 'border-gray-200 text-gray-600 hover:border-gray-300'
              }`}>
              {d === 'ALL' ? 'All' : d.replace('_', ' ')}
            </button>
          ))}
        </div>
      </div>

      {/* Plugin grid */}
      <div className="px-6 py-6 max-w-7xl mx-auto">
        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="bg-white border border-gray-200 rounded-xl p-5 animate-pulse">
                <div className="h-4 bg-gray-100 rounded w-2/3 mb-3" />
                <div className="h-3 bg-gray-100 rounded w-full mb-2" />
                <div className="h-3 bg-gray-100 rounded w-3/4" />
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <p className="text-lg">No plugins match the current filters</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {filtered.map(plugin => (
              <PluginCard
                key={plugin.pluginId}
                plugin={plugin}
                onClick={() => setSelectedPlugin(plugin.pluginId)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Plugin detail drawer */}
      {selectedPlugin && (
        <PluginDetailDrawer
          pluginId={selectedPlugin}
          onClose={() => setSelectedPlugin(null)}
        />
      )}
    </div>
  );
}
