import { memo } from 'react';

/**
 * RBAC user access matrix and permission panel.
 *
 * <p><b>Purpose</b><br>
 * Displays user roles, permissions, and access controls.
 * Supports adding/removing users, updating roles, and managing permissions.
 *
 * <p><b>Features</b><br>
 * - User list with roles
 * - Permission matrix (role vs resource)
 * - Role-based access control (Admin, Operator, Viewer, Developer)
 * - Add/remove users
 * - Bulk permission changes
 * - Last activity tracking
 *
 * @doc.type component
 * @doc.purpose User access and RBAC panel
 * @doc.layer product
 * @doc.pattern Panel
 */

interface User {
    id: string;
    name: string;
    email: string;
    role: 'admin' | 'operator' | 'viewer' | 'developer';
    lastActive: string;
    status: 'active' | 'inactive';
}

interface Permission {
    resource: string;
    admin: boolean;
    operator: boolean;
    viewer: boolean;
    developer: boolean;
}

// Mock users - for production, would use useQuery to fetch from API
const users: User[] = [
    {
        id: 'user-1',
        name: 'Alice Johnson',
        email: 'alice@example.com',
        role: 'admin',
        lastActive: '2 min ago',
        status: 'active',
    },
    {
        id: 'user-2',
        name: 'Bob Smith',
        email: 'bob@example.com',
        role: 'operator',
        lastActive: '15 min ago',
        status: 'active',
    },
    {
        id: 'user-3',
        name: 'Carol Williams',
        email: 'carol@example.com',
        role: 'viewer',
        lastActive: '1 hour ago',
        status: 'active',
    },
    {
        id: 'user-4',
        name: 'David Brown',
        email: 'david@example.com',
        role: 'developer',
        lastActive: '3 hours ago',
        status: 'inactive',
    },
];

// Permission matrix - for production, would use useQuery to fetch from API
const permissions: Permission[] = [
    { resource: 'Workflows', admin: true, operator: true, viewer: true, developer: true },
    { resource: 'HITL Console', admin: true, operator: true, viewer: false, developer: false },
    { resource: 'Reporting', admin: true, operator: true, viewer: true, developer: false },
    { resource: 'Security Settings', admin: true, operator: false, viewer: false, developer: false },
    { resource: 'User Management', admin: true, operator: false, viewer: false, developer: false },
    { resource: 'Audit Logs', admin: true, operator: true, viewer: true, developer: false },
];

export const UserAccessPanel = memo(function UserAccessPanel() {
    // GIVEN: Security dashboard opened
    // WHEN: User views access control tab
    // THEN: Display user list and permission matrix

    const getRoleColor = (role: string) => {
        switch (role) {
            case 'admin':
                return 'bg-red-950 text-red-400';
            case 'operator':
                return 'bg-blue-950 text-blue-400';
            case 'viewer':
                return 'bg-green-950 text-green-400';
            case 'developer':
                return 'bg-purple-950 text-purple-400';
            default:
                return 'bg-slate-700 text-slate-400';
        }
    };

    const getStatusIcon = (status: string) => (status === 'active' ? '🟢' : '🔴');

    return (
        <div className="space-y-6">
            {/* Users Table */}
            <div>
                <div className="flex items-center justify-between mb-3">
                    <h2 className="text-lg font-semibold text-white">Users ({users.length})</h2>
                    <button className="px-3 py-1 text-sm bg-blue-600 hover:bg-blue-500 text-white rounded">
                        + Add User
                    </button>
                </div>

                <div className="overflow-x-auto border border-slate-700 rounded-lg">
                    <table className="w-full text-sm">
                        <thead className="bg-slate-800 border-b border-slate-700">
                            <tr>
                                <th className="px-4 py-2 text-left text-slate-400">Name</th>
                                <th className="px-4 py-2 text-left text-slate-400">Email</th>
                                <th className="px-4 py-2 text-left text-slate-400">Role</th>
                                <th className="px-4 py-2 text-left text-slate-400">Status</th>
                                <th className="px-4 py-2 text-left text-slate-400">Last Active</th>
                                <th className="px-4 py-2 text-center text-slate-400">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-700">
                            {users.map((user) => (
                                <tr key={user.id} className="hover:bg-slate-800 transition-colors">
                                    <td className="px-4 py-3 text-slate-200">{user.name}</td>
                                    <td className="px-4 py-3 text-slate-400 font-mono text-xs">{user.email}</td>
                                    <td className="px-4 py-3">
                                        <span className={`px-2 py-1 rounded text-xs font-medium ${getRoleColor(user.role)}`}>
                                            {user.role.toUpperCase()}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3">
                                        <span className="text-sm">
                                            {getStatusIcon(user.status)} {user.status}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3 text-slate-400 text-xs">{user.lastActive}</td>
                                    <td className="px-4 py-3 text-center">
                                        <button className="text-blue-400 hover:text-blue-300 text-xs">Edit</button>
                                        <span className="text-slate-600 mx-2">•</span>
                                        <button className="text-red-400 hover:text-red-300 text-xs">Remove</button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Permission Matrix */}
            <div>
                <h2 className="text-lg font-semibold text-white mb-3">Permission Matrix</h2>

                <div className="overflow-x-auto border border-slate-700 rounded-lg">
                    <table className="w-full text-sm">
                        <thead className="bg-slate-800 border-b border-slate-700">
                            <tr>
                                <th className="px-4 py-2 text-left text-slate-400">Resource</th>
                                <th className="px-4 py-2 text-center text-slate-400">Admin</th>
                                <th className="px-4 py-2 text-center text-slate-400">Operator</th>
                                <th className="px-4 py-2 text-center text-slate-400">Viewer</th>
                                <th className="px-4 py-2 text-center text-slate-400">Developer</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-700">
                            {permissions.map((perm) => (
                                <tr key={perm.resource} className="hover:bg-slate-800 transition-colors">
                                    <td className="px-4 py-3 text-slate-200 font-medium">{perm.resource}</td>
                                    <td className="px-4 py-3 text-center">
                                        <input
                                            type="checkbox"
                                            checked={perm.admin}
                                            className="w-4 h-4 rounded border-slate-500 bg-slate-700"
                                            readOnly
                                        />
                                    </td>
                                    <td className="px-4 py-3 text-center">
                                        <input
                                            type="checkbox"
                                            checked={perm.operator}
                                            className="w-4 h-4 rounded border-slate-500 bg-slate-700"
                                            readOnly
                                        />
                                    </td>
                                    <td className="px-4 py-3 text-center">
                                        <input
                                            type="checkbox"
                                            checked={perm.viewer}
                                            className="w-4 h-4 rounded border-slate-500 bg-slate-700"
                                            readOnly
                                        />
                                    </td>
                                    <td className="px-4 py-3 text-center">
                                        <input
                                            type="checkbox"
                                            checked={perm.developer}
                                            className="w-4 h-4 rounded border-slate-500 bg-slate-700"
                                            readOnly
                                        />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Session Info */}
            <div className="bg-slate-800 rounded-lg p-4">
                <h3 className="font-semibold text-white mb-3">Active Sessions</h3>
                <div className="space-y-2 text-sm">
                    <div className="flex items-center justify-between text-slate-300">
                        <span>alice@example.com</span>
                        <span className="text-xs text-slate-500">Last seen: 2 min ago</span>
                    </div>
                    <div className="flex items-center justify-between text-slate-300">
                        <span>bob@example.com</span>
                        <span className="text-xs text-slate-500">Last seen: 15 min ago</span>
                    </div>
                </div>
            </div>
        </div>
    );
});

export default UserAccessPanel;
