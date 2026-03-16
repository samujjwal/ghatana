import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { atom, useAtom } from 'jotai';

// ── Types ─────────────────────────────────────────────────────────────────────

interface TenantHealthSummary {
  tenantId: string;
  tenantName: string;
  licenseType: 'BROKER' | 'ASSET_MANAGER' | 'CUSTODIAN';
  status: 'GREEN' | 'YELLOW' | 'RED';
  apiRpsUtilPct: number;
  eventThroughputUtilPct: number;
  storageUtilPct: number;
  activeUsers: number;
  openIncidents: number;
}

interface PlatformMetrics {
  totalApiRps: number;
  k05ThroughputEventsPerSec: number;
  k8sPodsHealthy: number;
  k8sPodTotal: number;
  settlementSuccessRatePct: number;
  dlqDepth: number;
  updatedAt: string;
}

interface ActiveAlert {
  alertId: string;
  tenantId: string;
  tenantName: string;
  severity: 'P1' | 'P2' | 'P3' | 'P4';
  message: string;
  firedAt: string;
}

// ── Atoms ─────────────────────────────────────────────────────────────────────

const selectedTenantAtom = atom<string | null>(null);
const searchQueryAtom = atom('');

// ── API fetchers ──────────────────────────────────────────────────────────────

const fetchPlatformMetrics = async (): Promise<PlatformMetrics> => {
  const res = await fetch('/api/operator/platform-metrics');
  if (!res.ok) throw new Error('Failed to fetch platform metrics');
  return res.json();
};

const fetchTenantHealth = async (): Promise<TenantHealthSummary[]> => {
  const res = await fetch('/api/operator/tenant-health');
  if (!res.ok) throw new Error('Failed to fetch tenant health');
  return res.json();
};

const fetchActiveAlerts = async (): Promise<ActiveAlert[]> => {
  const res = await fetch('/api/operator/active-alerts');
  if (!res.ok) throw new Error('Failed to fetch alerts');
  return res.json();
};

// ── Sub-components ────────────────────────────────────────────────────────────

function TrafficLightBadge({ status }: { status: 'GREEN' | 'YELLOW' | 'RED' }) {
  const colors = {
    GREEN:  'bg-green-100 text-green-800',
    YELLOW: 'bg-yellow-100 text-yellow-800',
    RED:    'bg-red-100 text-red-800',
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${colors[status]}`}>
      <span className={`w-1.5 h-1.5 rounded-full mr-1 ${
        status === 'GREEN' ? 'bg-green-500' : status === 'YELLOW' ? 'bg-yellow-500' : 'bg-red-500'
      }`} />
      {status}
    </span>
  );
}

function SeverityBadge({ severity }: { severity: 'P1' | 'P2' | 'P3' | 'P4' }) {
  const colors: Record<string, string> = {
    P1: 'bg-red-600 text-white',
    P2: 'bg-orange-500 text-white',
    P3: 'bg-yellow-400 text-gray-900',
    P4: 'bg-gray-200 text-gray-700',
  };
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-bold ${colors[severity]}`}>
      {severity}
    </span>
  );
}

function MetricCard({ label, value, unit, subtext }: {
  label: string; value: string | number; unit?: string; subtext?: string;
}) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-gray-900">
        {value}<span className="text-sm font-normal text-gray-500 ml-1">{unit}</span>
      </p>
      {subtext && <p className="mt-0.5 text-xs text-gray-400">{subtext}</p>}
    </div>
  );
}

function UtilBar({ pct, label }: { pct: number; label: string }) {
  const color = pct >= 100 ? 'bg-red-500' : pct >= 80 ? 'bg-yellow-400' : 'bg-emerald-500';
  return (
    <div className="mt-1">
      <div className="flex justify-between text-xs text-gray-500 mb-0.5">
        <span>{label}</span><span>{pct}%</span>
      </div>
      <div className="w-full bg-gray-100 rounded-full h-1.5">
        <div className={`h-1.5 rounded-full ${color}`} style={{ width: `${Math.min(pct, 100)}%` }} />
      </div>
    </div>
  );
}

