import React, { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface Permission {
  permissionId: string;
  permissionKey: string;  // e.g. "config:write", "users:delete"
  description: string;
  category: string;
}

interface Role {
  roleId: string;
  roleName: string;
  displayName: string;
  description: string;
  permissions: string[];  // permissionKey[]
  userCount: number;
  makerCheckerState: "DRAFT" | "PENDING_APPROVAL" | "APPROVED";
  createdAt: string;
  updatedAt: string;
}

interface RoleProposal {
  roleName: string;
  displayName: string;
  description: string;
  permissions: string[];
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const API = "/api/admin/roles";

async function fetchRoles(): Promise<Role[]> {
  const res = await fetch(API);
  if (!res.ok) throw new Error("Failed to load roles");
  return res.json();
}

async function fetchPermissions(): Promise<Permission[]> {
  const res = await fetch("/api/admin/permissions");
  if (!res.ok) throw new Error("Failed to load permissions");
  return res.json();
}

async function createRole(payload: RoleProposal): Promise<Role> {
  const res = await fetch(API, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error("Failed to create role");
  return res.json();
}

async function updateRolePermissions(roleId: string, permissions: string[]): Promise<void> {
  const res = await fetch(`${API}/${roleId}/permissions`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ permissions }),
  });
  if (!res.ok) throw new Error("Failed to update permissions");
}

