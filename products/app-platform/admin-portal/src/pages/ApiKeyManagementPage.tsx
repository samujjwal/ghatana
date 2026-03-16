import React, { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface ApiKey {
  keyId: string;
  keyPrefix: string;      // First 8 chars, e.g. "gk_prod_"
  name: string;
  serviceAccountId: string;
  serviceAccountName: string;
  scopes: string[];
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
  hmacAlgorithm: string;  // "HMAC-SHA256"
  lastUsedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
  rotationDueAt: string | null;
}

interface CreateKeyPayload {
  name: string;
  serviceAccountId: string;
  scopes: string[];
  expiryDays: number | null;
}

interface CreatedKeyResponse {
  key: ApiKey;
  plainTextKey: string;   // ONE-TIME: shown once, never stored again
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const API = "/api/admin/api-keys";

async function fetchApiKeys(): Promise<ApiKey[]> {
  const res = await fetch(API);
  if (!res.ok) throw new Error("Failed to load API keys");
  return res.json();
}

async function createApiKey(payload: CreateKeyPayload): Promise<CreatedKeyResponse> {
  const res = await fetch(API, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error("Failed to create API key");
  return res.json();
}

async function revokeApiKey(keyId: string): Promise<void> {
  const res = await fetch(`${API}/${keyId}/revoke`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to revoke API key");
}

async function rotateApiKey(keyId: string): Promise<CreatedKeyResponse> {
  const res = await fetch(`${API}/${keyId}/rotate`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to rotate API key");
  return res.json();
}

async function fetchServiceAccounts(): Promise<{ id: string; name: string }[]> {
  const res = await fetch("/api/admin/service-accounts");
  if (!res.ok) throw new Error("Failed to load service accounts");
  return res.json();
}

const COMMON_SCOPES = [
  "events:publish", "events:subscribe",
  "config:read", "config:write",
  "audit:read",
  "plugins:manage",
  "users:read",
];

// ---------------------------------------------------------------------------
// Status badge
// ---------------------------------------------------------------------------

const STATUS_STYLES: Record<ApiKey["status"], string> = {
  ACTIVE:  "bg-green-100 text-green-700",
  REVOKED: "bg-red-100   text-red-700",
  EXPIRED: "bg-gray-100  text-gray-500",
};

// ---------------------------------------------------------------------------
// One-time key reveal modal
// ---------------------------------------------------------------------------

function KeyRevealModal({ plainTextKey, onClose }: { plainTextKey: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false);

  function copyKey() {
    navigator.clipboard.writeText(plainTextKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">
        <div className="px-6 py-4 border-b">
          <h2 className="font-semibold text-gray-800">API Key Created</h2>
        </div>
        <div className="p-6 space-y-4">
          <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-sm text-amber-700">
            This key will <strong>never be shown again</strong>. Copy it now and store it securely.
          </div>
          <div className="flex items-center gap-2">
            <code className="flex-1 font-mono text-sm bg-gray-100 rounded px-3 py-2 overflow-x-auto break-all">
              {plainTextKey}
            </code>
            <button onClick={copyKey}
              className={`flex-shrink-0 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors
                ${copied ? "bg-green-600 text-white" : "bg-gray-200 hover:bg-gray-300 text-gray-700"}`}>
              {copied ? "Copied!" : "Copy"}
            </button>
          </div>
        </div>
        <div className="px-6 py-4 border-t flex justify-end">
          <button onClick={onClose}
            className="px-4 py-1.5 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700">
            I've saved this key
          </button>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Create key modal
// ---------------------------------------------------------------------------

function CreateKeyModal({ onClose, onCreate, serviceAccounts }: {
  onClose: () => void;
  onCreate: (p: CreateKeyPayload) => void;
  serviceAccounts: { id: string; name: string }[];
}) {
  const [name, setName] = useState("");
  const [serviceAccountId, setServiceAccountId] = useState("");
  const [selectedScopes, setSelectedScopes] = useState<string[]>([]);
  const [expiryDays, setExpiryDays] = useState<number | null>(90);

  function toggleScope(scope: string) {
    setSelectedScopes((prev) =>
      prev.includes(scope) ? prev.filter((s) => s !== scope) : [...prev, scope]
    );
  }

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!serviceAccountId) { alert("Select a service account"); return; }
    if (selectedScopes.length === 0) { alert("Select at least one scope"); return; }
    onCreate({ name, serviceAccountId, scopes: selectedScopes, expiryDays });
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="px-6 py-4 border-b flex justify-between">
          <h2 className="font-semibold text-gray-800">Create API Key</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <form onSubmit={submit} className="p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Key Name</label>
            <input type="text" required value={name} onChange={(e) => setName(e.target.value)}
              placeholder="e.g. reporting-service-prod"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Service Account</label>
            <select value={serviceAccountId} onChange={(e) => setServiceAccountId(e.target.value)} required
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none">
              <option value="">Select…</option>
              {serviceAccounts.map((sa) => <option key={sa.id} value={sa.id}>{sa.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Scopes</label>
            <div className="grid grid-cols-2 gap-1">
              {COMMON_SCOPES.map((scope) => (
                <label key={scope} className="flex items-center gap-1.5 text-sm cursor-pointer">
                  <input type="checkbox" className="accent-blue-600" checked={selectedScopes.includes(scope)}
                    onChange={() => toggleScope(scope)} />
                  <span className="font-mono text-xs">{scope}</span>
                </label>
              ))}
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Expiry (days)</label>
            <div className="flex gap-2">
              {[30, 90, 180, 365].map((d) => (
                <button key={d} type="button" onClick={() => setExpiryDays(d)}
                  className={`px-2 py-1 text-xs rounded border ${expiryDays === d ? "bg-blue-600 text-white border-blue-600" : "border-gray-300 hover:bg-gray-50"}`}>
                  {d}d
                </button>
              ))}
              <button type="button" onClick={() => setExpiryDays(null)}
                className={`px-2 py-1 text-xs rounded border ${expiryDays === null ? "bg-blue-600 text-white border-blue-600" : "border-gray-300 hover:bg-gray-50"}`}>
                No expiry
              </button>
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose}
              className="px-4 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Cancel</button>
            <button type="submit"
              className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
              Generate Key
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function ApiKeyManagementPage() {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const [revealedKey, setRevealedKey] = useState<string | null>(null);

  const { data: keys, isLoading } = useQuery({ queryKey: ["api-keys"], queryFn: fetchApiKeys });
  const { data: serviceAccounts = [] } = useQuery({ queryKey: ["service-accounts"], queryFn: fetchServiceAccounts });

  const createMutation = useMutation({
    mutationFn: createApiKey,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["api-keys"] });
      setShowCreate(false);
      setRevealedKey(result.plainTextKey);
    },
  });

  const revokeMutation = useMutation({
    mutationFn: revokeApiKey,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["api-keys"] }),
  });

  const rotateMutation = useMutation({
    mutationFn: rotateApiKey,
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["api-keys"] });
      setRevealedKey(result.plainTextKey);
    },
  });

  const isRotationDue = (key: ApiKey) =>
    key.rotationDueAt && new Date(key.rotationDueAt) < new Date();

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button onClick={() => setShowCreate(true)}
          className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          + Generate API Key
        </button>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              {["Key", "Service Account", "Scopes", "Status", "Last Used", "Expires", "Actions"].map((h) => (
                <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading
              ? <tr><td colSpan={7} className="text-center py-8 text-gray-400">Loading…</td></tr>
              : (keys ?? []).map((key) => (
                <tr key={key.keyId} className={`hover:bg-gray-50 ${isRotationDue(key) ? "bg-amber-50" : ""}`}>
                  <td className="px-4 py-3">
                    <p className="font-medium text-gray-800">{key.name}</p>
                    <p className="font-mono text-xs text-gray-400">{key.keyPrefix}••••••••</p>
                    {isRotationDue(key) && (
                      <span className="text-xs text-amber-600 font-medium">Rotation overdue</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-600 text-xs">{key.serviceAccountName}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {key.scopes.map((s) => (
                        <span key={s} className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded font-mono">{s}</span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATUS_STYLES[key.status]}`}>
                      {key.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-400">
                    {key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : "Never"}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-400">
                    {key.expiresAt ? new Date(key.expiresAt).toLocaleDateString() : "No expiry"}
                  </td>
                  <td className="px-4 py-3">
                    {key.status === "ACTIVE" && (
                      <div className="flex gap-1.5">
                        <button onClick={() => rotateMutation.mutate(key.keyId)}
                          className="text-xs text-blue-600 hover:underline">Rotate</button>
                        <button onClick={() => window.confirm(`Revoke key "${key.name}"?`) && revokeMutation.mutate(key.keyId)}
                          className="text-xs text-red-600 hover:underline">Revoke</button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {showCreate && (
        <CreateKeyModal
          onClose={() => setShowCreate(false)}
          onCreate={(p) => createMutation.mutate(p)}
          serviceAccounts={serviceAccounts}
        />
      )}

      {revealedKey && (
        <KeyRevealModal plainTextKey={revealedKey} onClose={() => setRevealedKey(null)} />
      )}
    </div>
  );
}
