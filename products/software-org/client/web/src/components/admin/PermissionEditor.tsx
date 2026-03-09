/**
 * Permission Editor Component
 *
 * Comprehensive RBAC permission management with:
 * - Role selector and creation
 * - Permission matrix (resources × actions)
 * - Permission inheritance visualization
 * - Testing panel (simulate permission checks)
 * - Bulk role assignment
 *
 * @package @ghatana/software-org-web
 */

import React, { useState, useMemo } from 'react';
import { Box, Card, Button, Stack, Chip } from '@ghatana/ui';
import { useSimulatePermission } from '@/hooks';

interface Permission {
    id: string;
    resource: string;
    action: string;
    description?: string;
}

interface Role {
    id: string;
    name: string;
    description: string;
    permissions: string[]; // Permission IDs
    inheritsFrom?: string; // Parent role ID
    isSystemRole?: boolean;
}

interface User {
    id: string;
    name: string;
    email: string;
    roles: string[]; // Role IDs
}

interface PermissionEditorProps {
    roles: Role[];
    users?: User[];
    permissions: Permission[];
    onRoleUpdate?: (role: Role) => void;
    onRoleCreate?: (role: Omit<Role, 'id'>) => void;
    onRoleDelete?: (roleId: string) => void;
    onRoleAssign?: (userId: string, roleIds: string[]) => void;
    onTestPermission?: (userId: string, permissionId: string) => Promise<boolean>;
}

/**
 * Default permissions catalog
 */
const DEFAULT_PERMISSIONS: Permission[] = [
    // Tenants
    { id: 'tenants.read', resource: 'tenants', action: 'read', description: 'View tenants' },
    { id: 'tenants.write', resource: 'tenants', action: 'write', description: 'Create/update tenants' },
    { id: 'tenants.delete', resource: 'tenants', action: 'delete', description: 'Delete tenants' },

    // Departments
    { id: 'departments.read', resource: 'departments', action: 'read' },
    { id: 'departments.write', resource: 'departments', action: 'write' },
    { id: 'departments.delete', resource: 'departments', action: 'delete' },

    // Teams
    { id: 'teams.read', resource: 'teams', action: 'read' },
    { id: 'teams.write', resource: 'teams', action: 'write' },
    { id: 'teams.delete', resource: 'teams', action: 'delete' },

    // Users
    { id: 'users.read', resource: 'users', action: 'read' },
    { id: 'users.write', resource: 'users', action: 'write' },
    { id: 'users.delete', resource: 'users', action: 'delete' },

    // Roles
    { id: 'roles.read', resource: 'roles', action: 'read' },
    { id: 'roles.write', resource: 'roles', action: 'write' },
    { id: 'roles.delete', resource: 'roles', action: 'delete' },

    // Approvals
    { id: 'approvals.read', resource: 'approvals', action: 'read' },
    { id: 'approvals.approve', resource: 'approvals', action: 'approve' },
    { id: 'approvals.reject', resource: 'approvals', action: 'reject' },

    // Budgets
    { id: 'budgets.read', resource: 'budgets', action: 'read' },
    { id: 'budgets.write', resource: 'budgets', action: 'write' },
    { id: 'budgets.approve', resource: 'budgets', action: 'approve' },

    // Audit
    { id: 'audit.read', resource: 'audit', action: 'read' },
    { id: 'audit.export', resource: 'audit', action: 'export' },

    // Workflows
    { id: 'workflows.read', resource: 'workflows', action: 'read' },
    { id: 'workflows.write', resource: 'workflows', action: 'write' },
    { id: 'workflows.execute', resource: 'workflows', action: 'execute' },
];