async function approveRole(roleId: string): Promise<void> {
  const res = await fetch(`${API}/${roleId}/approve`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to approve role");
}

async function deleteRole(roleId: string): Promise<void> {
  const res = await fetch(`${API}/${roleId}`, { method: "DELETE" });
  if (!res.ok) throw new Error("Failed to delete role");
}

// ---------------------------------------------------------------------------
// Permission matrix cell
// ---------------------------------------------------------------------------

function PermissionCell({ checked, onChange, disabled }: { checked: boolean; onChange: () => void; disabled: boolean }) {
  return (
    <td className="px-3 py-2 text-center">
      <input type="checkbox" checked={checked} onChange={onChange} disabled={disabled}
        className="accent-blue-600 h-4 w-4" />
    </td>
  );
}

// ---------------------------------------------------------------------------
// Role permission editor modal
// ---------------------------------------------------------------------------

interface EditRoleModalProps {
  role: Role;
  permissions: Permission[];
  onClose: () => void;
  onSave: (permissions: string[]) => void;
  saving: boolean;
}

function EditRoleModal({ role, permissions, onClose, onSave, saving }: EditRoleModalProps) {
  const [selected, setSelected] = useState<Set<string>>(new Set(role.permissions));

  const categories = [...new Set(permissions.map((p) => p.category))].sort();

  function toggle(key: string) {
    setSelected((prev) => { const s = new Set(prev); s.has(key) ? s.delete(key) : s.add(key); return s; });
  }

  function toggleCategory(cat: string) {
    const catKeys = permissions.filter((p) => p.category === cat).map((p) => p.permissionKey);
    const allSelected = catKeys.every((k) => selected.has(k));
    setSelected((prev) => {
      const s = new Set(prev);
      catKeys.forEach((k) => allSelected ? s.delete(k) : s.add(k));
      return s;
    });
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col">
        <div className="px-6 py-4 border-b flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">Edit Permissions — {role.displayName}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <div className="flex-1 overflow-auto p-6 space-y-4">
          {categories.map((cat) => {
            const catPerms = permissions.filter((p) => p.category === cat);
            const allSelected = catPerms.every((p) => selected.has(p.permissionKey));
            return (
              <div key={cat}>
                <label className="flex items-center gap-2 font-medium text-sm text-gray-700 mb-2 cursor-pointer">
                  <input type="checkbox" checked={allSelected} onChange={() => toggleCategory(cat)}
                    className="accent-blue-600" />
                  <span className="uppercase text-xs tracking-wide text-gray-500">{cat}</span>
                </label>
                <div className="grid grid-cols-2 gap-1 pl-6">
                  {catPerms.map((perm) => (
                    <label key={perm.permissionKey} className="flex items-center gap-2 text-sm cursor-pointer py-0.5">
                      <input type="checkbox" checked={selected.has(perm.permissionKey)}
                        onChange={() => toggle(perm.permissionKey)} className="accent-blue-600" />
                      <span className="font-mono text-xs text-gray-700">{perm.permissionKey}</span>
                    </label>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
        <div className="px-6 py-4 border-t flex justify-between items-center">
          <p className="text-xs text-yellow-600 bg-yellow-50 px-3 py-1 rounded">
            Changes require maker-checker approval.
          </p>
          <div className="flex gap-2">
            <button onClick={onClose}
              className="px-4 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Cancel</button>
            <button onClick={() => onSave([...selected])} disabled={saving}
              className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
              {saving ? "Saving…" : "Propose Changes"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Create role modal
// ---------------------------------------------------------------------------

function CreateRoleModal({ onClose, onCreate }: { onClose: () => void; onCreate: (p: RoleProposal) => void }) {
  const [form, setForm] = useState<RoleProposal>({ roleName: "", displayName: "", description: "", permissions: [] });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="px-6 py-4 border-b flex justify-between items-center">
          <h2 className="font-semibold text-gray-800">Create Role</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <form onSubmit={(e) => { e.preventDefault(); onCreate(form); }} className="p-6 space-y-4">
          {(["roleName", "displayName", "description"] as const).map((field) => (
            <div key={field}>
              <label className="block text-sm font-medium text-gray-700 mb-1 capitalize">
                {field === "roleName" ? "Role Name (code)" : field.charAt(0).toUpperCase() + field.slice(1)}
              </label>
              <input
                type="text" required={field !== "description"}
                value={form[field]} onChange={(e) => setForm((f) => ({ ...f, [field]: e.target.value }))}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder={field === "roleName" ? "e.g. DATA_ANALYST" : ""}
              />
            </div>
          ))}
          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose}
              className="px-4 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Cancel</button>
            <button type="submit"
              className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
              Create (permissions configured next)
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

const STATE_COLORS: Record<Role["makerCheckerState"], string> = {
  DRAFT:             "bg-gray-100 text-gray-600",
  PENDING_APPROVAL:  "bg-yellow-100 text-yellow-700",
  APPROVED:          "bg-green-100 text-green-700",
};

export default function RolePermissionManagementPage() {
  const queryClient = useQueryClient();
  const [editingRole, setEditingRole] = useState<Role | null>(null);
  const [showCreate, setShowCreate] = useState(false);

  const { data: roles, isLoading: rolesLoading } = useQuery({ queryKey: ["roles"], queryFn: fetchRoles });
  const { data: permissions = [] } = useQuery({ queryKey: ["permissions"], queryFn: fetchPermissions });

  const createMutation = useMutation({
    mutationFn: createRole,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["roles"] }); setShowCreate(false); },
  });

  const updatePermsMutation = useMutation({
    mutationFn: ({ roleId, perms }: { roleId: string; perms: string[] }) =>
      updateRolePermissions(roleId, perms),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["roles"] }); setEditingRole(null); },
  });

  const approveMutation = useMutation({
    mutationFn: approveRole,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["roles"] }),
  });

  const deleteMutation = useMutation({
    mutationFn: deleteRole,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["roles"] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button onClick={() => setShowCreate(true)}
          className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          + Create Role
        </button>
      </div>

      {rolesLoading && <div className="text-gray-400 text-sm">Loading roles…</div>}

      <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              {["Role", "Permissions", "Users", "State", "Updated", "Actions"].map((h) => (
                <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {(roles ?? []).map((role) => (
              <tr key={role.roleId} className="hover:bg-gray-50">
                <td className="px-4 py-3">
                  <p className="font-medium text-gray-800">{role.displayName}</p>
                  <p className="text-xs font-mono text-gray-400">{role.roleName}</p>
                  {role.description && <p className="text-xs text-gray-500 mt-0.5">{role.description}</p>}
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1 max-w-xs">
                    {role.permissions.slice(0, 4).map((p) => (
                      <span key={p} className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded font-mono">{p}</span>
                    ))}
                    {role.permissions.length > 4 && (
                      <span className="text-xs text-blue-600">+{role.permissions.length - 4} more</span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3 text-gray-600">{role.userCount}</td>
                <td className="px-4 py-3">
                  <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATE_COLORS[role.makerCheckerState]}`}>
                    {role.makerCheckerState.replace("_", " ")}
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-gray-400">{new Date(role.updatedAt).toLocaleDateString()}</td>
                <td className="px-4 py-3">
                  <div className="flex gap-1.5">
                    <button onClick={() => setEditingRole(role)} className="text-xs text-blue-600 hover:underline">Permissions</button>
                    {role.makerCheckerState === "PENDING_APPROVAL" && (
                      <button onClick={() => approveMutation.mutate(role.roleId)}
                        className="text-xs text-green-600 hover:underline">Approve</button>
                    )}
                    {role.userCount === 0 && (
                      <button onClick={() => window.confirm(`Delete ${role.roleName}?`) && deleteMutation.mutate(role.roleId)}
                        className="text-xs text-red-600 hover:underline">Delete</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editingRole && (
        <EditRoleModal
          role={editingRole} permissions={permissions}
          onClose={() => setEditingRole(null)}
          onSave={(perms) => updatePermsMutation.mutate({ roleId: editingRole.roleId, perms })}
          saving={updatePermsMutation.isPending}
        />
      )}

      {showCreate && (
        <CreateRoleModal onClose={() => setShowCreate(false)} onCreate={(p) => createMutation.mutate(p)} />
      )}
    </div>
  );
}