function TenantDetailDrawer({
  tenantId, onClose,
}: { tenantId: string; onClose: () => void }) {
  const { data } = useQuery<TenantHealthSummary[]>({
    queryKey: ['tenantHealth'],
    queryFn: fetchTenantHealth,
  });
  const tenant = data?.find(t => t.tenantId === tenantId);
  if (!tenant) return null;

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-white shadow-xl border-l border-gray-200 z-30 overflow-y-auto">
      <div className="flex items-center justify-between p-4 border-b border-gray-200">
        <h3 className="font-semibold text-gray-900">{tenant.tenantName}</h3>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
      <div className="p-4 space-y-4">
        <div className="flex items-center gap-3">
          <TrafficLightBadge status={tenant.status} />
          <span className="text-sm text-gray-500">{tenant.licenseType}</span>
        </div>
        <div className="space-y-2">
          <UtilBar pct={tenant.apiRpsUtilPct} label="API RPS" />
          <UtilBar pct={tenant.eventThroughputUtilPct} label="Event Throughput" />
          <UtilBar pct={tenant.storageUtilPct} label="Storage" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-gray-50 rounded-lg p-3 text-center">
            <p className="text-2xl font-bold text-gray-900">{tenant.activeUsers}</p>
            <p className="text-xs text-gray-500">Active Users</p>
          </div>
          <div className={`rounded-lg p-3 text-center ${tenant.openIncidents > 0 ? 'bg-red-50' : 'bg-gray-50'}`}>
            <p className={`text-2xl font-bold ${tenant.openIncidents > 0 ? 'text-red-600' : 'text-gray-900'}`}>
              {tenant.openIncidents}
            </p>
            <p className="text-xs text-gray-500">Open Incidents</p>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function OperatorHealthDashboardPage() {
  const [selectedTenant, setSelectedTenant] = useAtom(selectedTenantAtom);
  const [searchQuery, setSearchQuery] = useAtom(searchQueryAtom);

  const metricsQuery = useQuery<PlatformMetrics>({
    queryKey: ['platformMetrics'],
    queryFn: fetchPlatformMetrics,
    refetchInterval: 30_000,
    placeholderData: (prev) => prev,
  });

  const healthQuery = useQuery<TenantHealthSummary[]>({
    queryKey: ['tenantHealth'],
    queryFn: fetchTenantHealth,
    refetchInterval: 30_000,
    placeholderData: (prev) => prev,
  });

  const alertsQuery = useQuery<ActiveAlert[]>({
    queryKey: ['activeAlerts'],
    queryFn: fetchActiveAlerts,
    refetchInterval: 30_000,
    placeholderData: (prev) => prev,
  });

  const m = metricsQuery.data;
  const tenants = healthQuery.data ?? [];
  const alerts = alertsQuery.data ?? [];

  const filteredTenants = tenants.filter(t =>
    t.tenantName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    t.tenantId.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const podHealthPct = m ? Math.round((m.k8sPodsHealthy / Math.max(m.k8sPodTotal, 1)) * 100) : 0;

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold text-gray-900">Platform Health Dashboard</h1>
            <p className="text-sm text-gray-500">Cross-tenant operational view · refreshes every 30 s</p>
          </div>
          <div className="flex items-center gap-3">
            {alerts.filter(a => a.severity === 'P1' || a.severity === 'P2').length > 0 && (
              <span className="flex items-center gap-1 bg-red-100 text-red-700 text-sm px-3 py-1 rounded-full font-medium">
                <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                </svg>
                {alerts.filter(a => a.severity === 'P1' || a.severity === 'P2').length} Critical
              </span>
            )}
            <span className="text-xs text-gray-400">
              {m ? `Updated ${new Date(m.updatedAt).toLocaleTimeString()}` : 'Loading…'}
            </span>
          </div>
        </div>
      </div>

      <div className="px-6 py-6 space-y-6 max-w-7xl mx-auto">
        {/* Platform metrics grid */}
        <div>
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Platform Metrics</h2>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            <MetricCard label="Total API RPS" value={m?.totalApiRps ?? '—'} unit="req/s" />
            <MetricCard label="Event Throughput" value={m?.k05ThroughputEventsPerSec ?? '—'} unit="evt/s" />
            <MetricCard
              label="K8s Pod Health"
              value={`${m?.k8sPodsHealthy ?? '—'}/${m?.k8sPodTotal ?? '—'}`}
              subtext={`${podHealthPct}% healthy`}
            />
            <MetricCard label="Settlement Success" value={m ? `${m.settlementSuccessRatePct}%` : '—'} />
            <MetricCard
              label="DLQ Depth"
              value={m?.dlqDepth ?? '—'}
              unit="msgs"
              subtext={m && m.dlqDepth > 100 ? '⚠ High' : undefined}
            />
            <MetricCard
              label="Active Alerts"
              value={alerts.length}
              subtext={`P1/P2: ${alerts.filter(a => a.severity === 'P1' || a.severity === 'P2').length}`}
            />
          </div>
        </div>

        {/* Active alerts */}
        {alerts.length > 0 && (
          <div>
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">Alert Center</h2>
            <div className="bg-white border border-gray-200 rounded-lg divide-y divide-gray-100">
              {alerts.map(alert => (
                <div key={alert.alertId} className="flex items-center gap-3 px-4 py-3">
                  <SeverityBadge severity={alert.severity} />
                  <span className="text-sm font-medium text-gray-800 flex-1">{alert.message}</span>
                  <span className="text-xs text-gray-400 whitespace-nowrap">{alert.tenantName}</span>
                  <span className="text-xs text-gray-400 whitespace-nowrap">
                    {new Date(alert.firedAt).toLocaleTimeString()}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Tenant health grid */}
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Tenant Health</h2>
            <div className="relative">
              <svg className="absolute left-2.5 top-2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
              </svg>
              <input
                type="text"
                placeholder="Search tenants…"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="pl-8 pr-3 py-1.5 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 w-56"
              />
            </div>
          </div>
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50">
                  {['Tenant', 'Type', 'Status', 'API RPS %', 'Event %', 'Storage %', 'Users', 'Incidents'].map(h => (
                    <th key={h} className="px-4 py-2.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {filteredTenants.map(t => (
                  <tr
                    key={t.tenantId}
                    onClick={() => setSelectedTenant(t.tenantId)}
                    className="hover:bg-blue-50 cursor-pointer transition-colors"
                  >
                    <td className="px-4 py-3 font-medium text-gray-900">{t.tenantName}</td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{t.licenseType}</td>
                    <td className="px-4 py-3"><TrafficLightBadge status={t.status} /></td>
                    <td className="px-4 py-3">
                      <span className={t.apiRpsUtilPct >= 80 ? 'text-red-600 font-semibold' : 'text-gray-700'}>
                        {t.apiRpsUtilPct}%
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={t.eventThroughputUtilPct >= 80 ? 'text-red-600 font-semibold' : 'text-gray-700'}>
                        {t.eventThroughputUtilPct}%
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={t.storageUtilPct >= 80 ? 'text-red-600 font-semibold' : 'text-gray-700'}>
                        {t.storageUtilPct}%
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-700">{t.activeUsers}</td>
                    <td className="px-4 py-3">
                      {t.openIncidents > 0
                        ? <span className="font-semibold text-red-600">{t.openIncidents}</span>
                        : <span className="text-gray-400">—</span>}
                    </td>
                  </tr>
                ))}
                {filteredTenants.length === 0 && (
                  <tr><td colSpan={8} className="text-center py-8 text-gray-400">No tenants match the search</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Tenant detail right-drawer */}
      {selectedTenant && (
        <TenantDetailDrawer tenantId={selectedTenant} onClose={() => setSelectedTenant(null)} />
      )}
    </div>
  );
}
