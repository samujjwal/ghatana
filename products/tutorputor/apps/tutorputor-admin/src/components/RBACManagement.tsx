/**
 * RBAC Permission UI for Admins
 *
 * Interface for tenant administrators to view and assign roles/permissions to users.
 *
 * @doc.type component
 * @doc.purpose RBAC management interface for administrators
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useEffect } from "react";

export interface Role {
  id: string;
  name: string;
  description?: string;
}

export interface Permission {
  id: string;
  name: string;
  resource: string;
  action: string;
  description?: string;
}

export interface UserWithRoles {
  id: string;
  email: string;
  displayName?: string;
  roles: Role[];
  permissions: Permission[];
}

export function RBACManagement() {
  const [users, setUsers] = useState<UserWithRoles[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [selectedUser, setSelectedUser] = useState<UserWithRoles | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showRoleModal, setShowRoleModal] = useState(false);

  useEffect(() => {
    fetchUsers();
    fetchRoles();
    fetchPermissions();
  }, []);

  const fetchUsers = async () => {
    try {
      const response = await fetch("/api/v1/admin/users/with-roles");
      if (response.ok) {
        const data = await response.json();
        setUsers(data.users || []);
      }
    } catch (error) {
      console.error("Failed to fetch users:", error);
    } finally {
      setLoading(false);
    }
  };

  const fetchRoles = async () => {
    try {
      const response = await fetch("/api/v1/admin/roles");
      if (response.ok) {
        const data = await response.json();
        setRoles(data.roles || []);
      }
    } catch (error) {
      console.error("Failed to fetch roles:", error);
    }
  };

  const fetchPermissions = async () => {
    try {
      const response = await fetch("/api/v1/admin/permissions");
      if (response.ok) {
        const data = await response.json();
        setPermissions(data.permissions || []);
      }
    } catch (error) {
      console.error("Failed to fetch permissions:", error);
    }
  };

  const assignRole = async (userId: string, roleId: string) => {
    setSaving(true);
    try {
      const response = await fetch("/api/v1/admin/users/roles", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId, roleId }),
      });
      if (response.ok) {
        await fetchUsers();
        if (selectedUser?.id === userId) {
          setSelectedUser((prev) => {
            if (!prev) return null;
            const role = roles.find((r) => r.id === roleId);
            return role
              ? { ...prev, roles: [...prev.roles, role] }
              : prev;
          });
        }
      }
    } catch (error) {
      console.error("Failed to assign role:", error);
    } finally {
      setSaving(false);
    }
  };

  const removeRole = async (userId: string, roleId: string) => {
    setSaving(true);
    try {
      const response = await fetch("/api/v1/admin/users/roles", {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId, roleId }),
      });
      if (response.ok) {
        await fetchUsers();
        if (selectedUser?.id === userId) {
          setSelectedUser((prev) => {
            if (!prev) return null;
            return {
              ...prev,
              roles: prev.roles.filter((r) => r.id !== roleId),
            };
          });
        }
      }
    } catch (error) {
      console.error("Failed to remove role:", error);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto p-6">
      <h1 className="text-3xl font-bold mb-6">RBAC Management</h1>
      <p className="text-gray-600 mb-8">
        Manage user roles and permissions for your tenant.
      </p>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Users List */}
        <div className="lg:col-span-1">
          <div className="bg-white border border-gray-200 rounded-lg shadow-sm">
            <div className="p-4 border-b border-gray-200">
              <h2 className="font-semibold">Users</h2>
            </div>
            <div className="max-h-96 overflow-y-auto">
              {users.map((user) => (
                <div
                  key={user.id}
                  onClick={() => setSelectedUser(user)}
                  className={`p-4 border-b border-gray-100 cursor-pointer hover:bg-gray-50 ${
                    selectedUser?.id === user.id ? "bg-blue-50" : ""
                  }`}
                >
                  <div className="font-medium">{user.displayName || user.email}</div>
                  <div className="text-sm text-gray-500">{user.email}</div>
                  <div className="mt-2 flex flex-wrap gap-1">
                    {user.roles.map((role) => (
                      <span
                        key={role.id}
                        className="px-2 py-1 text-xs bg-blue-100 text-blue-700 rounded"
                      >
                        {role.name}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* User Details */}
        <div className="lg:col-span-2">
          {selectedUser ? (
            <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-6">
              <div className="mb-6">
                <h2 className="text-xl font-semibold">{selectedUser.displayName || selectedUser.email}</h2>
                <p className="text-gray-500">{selectedUser.email}</p>
              </div>

              {/* Roles Section */}
              <div className="mb-6">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="font-semibold">Roles</h3>
                  <button
                    onClick={() => setShowRoleModal(true)}
                    className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
                  >
                    Add Role
                  </button>
                </div>
                <div className="space-y-2">
                  {selectedUser.roles.length === 0 ? (
                    <p className="text-gray-500 text-sm">No roles assigned</p>
                  ) : (
                    selectedUser.roles.map((role) => (
                      <div
                        key={role.id}
                        className="flex items-center justify-between p-3 bg-gray-50 rounded"
                      >
                        <div>
                          <div className="font-medium">{role.name}</div>
                          {role.description && (
                            <div className="text-sm text-gray-500">{role.description}</div>
                          )}
                        </div>
                        <button
                          onClick={() => removeRole(selectedUser.id, role.id)}
                          disabled={saving}
                          className="text-red-600 hover:text-red-700 disabled:opacity-50"
                        >
                          Remove
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* Permissions Section */}
              <div>
                <h3 className="font-semibold mb-3">Effective Permissions</h3>
                <div className="space-y-2">
                  {selectedUser.permissions.length === 0 ? (
                    <p className="text-gray-500 text-sm">No permissions</p>
                  ) : (
                    selectedUser.permissions.map((permission) => (
                      <div
                        key={permission.id}
                        className="p-3 bg-gray-50 rounded"
                      >
                        <div className="font-medium">{permission.name}</div>
                        <div className="text-sm text-gray-500">
                          {permission.resource}.{permission.action}
                        </div>
                        {permission.description && (
                          <div className="text-sm text-gray-500">{permission.description}</div>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-6">
              <p className="text-gray-500">Select a user to view and manage their roles and permissions.</p>
            </div>
          )}
        </div>
      </div>

      {/* Role Selection Modal */}
      {showRoleModal && selectedUser && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center">
          <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold mb-4">Assign Role</h3>
            <div className="space-y-2">
              {roles.map((role) => {
                const isAssigned = selectedUser.roles.some((r) => r.id === role.id);
                return (
                  <div
                    key={role.id}
                    onClick={() => !isAssigned && assignRole(selectedUser.id, role.id)}
                    className={`p-3 border rounded cursor-pointer ${
                      isAssigned
                        ? "bg-gray-100 border-gray-300 opacity-50 cursor-not-allowed"
                        : "hover:bg-gray-50"
                    }`}
                  >
                    <div className="font-medium">{role.name}</div>
                    {role.description && (
                      <div className="text-sm text-gray-500">{role.description}</div>
                    )}
                  </div>
                );
              })}
            </div>
            <button
              onClick={() => setShowRoleModal(false)}
              className="mt-4 w-full px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
