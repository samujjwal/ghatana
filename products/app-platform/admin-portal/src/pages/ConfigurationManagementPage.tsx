import React, { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { atom, useAtom } from "jotai";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface ConfigEntry {
  key: string;
  namespace: string;
  value: string;
  dataType: "STRING" | "INTEGER" | "BOOLEAN" | "JSON";
  tier: "T1" | "T2" | "T3";
  makerCheckerState: "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED";
  createdBy: string;
  updatedAt: string;
  description: string;
}

interface ConfigUpdate {
  namespace: string;
  key: string;
  value: string;
  reason: string;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const API = "/api/admin/config";

async function fetchAllConfigs(namespace: string | null): Promise<ConfigEntry[]> {
  const url = namespace ? `${API}?namespace=${encodeURIComponent(namespace)}` : API;
  const res = await fetch(url);
  if (!res.ok) throw new Error("Failed to fetch configurations");
  return res.json();
}

async function proposeUpdate(update: ConfigUpdate): Promise<ConfigEntry> {
  const res = await fetch(API, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(update),
  });
  if (!res.ok) throw new Error("Failed to propose config update");
  return res.json();
}

async function approveConfig(key: string, namespace: string): Promise<void> {
  const res = await fetch(`${API}/${encodeURIComponent(namespace)}/${encodeURIComponent(key)}/approve`, {
    method: "POST",
  });
  if (!res.ok) throw new Error("Failed to approve config");
}

async function rejectConfig(key: string, namespace: string, reason: string): Promise<void> {
  const res = await fetch(`${API}/${encodeURIComponent(namespace)}/${encodeURIComponent(key)}/reject`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) throw new Error("Failed to reject config");
}

async function bulkImportYaml(yamlContent: string): Promise<{ imported: number; errors: string[] }> {
  const res = await fetch(`${API}/bulk-import`, {
    method: "POST",
    headers: { "Content-Type": "application/yaml" },
    body: yamlContent,
  });
  if (!res.ok) throw new Error("Failed to import YAML");
  return res.json();
}

// ---------------------------------------------------------------------------
// State atoms
// ---------------------------------------------------------------------------

const selectedNamespaceAtom = atom<string | null>(null);
const editingEntryAtom = atom<ConfigEntry | null>(null);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const STATE_COLORS: Record<ConfigEntry["makerCheckerState"], string> = {
  DRAFT:             "bg-gray-100 text-gray-600",
  PENDING_APPROVAL:  "bg-yellow-100 text-yellow-700",
  APPROVED:          "bg-green-100 text-green-700",
  REJECTED:          "bg-red-100 text-red-700",
};

// ---------------------------------------------------------------------------
// Edit modal
// ---------------------------------------------------------------------------

interface EditModalProps {
  entry: ConfigEntry | null;
  onClose: () => void;
  onSave: (update: ConfigUpdate) => void;
  saving: boolean;
}

function EditModal({ entry, onClose, onSave, saving }: EditModalProps) {
  const [value, setValue] = useState(entry?.value ?? "");
  const [reason, setReason] = useState("");

  if (!entry) return null;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    onSave({ namespace: entry!.namespace, key: entry!.key, value, reason });
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">
        <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">Edit Configuration</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <form onSubmit={submit} className="p-6 space-y-4">
          <div>
            <p className="text-sm text-gray-500">Key</p>
            <p className="font-mono text-sm text-gray-800">{entry.namespace}.{entry.key}</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Value</label>
            <textarea
              rows={entry.dataType === "JSON" ? 6 : 2}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 font-mono text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Reason for change</label>
            <input
              type="text" value={reason} onChange={(e) => setReason(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Required for maker-checker audit trail"
              required
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-1.5 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
              {saving ? "Proposing…" : "Propose Change"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// YAML import panel
// ---------------------------------------------------------------------------

function YamlImportPanel({ onClose }: { onClose: () => void }) {
  const [yaml, setYaml] = useState("");
  const [result, setResult] = useState<{ imported: number; errors: string[] } | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleImport() {
    setLoading(true);
    try {
      const r = await bulkImportYaml(yaml);
      setResult(r);
    } catch (err: any) {
      setResult({ imported: 0, errors: [err.message] });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 p-4 shadow space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-gray-800">Bulk YAML Import</h3>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-sm">Close</button>
      </div>
      <textarea
        rows={10}
        value={yaml}
        onChange={(e) => setYaml(e.target.value)}
        placeholder={"# Example:\nplatform:\n  feature.flag.x: true\n  batch.size: 100"}
        className="w-full font-mono text-xs border border-gray-300 rounded-lg p-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <button
        onClick={handleImport} disabled={loading || !yaml.trim()}
        className="px-4 py-1.5 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
      >
        {loading ? "Importing…" : "Import"}
      </button>
      {result && (
        <div className={`rounded-lg p-3 text-sm ${result.errors.length ? "bg-red-50 text-red-700" : "bg-green-50 text-green-700"}`}>
          {result.errors.length
            ? <><p className="font-medium">Errors:</p><ul className="list-disc pl-4">{result.errors.map((e, i) => <li key={i}>{e}</li>)}</ul></>
            : <p>Imported {result.imported} entries successfully.</p>
          }
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function ConfigurationManagementPage() {
  const queryClient = useQueryClient();
  const [selectedNamespace, setSelectedNamespace] = useAtom(selectedNamespaceAtom);
  const [editingEntry, setEditingEntry] = useAtom(editingEntryAtom);
  const [search, setSearch] = useState("");
  const [showYamlImport, setShowYamlImport] = useState(false);

  const { data: configs, isLoading, error } = useQuery({
    queryKey: ["configs", selectedNamespace],
    queryFn: () => fetchAllConfigs(selectedNamespace),
  });

  const updateMutation = useMutation({
    mutationFn: proposeUpdate,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["configs"] });
      setEditingEntry(null);
    },
  });

  const approveMutation = useMutation({
    mutationFn: ({ key, namespace }: { key: string; namespace: string }) => approveConfig(key, namespace),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["configs"] }),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ key, namespace, reason }: { key: string; namespace: string; reason: string }) =>
      rejectConfig(key, namespace, reason),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["configs"] }),
  });

  const namespaces = configs ? [...new Set(configs.map((c) => c.namespace))] : [];
  const filtered = (configs ?? []).filter(
    (c) =>
      (!search || c.key.includes(search) || c.value.includes(search)) &&
      (!selectedNamespace || c.namespace === selectedNamespace)
  );

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex items-center gap-3 flex-wrap">
        <input
          type="search" placeholder="Search key or value…" value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm w-56 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          value={selectedNamespace ?? ""}
          onChange={(e) => setSelectedNamespace(e.target.value || null)}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none"
        >
          <option value="">All namespaces</option>
          {namespaces.map((ns) => <option key={ns} value={ns}>{ns}</option>)}
        </select>
        <button
          onClick={() => setShowYamlImport((v) => !v)}
          className="ml-auto px-3 py-1.5 text-sm bg-slate-800 text-white rounded-lg hover:bg-slate-700"
        >
          Bulk YAML Import
        </button>
      </div>

      {showYamlImport && <YamlImportPanel onClose={() => setShowYamlImport(false)} />}

      {/* Table */}
      {isLoading && <div className="text-gray-400 text-sm">Loading…</div>}
      {error && <div className="text-red-600 text-sm">Failed to load configurations.</div>}

      {!isLoading && !error && (
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {["Namespace", "Key", "Value", "Type", "Tier", "State", "Updated", "Actions"].map((h) => (
                  <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map((entry) => (
                <tr key={`${entry.namespace}.${entry.key}`} className="hover:bg-gray-50">
                  <td className="px-4 py-2 text-gray-500 font-mono text-xs">{entry.namespace}</td>
                  <td className="px-4 py-2 font-mono text-xs text-gray-800">{entry.key}</td>
                  <td className="px-4 py-2 max-w-xs">
                    <span className="font-mono text-xs text-gray-700 truncate block max-w-[200px]"
                      title={entry.value}>{entry.value}</span>
                  </td>
                  <td className="px-4 py-2 text-xs text-gray-500">{entry.dataType}</td>
                  <td className="px-4 py-2 text-xs text-gray-500">{entry.tier}</td>
                  <td className="px-4 py-2">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATE_COLORS[entry.makerCheckerState]}`}>
                      {entry.makerCheckerState.replace("_", " ")}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-xs text-gray-400">
                    {new Date(entry.updatedAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex gap-1">
                      <button onClick={() => setEditingEntry(entry)}
                        className="text-xs text-blue-600 hover:underline">Edit</button>
                      {entry.makerCheckerState === "PENDING_APPROVAL" && (
                        <>
                          <button onClick={() => approveMutation.mutate({ key: entry.key, namespace: entry.namespace })}
                            className="text-xs text-green-600 hover:underline">Approve</button>
                          <button onClick={() => {
                            const reason = window.prompt("Rejection reason:");
                            if (reason) rejectMutation.mutate({ key: entry.key, namespace: entry.namespace, reason });
                          }} className="text-xs text-red-600 hover:underline">Reject</button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr><td colSpan={8} className="text-center text-gray-400 py-8 text-sm">No configurations found.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <EditModal
        entry={editingEntry}
        onClose={() => setEditingEntry(null)}
        onSave={(u) => updateMutation.mutate(u)}
        saving={updateMutation.isPending}
      />
    </div>
  );
}
