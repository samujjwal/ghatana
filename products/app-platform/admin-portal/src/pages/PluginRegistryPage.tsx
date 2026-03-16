import React, { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type PluginStatus = "ACTIVE" | "SUSPENDED" | "PENDING_REVIEW" | "REJECTED" | "DEPRECATED";
type PluginTier = "T1" | "T2" | "T3";

interface Plugin {
  pluginId: string;
  name: string;
  description: string;
  version: string;
  tier: PluginTier;
  status: PluginStatus;
  author: string;
  serviceId: string;
  sandboxConfig: Record<string, string | number | boolean>;
  securityReviewedAt: string | null;
  complianceApprovedAt: string | null;
  installCount: number;
  lastHealthCheck: "HEALTHY" | "DEGRADED" | "UNREACHABLE" | null;
  publishedAt: string;
  updatedAt: string;
}

interface PluginListResponse {
  plugins: Plugin[];
  total: number;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const API = "/api/admin/plugins";

async function fetchPlugins(tier: string, status: string, search: string): Promise<PluginListResponse> {
  const params = new URLSearchParams();
  if (tier)   params.set("tier", tier);
  if (status) params.set("status", status);
  if (search) params.set("search", search);
  const res = await fetch(`${API}?${params}`);
  if (!res.ok) throw new Error("Failed to load plugins");
  return res.json();
}

async function suspendPlugin(pluginId: string, reason: string): Promise<void> {
  const res = await fetch(`${API}/${pluginId}/suspend`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error("Failed to suspend plugin");
}

async function activatePlugin(pluginId: string): Promise<void> {
  const res = await fetch(`${API}/${pluginId}/activate`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to activate plugin");
}

async function bulkSuspendPlugins(pluginIds: string[], reason: string): Promise<void> {
  const res = await fetch(`${API}/bulk-suspend`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ pluginIds, reason }),
  });
  if (!res.ok) throw new Error("Bulk suspend failed");
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const STATUS_STYLES: Record<PluginStatus, string> = {
  ACTIVE:          "bg-green-100  text-green-700",
  SUSPENDED:       "bg-red-100    text-red-700",
  PENDING_REVIEW:  "bg-yellow-100 text-yellow-700",
  REJECTED:        "bg-gray-100   text-gray-500",
  DEPRECATED:      "bg-gray-100   text-gray-400",
};

const TIER_COLORS: Record<PluginTier, string> = {
  T1: "bg-blue-50  text-blue-700  border-blue-200",
  T2: "bg-teal-50  text-teal-700  border-teal-200",
  T3: "bg-purple-50 text-purple-700 border-purple-200",
};

const HEALTH_DOT: Record<string, string> = {
  HEALTHY:     "bg-green-500",
  DEGRADED:    "bg-yellow-500",
  UNREACHABLE: "bg-red-500",
};

// ---------------------------------------------------------------------------
// Plugin detail drawer
// ---------------------------------------------------------------------------

function PluginDrawer({ plugin, onClose }: { plugin: Plugin; onClose: () => void }) {
  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-white shadow-2xl border-l border-gray-200 flex flex-col z-40">
      <div className="px-5 py-4 border-b flex justify-between items-start">
        <div>
          <h2 className="font-semibold text-gray-800">{plugin.name}</h2>
          <p className="text-xs text-gray-400 font-mono">{plugin.pluginId}</p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
      </div>
      <div className="flex-1 overflow-auto p-5 space-y-4">
        <div className="flex gap-2">
          <span className={`text-xs font-bold px-2 py-0.5 rounded-full border ${TIER_COLORS[plugin.tier]}`}>
            {plugin.tier}
          </span>
          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${STATUS_STYLES[plugin.status]}`}>
            {plugin.status.replace("_", " ")}
          </span>
        </div>
        <p className="text-sm text-gray-600">{plugin.description}</p>

        <dl className="grid grid-cols-2 gap-y-2 text-sm">
          <dt className="text-gray-500">Version</dt>   <dd className="font-mono text-xs">{plugin.version}</dd>
          <dt className="text-gray-500">Author</dt>    <dd>{plugin.author}</dd>
          <dt className="text-gray-500">Service ID</dt><dd className="font-mono text-xs">{plugin.serviceId}</dd>
          <dt className="text-gray-500">Installs</dt>  <dd>{plugin.installCount.toLocaleString()}</dd>
          <dt className="text-gray-500">Health</dt>
          <dd className="flex items-center gap-1.5">
            {plugin.lastHealthCheck ? (
              <><div className={`h-2 w-2 rounded-full ${HEALTH_DOT[plugin.lastHealthCheck]}`} />{plugin.lastHealthCheck}</>
            ) : "—"}
          </dd>
          <dt className="text-gray-500">Security Review</dt>
          <dd className="text-xs">{plugin.securityReviewedAt ? new Date(plugin.securityReviewedAt).toLocaleDateString() : <span className="text-red-500">Not reviewed</span>}</dd>
          <dt className="text-gray-500">Compliance</dt>
          <dd className="text-xs">{plugin.complianceApprovedAt ? new Date(plugin.complianceApprovedAt).toLocaleDateString() : <span className="text-red-500">Pending</span>}</dd>
          <dt className="text-gray-500">Published</dt> <dd className="text-xs">{new Date(plugin.publishedAt).toLocaleDateString()}</dd>
          <dt className="text-gray-500">Updated</dt>   <dd className="text-xs">{new Date(plugin.updatedAt).toLocaleDateString()}</dd>
        </dl>

        {Object.keys(plugin.sandboxConfig).length > 0 && (
          <div>
            <h3 className="text-sm font-medium text-gray-700 mb-1.5">Sandbox Config</h3>
            <div className="rounded-lg bg-gray-50 p-3 font-mono text-xs space-y-1">
              {Object.entries(plugin.sandboxConfig).map(([k, v]) => (
                <div key={k} className="flex justify-between">
                  <span className="text-gray-500">{k}</span>
                  <span className="text-gray-800">{String(v)}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function PluginRegistryPage() {
  const queryClient = useQueryClient();
  const [tierFilter, setTierFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [detailPlugin, setDetailPlugin] = useState<Plugin | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ["plugins", tierFilter, statusFilter, search],
    queryFn: () => fetchPlugins(tierFilter, statusFilter, search),
  });

  const suspendMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => suspendPlugin(id, reason),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plugins"] }),
  });

  const activateMutation = useMutation({
    mutationFn: activatePlugin,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plugins"] }),
  });

  const bulkSuspendMutation = useMutation({
    mutationFn: ({ ids, reason }: { ids: string[]; reason: string }) => bulkSuspendPlugins(ids, reason),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["plugins"] }); setSelected(new Set()); },
  });

  function toggleSelect(id: string) {
    setSelected((prev) => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s; });
  }

  function toggleAll() {
    if (!data) return;
    setSelected(selected.size === data.plugins.length ? new Set() : new Set(data.plugins.map((p) => p.pluginId)));
  }

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex items-center gap-3 flex-wrap">
        <input type="search" placeholder="Search plugins…" value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm w-52 focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <select value={tierFilter} onChange={(e) => setTierFilter(e.target.value)}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm">
          <option value="">All tiers</option>
          <option value="T1">T1</option>
          <option value="T2">T2</option>
          <option value="T3">T3</option>
        </select>
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm">
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="PENDING_REVIEW">Pending Review</option>
        </select>
        {selected.size > 0 && (
          <button onClick={() => {
            const reason = window.prompt("Suspension reason:");
            if (reason) bulkSuspendMutation.mutate({ ids: [...selected], reason });
          }} className="px-3 py-1.5 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700">
            Suspend {selected.size} selected
          </button>
        )}
        <span className="ml-auto text-xs text-gray-400">{data?.total ?? 0} plugins</span>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="px-4 py-2.5">
                <input type="checkbox" className="accent-blue-600" onChange={toggleAll}
                  checked={data ? selected.size === data.plugins.length && data.plugins.length > 0 : false} />
              </th>
              {["Plugin", "Tier", "Version", "Status", "Health", "Installs", "Actions"].map((h) => (
                <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading
              ? <tr><td colSpan={8} className="text-center py-8 text-gray-400">Loading…</td></tr>
              : (data?.plugins ?? []).map((plugin) => (
                <tr key={plugin.pluginId} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <input type="checkbox" className="accent-blue-600" checked={selected.has(plugin.pluginId)}
                      onChange={() => toggleSelect(plugin.pluginId)} />
                  </td>
                  <td className="px-4 py-3">
                    <button onClick={() => setDetailPlugin(plugin)} className="text-left">
                      <p className="font-medium text-blue-600 hover:underline">{plugin.name}</p>
                      <p className="text-xs text-gray-400 truncate max-w-[180px]">{plugin.author}</p>
                    </button>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-bold px-2 py-0.5 rounded-full border ${TIER_COLORS[plugin.tier]}`}>
                      {plugin.tier}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs font-mono text-gray-600">{plugin.version}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATUS_STYLES[plugin.status]}`}>
                      {plugin.status.replace("_", " ")}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {plugin.lastHealthCheck ? (
                      <div className="flex items-center gap-1.5">
                        <div className={`h-2 w-2 rounded-full ${HEALTH_DOT[plugin.lastHealthCheck]}`} />
                        <span className="text-xs text-gray-500">{plugin.lastHealthCheck}</span>
                      </div>
                    ) : <span className="text-xs text-gray-400">—</span>}
                  </td>
                  <td className="px-4 py-3 text-gray-600 text-sm">{plugin.installCount.toLocaleString()}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1.5">
                      {plugin.status === "ACTIVE" || plugin.status === "PENDING_REVIEW"
                        ? <button onClick={() => {
                              const reason = window.prompt("Suspension reason:");
                              if (reason) suspendMutation.mutate({ id: plugin.pluginId, reason });
                            }} className="text-xs text-red-600 hover:underline">Suspend</button>
                        : plugin.status === "SUSPENDED"
                          ? <button onClick={() => activateMutation.mutate(plugin.pluginId)}
                              className="text-xs text-green-600 hover:underline">Activate</button>
                          : null}
                    </div>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {detailPlugin && (
        <PluginDrawer plugin={detailPlugin} onClose={() => setDetailPlugin(null)} />
      )}
    </div>
  );
}
