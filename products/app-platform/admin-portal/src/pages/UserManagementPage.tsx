import React, { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface User {
  userId: string;
  username: string;
  displayName: string;
  email: string;
  roles: string[];
  status: "ACTIVE" | "SUSPENDED" | "PENDING";
  mfaEnabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

interface CreateUserPayload {
  username: string;
  displayName: string;
  email: string;
  roles: string[];
  sendInviteEmail: boolean;
}

interface UserPage {
  users: User[];
  total: number;
  page: number;
  pageSize: number;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const API = "/api/admin/users";

async function fetchUsers(page: number, pageSize: number, search: string, status: string): Promise<UserPage> {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  if (search) params.set("search", search);
  if (status) params.set("status", status);
  const res = await fetch(`${API}?${params}`);
  if (!res.ok) throw new Error("Failed to load users");
  return res.json();
}

async function createUser(payload: CreateUserPayload): Promise<User> {
  const res = await fetch(API, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error("Failed to create user");
  return res.json();
}

async function suspendUser(userId: string): Promise<void> {
  const res = await fetch(`${API}/${userId}/suspend`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to suspend user");
}

async function activateUser(userId: string): Promise<void> {
  const res = await fetch(`${API}/${userId}/activate`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to activate user");
}

async function deleteUser(userId: string): Promise<void> {
  const res = await fetch(`${API}/${userId}`, { method: "DELETE" });
  if (!res.ok) throw new Error("Failed to delete user");
}

async function exportCsv(): Promise<void> {
  const res = await fetch(`${API}/export/csv`);
  if (!res.ok) throw new Error("Failed to export CSV");
  const blob = await res.blob();
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement("a");
  a.href     = url;
  a.download = `users-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

const ALL_ROLES = ["PLATFORM_ADMIN", "KYC_REVIEWER", "COMPLIANCE_OFFICER", "PLUGIN_MANAGER", "READ_ONLY"];

// ---------------------------------------------------------------------------
// Status badge
// ---------------------------------------------------------------------------

const STATUS_STYLES: Record<User["status"], string> = {
  ACTIVE:    "bg-green-100 text-green-700",
  SUSPENDED: "bg-red-100   text-red-700",
  PENDING:   "bg-yellow-100 text-yellow-700",
};

// ---------------------------------------------------------------------------
// Create user modal
// ---------------------------------------------------------------------------

function CreateUserModal({ onClose, onCreate }: { onClose: () => void; onCreate: (p: CreateUserPayload) => void }) {
  const [form, setForm] = useState<CreateUserPayload>({
    username: "", displayName: "", email: "", roles: [], sendInviteEmail: true,
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (form.roles.length === 0) { alert("Select at least one role."); return; }
    onCreate(form);
  }

  function toggleRole(role: string) {
    setForm((f) => ({
      ...f,
      roles: f.roles.includes(role) ? f.roles.filter((r) => r !== role) : [...f.roles, role],
    }));
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="px-6 py-4 border-b flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">Create User</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <form onSubmit={submit} className="p-6 space-y-4">
          {(["username", "displayName", "email"] as const).map((field) => (
            <div key={field}>
              <label className="block text-sm font-medium text-gray-700 capitalize mb-1">
                {field === "displayName" ? "Display Name" : field.charAt(0).toUpperCase() + field.slice(1)}
              </label>
              <input
                type={field === "email" ? "email" : "text"} required
                value={form[field]} onChange={(e) => setForm((f) => ({ ...f, [field]: e.target.value }))}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          ))}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Roles</label>
            <div className="flex flex-wrap gap-2">
              {ALL_ROLES.map((role) => (
                <label key={role} className="flex items-center gap-1.5 cursor-pointer text-sm">
                  <input type="checkbox" checked={form.roles.includes(role)} onChange={() => toggleRole(role)}
                    className="accent-blue-600" />
                  {role.replace(/_/g, " ")}
                </label>
              ))}
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm cursor-pointer">
            <input type="checkbox" checked={form.sendInviteEmail}
              onChange={(e) => setForm((f) => ({ ...f, sendInviteEmail: e.target.checked }))}
              className="accent-blue-600" />
            Send invite email
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose}
              className="px-4 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Cancel</button>
            <button type="submit"
              className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
              Create
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

export default function UserManagementPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const PAGE_SIZE = 20;
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [showCreate, setShowCreate] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const { data, isLoading } = useQuery({
    queryKey: ["users", page, PAGE_SIZE, search, statusFilter],
    queryFn: () => fetchUsers(page, PAGE_SIZE, search, statusFilter),
  });

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["users"] }); setShowCreate(false); },
  });

  const suspendMutation = useMutation({
    mutationFn: suspendUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["users"] }),
  });

  const activateMutation = useMutation({
    mutationFn: activateUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["users"] }),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteUser,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["users"] }),
  });

  function toggleSelect(userId: string) {
    setSelected((prev) => { const s = new Set(prev); s.has(userId) ? s.delete(userId) : s.add(userId); return s; });
  }

  function toggleAll() {
    if (!data) return;
    if (selected.size === data.users.length) setSelected(new Set());
    else setSelected(new Set(data.users.map((u) => u.userId)));
  }

  async function bulkSuspend() {
    await Promise.all([...selected].map((id) => suspendMutation.mutateAsync(id)));
    setSelected(new Set());
  }

  const totalPages = data ? Math.ceil(data.total / PAGE_SIZE) : 1;

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex items-center gap-3 flex-wrap">
        <input
          type="search" placeholder="Search users…" value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(1); }}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm w-52 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(1); }}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm">
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="PENDING">Pending</option>
        </select>
        {selected.size > 0 && (
          <button onClick={bulkSuspend}
            className="px-3 py-1.5 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700">
            Suspend {selected.size} selected
          </button>
        )}
        <div className="ml-auto flex gap-2">
          <button onClick={exportCsv}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">
            CSV Export
          </button>
          <button onClick={() => setShowCreate(true)}
            className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
            + Create User
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="px-4 py-2.5">
                <input type="checkbox" onChange={toggleAll}
                  checked={data ? selected.size === data.users.length && data.users.length > 0 : false}
                  className="accent-blue-600" />
              </th>
              {["Name", "Email", "Roles", "Status", "MFA", "Last Login", "Actions"].map((h) => (
                <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr><td colSpan={8} className="text-center py-8 text-gray-400">Loading…</td></tr>
            ) : (data?.users ?? []).map((user) => (
              <tr key={user.userId} className={`hover:bg-gray-50 ${selected.has(user.userId) ? "bg-blue-50" : ""}`}>
                <td className="px-4 py-3">
                  <input type="checkbox" checked={selected.has(user.userId)} onChange={() => toggleSelect(user.userId)}
                    className="accent-blue-600" />
                </td>
                <td className="px-4 py-3">
                  <p className="font-medium text-gray-800">{user.displayName}</p>
                  <p className="text-xs text-gray-400">@{user.username}</p>
                </td>
                <td className="px-4 py-3 text-gray-600">{user.email}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {user.roles.map((r) => (
                      <span key={r} className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
                        {r.replace(/_/g, " ")}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATUS_STYLES[user.status]}`}>
                    {user.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-center">
                  {user.mfaEnabled
                    ? <span className="text-green-600 text-xs font-medium">✓ ON</span>
                    : <span className="text-gray-400 text-xs">—</span>}
                </td>
                <td className="px-4 py-3 text-xs text-gray-400">
                  {user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : "Never"}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-1.5">
                    {user.status === "ACTIVE"
                      ? <button onClick={() => suspendMutation.mutate(user.userId)}
                          className="text-xs text-orange-600 hover:underline">Suspend</button>
                      : <button onClick={() => activateMutation.mutate(user.userId)}
                          className="text-xs text-green-600 hover:underline">Activate</button>}
                    <button onClick={() => {
                        if (window.confirm(`Delete user ${user.username}?`))
                          deleteMutation.mutate(user.userId);
                      }} className="text-xs text-red-600 hover:underline">Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between text-sm text-gray-500">
        <span>{data ? `${data.total} total users` : ""}</span>
        <div className="flex gap-2">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page === 1}
            className="px-3 py-1 border rounded-lg disabled:opacity-40 hover:bg-gray-50">← Prev</button>
          <span className="px-3 py-1">{page} / {totalPages}</span>
          <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page === totalPages}
            className="px-3 py-1 border rounded-lg disabled:opacity-40 hover:bg-gray-50">Next →</button>
        </div>
      </div>

      {showCreate && (
        <CreateUserModal
          onClose={() => setShowCreate(false)}
          onCreate={(p) => createMutation.mutate(p)}
        />
      )}
    </div>
  );
}