export function PermissionEditor({
    roles,
    users = [],
    permissions = DEFAULT_PERMISSIONS,
    onRoleUpdate,
    onRoleCreate,
    onRoleDelete,
    onRoleAssign,
    onTestPermission,
}: PermissionEditorProps) {
    const [activeTab, setActiveTab] = useState<'matrix' | 'inheritance' | 'testing' | 'assignment'>(
        'matrix'
    );
    const [selectedRole, setSelectedRole] = useState<Role | null>(roles[0] || null);
    const [newRoleName, setNewRoleName] = useState('');
    const [newRoleDescription, setNewRoleDescription] = useState('');
    const [selectedPermissions, setSelectedPermissions] = useState<Set<string>>(
        new Set(selectedRole?.permissions || [])
    );

    // Testing panel state
    const [testUserId, setTestUserId] = useState('');
    const [testPermissionId, setTestPermissionId] = useState('');
    const [testResult, setTestResult] = useState<{ granted: boolean; matchedRoles: { roleName: string }[] } | null>(null);

    const simulatePermissionMutation = useSimulatePermission();

    // Assignment panel state
    const [assignUserId, setAssignUserId] = useState('');
    const [assignRoleIds, setAssignRoleIds] = useState<Set<string>>(new Set());

    // Group permissions by resource
    const permissionsByResource = useMemo(() => {
        const grouped: Record<string, Permission[]> = {};
        permissions.forEach((perm) => {
            if (!grouped[perm.resource]) {
                grouped[perm.resource] = [];
            }
            grouped[perm.resource].push(perm);
        });
        return grouped;
    }, [permissions]);

    // Calculate inherited permissions
    const getInheritedPermissions = (role: Role): string[] => {
        if (!role.inheritsFrom) return [];
        const parentRole = roles.find((r) => r.id === role.inheritsFrom);
        if (!parentRole) return [];
        return [...parentRole.permissions, ...getInheritedPermissions(parentRole)];
    };

    const effectivePermissions = useMemo(() => {
        if (!selectedRole) return new Set<string>();
        const inherited = getInheritedPermissions(selectedRole);
        return new Set([...selectedRole.permissions, ...inherited]);
    }, [selectedRole]);

    // Handlers
    const handleRoleSelect = (role: Role) => {
        setSelectedRole(role);
        setSelectedPermissions(new Set(role.permissions));
    };

    const handlePermissionToggle = (permissionId: string) => {
        setSelectedPermissions((prev) => {
            const next = new Set(prev);
            if (next.has(permissionId)) {
                next.delete(permissionId);
            } else {
                next.add(permissionId);
            }
            return next;
        });
    };

    const handleSaveRole = () => {
        if (!selectedRole) return;
        const updated: Role = {
            ...selectedRole,
            permissions: Array.from(selectedPermissions),
        };
        onRoleUpdate?.(updated);
    };

    const handleCreateRole = () => {
        if (!newRoleName.trim()) return;
        onRoleCreate?.({
            name: newRoleName,
            description: newRoleDescription,
            permissions: Array.from(selectedPermissions),
        });
        setNewRoleName('');
        setNewRoleDescription('');
        setSelectedPermissions(new Set());
    };

    const handleTestPermission = async () => {
        if (!testUserId || !testPermissionId) {
            alert('Please select both user and permission');
            return;
        }

        try {
            const result = await simulatePermissionMutation.mutateAsync({
                userId: testUserId,
                permissionId: testPermissionId,
            });
            setTestResult(result);
        } catch (error) {
            console.error('Permission simulation error:', error);
            alert('Failed to test permission');
        }
    };

    const handleAssignRoles = () => {
        if (!assignUserId || assignRoleIds.size === 0) return;
        onRoleAssign?.(assignUserId, Array.from(assignRoleIds));
        setAssignUserId('');
        setAssignRoleIds(new Set());
    };

    return (
        <Box className="space-y-6">
            {/* Header */}
            <div>
                <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                    Permission Editor
                </h2>
                <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                    Manage roles, permissions, and access control
                </p>
            </div>

            {/* Tabs */}
            <div className="flex gap-2 border-b border-slate-200 dark:border-neutral-700">
                {[
                    { id: 'matrix' as const, label: '📋 Permission Matrix', icon: '📋' },
                    { id: 'inheritance' as const, label: '🌳 Inheritance Tree', icon: '🌳' },
                    { id: 'testing' as const, label: '🧪 Testing Panel', icon: '🧪' },
                    { id: 'assignment' as const, label: '👥 Role Assignment', icon: '👥' },
                ].map((tab) => (
                    <button
                        key={tab.id}
                        onClick={() => setActiveTab(tab.id)}
                        className={`
              px-4 py-2 font-medium text-sm border-b-2 transition-all
              ${activeTab === tab.id
                                ? 'border-blue-600 text-blue-600'
                                : 'border-transparent text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-neutral-100'
                            }
            `}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Permission Matrix Tab */}
            {activeTab === 'matrix' && (
                <div className="grid grid-cols-3 gap-6">
                    {/* Role List */}
                    <Card>
                        <Box className="p-4">
                            <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">Roles</h3>
                            <Stack spacing={2}>
                                {roles.map((role) => (
                                    <div
                                        key={role.id}
                                        onClick={() => handleRoleSelect(role)}
                                        className={`
                      p-3 rounded-lg border cursor-pointer transition-all
                      ${selectedRole?.id === role.id
                                                ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                                : 'border-slate-200 dark:border-neutral-700 hover:border-blue-300'
                                            }
                    `}
                                    >
                                        <div className="flex items-center justify-between mb-1">
                                            <span className="font-medium text-slate-900 dark:text-neutral-100">
                                                {role.name}
                                            </span>
                                            {role.isSystemRole && (
                                                <Chip label="System" size="small" color="primary" />
                                            )}
                                        </div>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                                            {role.description}
                                        </p>
                                        <div className="flex items-center gap-2 mt-2">
                                            <span className="text-xs text-slate-500 dark:text-neutral-500">
                                                {role.permissions.length} permissions
                                            </span>
                                            {role.inheritsFrom && (
                                                <span className="text-xs text-blue-600">
                                                    ← inherits
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                ))}

                                {/* Create New Role */}
                                <div className="pt-4 border-t border-slate-200 dark:border-neutral-700">
                                    <input
                                        type="text"
                                        placeholder="New role name..."
                                        value={newRoleName}
                                        onChange={(e) => setNewRoleName(e.target.value)}
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg text-sm mb-2"
                                    />
                                    <input
                                        type="text"
                                        placeholder="Description..."
                                        value={newRoleDescription}
                                        onChange={(e) => setNewRoleDescription(e.target.value)}
                                        className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg text-sm mb-2"
                                    />
                                    <Button variant="outline" size="sm" fullWidth onClick={handleCreateRole}>
                                        ➕ Create Role
                                    </Button>
                                </div>
                            </Stack>
                        </Box>
                    </Card>

                    {/* Permission Matrix */}
                    <div className="col-span-2">
                        <Card>
                            <Box className="p-6">
                                <div className="flex items-center justify-between mb-4">
                                    <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                        Permissions for {selectedRole?.name || 'Select a role'}
                                    </h3>
                                    <Button variant="primary" size="sm" onClick={handleSaveRole}>
                                        💾 Save Changes
                                    </Button>
                                </div>

                                {selectedRole && (
                                    <div className="space-y-6 max-h-[600px] overflow-y-auto">
                                        {Object.entries(permissionsByResource).map(([resource, perms]) => (
                                            <div key={resource}>
                                                <h4 className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-3 capitalize">
                                                    {resource}
                                                </h4>
                                                <div className="grid grid-cols-2 gap-3">
                                                    {perms.map((perm) => {
                                                        const isInherited = effectivePermissions.has(perm.id) && !selectedPermissions.has(perm.id);
                                                        return (
                                                            <label
                                                                key={perm.id}
                                                                className={`
                                  flex items-center gap-2 p-2 rounded border cursor-pointer transition-all
                                  ${selectedPermissions.has(perm.id)
                                                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                                                        : isInherited
                                                                            ? 'border-green-300 bg-green-50 dark:bg-green-900/20'
                                                                            : 'border-slate-200 dark:border-neutral-700 hover:border-slate-300'
                                                                    }
                                `}
                                                            >
                                                                <input
                                                                    type="checkbox"
                                                                    checked={selectedPermissions.has(perm.id)}
                                                                    onChange={() => handlePermissionToggle(perm.id)}
                                                                    className="rounded"
                                                                />
                                                                <div className="flex-1">
                                                                    <span className="text-sm text-slate-900 dark:text-neutral-100">
                                                                        {perm.action}
                                                                    </span>
                                                                    {isInherited && (
                                                                        <span className="ml-2 text-xs text-green-600">(inherited)</span>
                                                                    )}
                                                                    {perm.description && (
                                                                        <p className="text-xs text-slate-500 dark:text-neutral-500">
                                                                            {perm.description}
                                                                        </p>
                                                                    )}
                                                                </div>
                                                            </label>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}

                                {!selectedRole && (
                                    <div className="py-12 text-center text-slate-500 dark:text-neutral-400">
                                        <p>Select a role to edit permissions</p>
                                    </div>
                                )}
                            </Box>
                        </Card>
                    </div>
                </div>
            )}

            {/* Inheritance Tree Tab */}
            {activeTab === 'inheritance' && (
                <Card>
                    <Box className="p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Role Inheritance Tree
                        </h3>
                        <div className="bg-slate-50 dark:bg-neutral-800 rounded-lg p-6 border border-slate-200 dark:border-neutral-700">
                            {/* Tree visualization would go here */}
                            <p className="text-sm text-slate-600 dark:text-neutral-400">
                                Role inheritance visualization (use RoleInheritanceTree component)
                            </p>
                        </div>
                    </Box>
                </Card>
            )}

            {/* Testing Panel Tab */}
            {activeTab === 'testing' && (
                <Card>
                    <Box className="p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Permission Testing
                        </h3>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    User ID
                                </label>
                                <input
                                    type="text"
                                    value={testUserId}
                                    onChange={(e) => setTestUserId(e.target.value)}
                                    placeholder="Enter user ID..."
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Permission ID
                                </label>
                                <select
                                    value={testPermissionId}
                                    onChange={(e) => setTestPermissionId(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg"
                                >
                                    <option value="">Select permission...</option>
                                    {permissions.map((perm) => (
                                        <option key={perm.id} value={perm.id}>
                                            {perm.id}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <Button variant="primary" size="md" onClick={handleTestPermission}>
                                🧪 Test Permission
                            </Button>

                            {testResult !== null && (
                                <div
                                    className={`
                    p-4 rounded-lg border-2
                    ${testResult.granted
                                            ? 'border-green-500 bg-green-50 dark:bg-green-900/20'
                                            : 'border-red-500 bg-red-50 dark:bg-red-900/20'
                                        }
                  `}
                                >
                                    <div className="flex items-center gap-2 mb-2">
                                        <span className="text-2xl">{testResult.granted ? '✅' : '❌'}</span>
                                        <div>
                                            <p className="font-semibold text-slate-900 dark:text-neutral-100">
                                                {testResult.granted ? 'Permission Granted' : 'Permission Denied'}
                                            </p>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400">
                                                User {testResult.granted ? 'has' : 'does not have'} permission {testPermissionId}
                                            </p>
                                        </div>
                                    </div>
                                    {testResult.matchedRoles.length > 0 && (
                                        <div className="mt-2 pt-2 border-t border-slate-200 dark:border-neutral-700">
                                            <p className="text-xs font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                                Granted via roles:
                                            </p>
                                            <div className="flex gap-1 flex-wrap">
                                                {testResult.matchedRoles.map((role) => (
                                                    <Chip key={role.roleName} label={role.roleName} size="small" color="success" />
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </Box>
                </Card>
            )}

            {/* Role Assignment Tab */}
            {activeTab === 'assignment' && (
                <Card>
                    <Box className="p-6">
                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                            Assign Roles to Users
                        </h3>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    User
                                </label>
                                <select
                                    value={assignUserId}
                                    onChange={(e) => setAssignUserId(e.target.value)}
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg"
                                >
                                    <option value="">Select user...</option>
                                    {users.map((user) => (
                                        <option key={user.id} value={user.id}>
                                            {user.name} ({user.email})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Roles
                                </label>
                                <div className="grid grid-cols-2 gap-2">
                                    {roles.map((role) => (
                                        <label
                                            key={role.id}
                                            className="flex items-center gap-2 p-2 rounded border border-slate-200 dark:border-neutral-700 cursor-pointer hover:border-blue-300"
                                        >
                                            <input
                                                type="checkbox"
                                                checked={assignRoleIds.has(role.id)}
                                                onChange={() => {
                                                    setAssignRoleIds((prev) => {
                                                        const next = new Set(prev);
                                                        if (next.has(role.id)) {
                                                            next.delete(role.id);
                                                        } else {
                                                            next.add(role.id);
                                                        }
                                                        return next;
                                                    });
                                                }}
                                                className="rounded"
                                            />
                                            <span className="text-sm text-slate-900 dark:text-neutral-100">
                                                {role.name}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            </div>

                            <Button variant="primary" size="md" onClick={handleAssignRoles}>
                                👥 Assign Roles
                            </Button>
                        </div>
                    </Box>
                </Card>
            )}
        </Box>
    );
}
