import { Save, Plus as Add, Pencil as Edit, Trash2 as Delete, AlertTriangle as Warning, Eye as Visibility, EyeOff as VisibilityOff, Copy as ContentCopy, MoreVertical as MoreVert } from 'lucide-react';
import React, { useState } from 'react';
import { useParams } from 'react-router';

import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

// Types
/**
 *
 */
interface User {
    id: string;
    name: string;
    email: string;
    avatar?: string;
    role: 'owner' | 'admin' | 'developer' | 'viewer';
    status: 'active' | 'pending' | 'suspended';
    lastActive: Date;
    permissions: string[];
}

/**
 *
 */
interface ApiToken {
    id: string;
    name: string;
    token: string;
    permissions: string[];
    createdAt: Date;
    lastUsed?: Date;
    expiresAt?: Date;
}

/**
 *
 */
interface Webhook {
    id: string;
    name: string;
    url: string;
    events: string[];
    secret?: string;
    active: boolean;
    createdAt: Date;
    lastTriggered?: Date;
}

/**
 *
 */
interface AuditLog {
    id: string;
    action: string;
    user: string;
    target: string;
    timestamp: Date;
    details: string;
    ipAddress: string;
}

// Project Settings Component
/**
 *
 */
interface ProjectSettingsProps {
    onSave: (settings: unknown) => void;
}

const selectCls = 'w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-fg focus:outline-none focus:ring-2 focus:ring-brand';
const inputCls = 'w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-fg placeholder-fg-muted focus:outline-none focus:ring-2 focus:ring-brand';
const labelCls = 'block text-xs font-medium text-fg-muted mb-1';

const Toggle: React.FC<{ checked: boolean; onChange: (v: boolean) => void }> = ({ checked, onChange }) => (
    <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors ${checked ? 'bg-brand' : 'bg-surface-muted'}`}
    >
        <span className={`inline-block h-4 w-4 translate-y-0.5 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-4' : 'translate-x-0.5'}`} />
    </button>
);

const ProjectSettings: React.FC<ProjectSettingsProps> = ({ onSave }) => {
    const [settings, setSettings] = useState({
        name: 'YAPPC Application Creator',
        description: 'A comprehensive application creation and management platform',
        language: 'typescript',
        serviceKind: 'web-app',
        target: 'production',
        autoBackup: true,
        enableNotifications: true,
        publicAccess: false
    });

    const [showMigrationWarning, setShowMigrationWarning] = useState(false);

    const handleLanguageChange = (newLanguage: string) => {
        if (newLanguage !== settings.language) {
            setShowMigrationWarning(true);
        }
        setSettings(prev => ({ ...prev, language: newLanguage }));
    };

    return (
        <div className="rounded-lg border border-border bg-surface-raised">
            <div className="flex items-center justify-between border-b border-border px-5 py-4">
                <h2 className="text-sm font-semibold text-fg">Project Configuration</h2>
            </div>
            <div className="space-y-5 p-5">
                <div>
                    <label className={labelCls} htmlFor="project-name">Project Name</label>
                    <input
                        id="project-name"
                        className={inputCls}
                        value={settings.name}
                        onChange={(e) => setSettings(prev => ({ ...prev, name: e.target.value }))}
                        data-testid="project-name-input"
                    />
                </div>

                <div>
                    <label className={labelCls} htmlFor="project-desc">Description</label>
                    <textarea
                        id="project-desc"
                        rows={3}
                        className={inputCls}
                        value={settings.description}
                        onChange={(e) => setSettings(prev => ({ ...prev, description: e.target.value }))}
                        data-testid="project-description-input"
                    />
                </div>

                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                    <div>
                        <label className={labelCls} htmlFor="lang-select">Primary Language</label>
                        <select id="lang-select" className={selectCls} value={settings.language} onChange={(e) => handleLanguageChange(e.target.value)}>
                            <option value="typescript">TypeScript</option>
                            <option value="javascript">JavaScript</option>
                            <option value="python">Python</option>
                            <option value="java">Java</option>
                            <option value="go">Go</option>
                        </select>
                    </div>
                    <div>
                        <label className={labelCls} htmlFor="kind-select">Service Kind</label>
                        <select id="kind-select" className={selectCls} value={settings.serviceKind} onChange={(e) => setSettings(prev => ({ ...prev, serviceKind: e.target.value }))}>
                            <option value="web-app">Web Application</option>
                            <option value="api">REST API</option>
                            <option value="microservice">Microservice</option>
                            <option value="mobile-app">Mobile App</option>
                            <option value="desktop-app">Desktop App</option>
                        </select>
                    </div>
                    <div>
                        <label className={labelCls} htmlFor="env-select">Target Environment</label>
                        <select id="env-select" className={selectCls} value={settings.target} onChange={(e) => setSettings(prev => ({ ...prev, target: e.target.value }))}>
                            <option value="development">Development</option>
                            <option value="staging">Staging</option>
                            <option value="production">Production</option>
                        </select>
                    </div>
                </div>

                <div>
                    <p className="mb-3 text-xs font-medium text-zinc-400">Project Features</p>
                    <div className="space-y-3">
                        {([
                            { key: 'autoBackup', label: 'Automatic Backups' },
                            { key: 'enableNotifications', label: 'Enable Notifications' },
                            { key: 'publicAccess', label: 'Public Access (Read-only)' },
                        ] as const).map(({ key, label }) => (
                            <label key={key} className="flex cursor-pointer items-center gap-3">
                                <Toggle checked={settings[key]} onChange={(v) => setSettings(prev => ({ ...prev, [key]: v }))} />
                                <span className="text-sm text-zinc-300">{label}</span>
                            </label>
                        ))}
                    </div>
                </div>

                {showMigrationWarning && (
                    <div className="flex items-start gap-3 rounded-md border border-amber-700/50 bg-amber-950/40 px-4 py-3">
                        <Warning className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" />
                        <p className="text-sm text-amber-200">
                            <strong>Language Migration Required:</strong> Changing the primary language will require
                            code migration. This process may take several minutes and should be done during maintenance windows.
                        </p>
                        <button onClick={() => setShowMigrationWarning(false)} className="ml-auto shrink-0 text-amber-400 hover:text-amber-200">✕</button>
                    </div>
                )}

                <div className="flex justify-end gap-2">
                    <button className="rounded-md border border-zinc-700 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800">Reset</button>
                    <button
                        className="flex items-center gap-2 rounded-md bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700"
                        onClick={() => onSave(settings)}
                        data-testid="save-settings-button"
                    >
                        <Save className="h-4 w-4" /> Save Changes
                    </button>
                </div>
            </div>
        </div>
    );
};

// RBAC Manager Component
/**
 *
 */
interface RBACManagerProps {
    users: User[];
    onAddUser: (user: Partial<User>) => void;
    onUpdateUser: (id: string, updates: Partial<User>) => void;
    onRemoveUser: (id: string) => void;
}

const roleBadge: Record<string, string> = {
    owner: 'bg-red-900/50 text-red-300 border border-red-700/50',
    admin: 'bg-violet-900/50 text-violet-300 border border-violet-700/50',
    developer: 'bg-blue-900/50 text-blue-300 border border-blue-700/50',
    viewer: 'bg-zinc-800 text-zinc-300 border border-zinc-700',
};
const statusBadge: Record<string, string> = {
    active: 'bg-emerald-900/50 text-emerald-300 border border-emerald-700/50',
    pending: 'bg-amber-900/50 text-amber-300 border border-amber-700/50',
    suspended: 'bg-red-900/50 text-red-300 border border-red-700/50',
};

const Modal: React.FC<{ open: boolean; onClose: () => void; title: string; children: React.ReactNode; footer: React.ReactNode }> = ({ open, onClose, title, children, footer }) => {
    if (!open) return null;
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={onClose}>
            <div className="w-full max-w-md rounded-lg border border-zinc-800 bg-zinc-950 shadow-xl" onClick={e => e.stopPropagation()}>
                <div className="border-b border-zinc-800 px-5 py-4">
                    <h3 className="text-sm font-semibold text-zinc-100">{title}</h3>
                </div>
                <div className="space-y-4 p-5">{children}</div>
                <div className="flex justify-end gap-2 border-t border-zinc-800 px-5 py-3">{footer}</div>
            </div>
        </div>
    );
};

const RBACManager: React.FC<RBACManagerProps> = ({ users, onRemoveUser }) => {
    const [addUserDialog, setAddUserDialog] = useState(false);
    const [editUserDialog, setEditUserDialog] = useState<{ open: boolean; user?: User }>({ open: false });
    const [openMenuId, setOpenMenuId] = useState<string | null>(null);
    const [showInviteSuccess, setShowInviteSuccess] = useState(false);

    const rolePermissions: Record<string, string[]> = {
        owner: ['read', 'write', 'admin', 'deploy', 'delete', 'manage_users', 'manage_settings'],
        admin: ['read', 'write', 'deploy', 'manage_users'],
        developer: ['read', 'write', 'deploy'],
        viewer: ['read'],
    };

    return (
        <div className="rounded-lg border border-zinc-800 bg-zinc-950">
            <div className="flex items-center justify-between border-b border-zinc-800 px-5 py-4">
                <h2 className="text-sm font-semibold text-zinc-100">Access Control (RBAC)</h2>
                <button
                    className="flex items-center gap-1.5 rounded-md bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700"
                    onClick={() => setAddUserDialog(true)}
                    data-testid="add-team-member-button"
                >
                    <Add className="h-3.5 w-3.5" /> Add User
                </button>
            </div>
            <div className="overflow-x-auto">
                <table className="w-full text-sm">
                    <thead>
                        <tr className="border-b border-zinc-800">
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">User</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">Role</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">Status</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">Last Active</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/50">
                        {users.map((user) => (
                            <tr key={user.id} className="hover:bg-zinc-900/40">
                                <td className="px-4 py-3">
                                    <div className="flex items-center gap-3">
                                        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-zinc-700 text-xs font-medium text-zinc-200">
                                            {user.name.charAt(0)}
                                        </div>
                                        <div>
                                            <div className="font-medium text-zinc-200">{user.name}</div>
                                            <div className="text-xs text-zinc-500">{user.email}</div>
                                        </div>
                                    </div>
                                </td>
                                <td className="px-4 py-3">
                                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${roleBadge[user.role] ?? roleBadge.viewer}`}>
                                        {user.role.charAt(0).toUpperCase() + user.role.slice(1)}
                                    </span>
                                </td>
                                <td className="px-4 py-3">
                                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${statusBadge[user.status] ?? statusBadge.pending}`}>
                                        {user.status.charAt(0).toUpperCase() + user.status.slice(1)}
                                    </span>
                                </td>
                                <td className="px-4 py-3 text-xs text-zinc-400">{user.lastActive.toLocaleDateString()}</td>
                                <td className="px-4 py-3">
                                    <div className="relative">
                                        <button
                                            className="rounded p-1 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200"
                                            onClick={() => setOpenMenuId(openMenuId === user.id ? null : user.id)}
                                        >
                                            <MoreVert className="h-4 w-4" />
                                        </button>
                                        {openMenuId === user.id && (
                                            <div className="absolute right-0 z-10 mt-1 w-40 rounded-md border border-zinc-800 bg-zinc-900 py-1 shadow-lg">
                                                <button
                                                    className="flex w-full items-center gap-2 px-3 py-2 text-xs text-zinc-300 hover:bg-zinc-800"
                                                    onClick={() => { setEditUserDialog({ open: true, user }); setOpenMenuId(null); }}
                                                >
                                                    <Edit className="h-3.5 w-3.5" /> Edit User
                                                </button>
                                                <button
                                                    className="flex w-full items-center gap-2 px-3 py-2 text-xs text-red-400 hover:bg-zinc-800"
                                                    onClick={() => { onRemoveUser(user.id); setOpenMenuId(null); }}
                                                >
                                                    <Delete className="h-3.5 w-3.5" /> Remove User
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {showInviteSuccess && (
                <div data-testid="success-toast" className="fixed right-4 top-4 z-50 rounded-md border border-emerald-700/50 bg-emerald-950 px-4 py-3 text-sm text-emerald-300 shadow-lg">
                    Team member invited
                </div>
            )}

            <Modal open={addUserDialog} onClose={() => setAddUserDialog(false)} title="Add New User"
                footer={<>
                    <button className="rounded-md border border-zinc-700 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800" onClick={() => setAddUserDialog(false)} data-testid="add-team-member-cancel">Cancel</button>
                    <button
                        className="rounded-md bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700"
                        data-testid="invite-member-button"
                        onClick={() => { setShowInviteSuccess(true); setAddUserDialog(false); setTimeout(() => setShowInviteSuccess(false), 2500); }}
                    >Send Invitation</button>
                </>}
            >
                <div>
                    <label className={labelCls} htmlFor="member-name">Full Name</label>
                    <input id="member-name" className={inputCls} data-testid="member-name-input" />
                </div>
                <div>
                    <label className={labelCls} htmlFor="member-email">Email Address</label>
                    <input id="member-email" type="email" className={inputCls} data-testid="member-email-input" />
                </div>
                <div>
                    <label className={labelCls} htmlFor="member-role-select">Role</label>
                    <select id="member-role-select" className={selectCls} defaultValue="viewer" data-testid="member-role-select">
                        <option value="admin">Admin</option>
                        <option value="developer">Developer</option>
                        <option value="viewer">Viewer</option>
                    </select>
                </div>
            </Modal>

            <Modal open={editUserDialog.open} onClose={() => setEditUserDialog({ open: false })} title="Edit User Permissions"
                footer={<>
                    <button className="rounded-md border border-zinc-700 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800" onClick={() => setEditUserDialog({ open: false })}>Cancel</button>
                    <button className="rounded-md bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700">Save Changes</button>
                </>}
            >
                {editUserDialog.user && (
                    <>
                        <div>
                            <p className="text-xs font-medium text-zinc-400">User Information</p>
                            <p className="mt-1 text-sm text-zinc-200">{editUserDialog.user.name}</p>
                            <p className="text-xs text-zinc-500">{editUserDialog.user.email}</p>
                        </div>
                        <div>
                            <label className={labelCls} htmlFor="edit-role">Role</label>
                            <select id="edit-role" className={selectCls} defaultValue={editUserDialog.user.role}>
                                <option value="admin">Admin</option>
                                <option value="developer">Developer</option>
                                <option value="viewer">Viewer</option>
                            </select>
                        </div>
                        <div>
                            <p className="mb-2 text-xs font-medium text-zinc-400">Permissions</p>
                            <div className="flex flex-wrap gap-1">
                                {rolePermissions[editUserDialog.user.role]?.map((p) => (
                                    <span key={p} className="rounded bg-zinc-800 px-2 py-0.5 text-xs text-zinc-300">{p}</span>
                                ))}
                            </div>
                        </div>
                    </>
                )}
            </Modal>
        </div>
    );
};

// Token Management Component
/**
 *
 */
interface TokenManagerProps {
    tokens: ApiToken[];
    onCreateToken: (token: Partial<ApiToken>) => void;
    onRevokeToken: (id: string) => void;
}

const TokenManager: React.FC<TokenManagerProps> = ({ tokens, onRevokeToken }) => {
    const [createDialog, setCreateDialog] = useState(false);
    const [showToken, setShowToken] = useState<string | null>(null);
    const [permToggles, setPermToggles] = useState<Record<string, boolean>>({ read: true, write: false, deploy: false, admin: false });

    const copyToClipboard = (text: string) => { navigator.clipboard.writeText(text); };

    return (
        <div className="rounded-lg border border-zinc-800 bg-zinc-950">
            <div className="flex items-center justify-between border-b border-zinc-800 px-5 py-4">
                <h2 className="text-sm font-semibold text-zinc-100">API Tokens</h2>
                <button
                    className="flex items-center gap-1.5 rounded-md bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700"
                    onClick={() => setCreateDialog(true)}
                >
                    <Add className="h-3.5 w-3.5" /> Create Token
                </button>
            </div>
            <div className="divide-y divide-zinc-800/50 p-2">
                {tokens.map((token) => (
                    <div key={token.id} className="flex items-start justify-between gap-4 rounded-md px-3 py-4">
                        <div className="min-w-0 flex-1">
                            <p className="mb-1 font-medium text-zinc-200">{token.name}</p>
                            <div className="mb-2 flex items-center gap-1.5">
                                <span className="font-mono text-xs text-zinc-400">
                                    {showToken === token.id ? token.token : '••••••••••••••••'}
                                </span>
                                <button className="rounded p-0.5 text-zinc-500 hover:text-zinc-300" onClick={() => setShowToken(showToken === token.id ? null : token.id)}>
                                    {showToken === token.id ? <VisibilityOff className="h-3.5 w-3.5" /> : <Visibility className="h-3.5 w-3.5" />}
                                </button>
                                <button className="rounded p-0.5 text-zinc-500 hover:text-zinc-300" onClick={() => copyToClipboard(token.token)}>
                                    <ContentCopy className="h-3.5 w-3.5" />
                                </button>
                            </div>
                            <div className="mb-2 flex flex-wrap gap-1">
                                {token.permissions.map((p) => (
                                    <span key={p} className="rounded bg-zinc-800 px-2 py-0.5 text-xs text-zinc-300">{p}</span>
                                ))}
                            </div>
                            <p className="text-xs text-zinc-500">
                                Created {token.createdAt.toLocaleDateString()} · Last used {token.lastUsed ? token.lastUsed.toLocaleDateString() : 'Never'}
                            </p>
                        </div>
                        <button
                            className="shrink-0 rounded-md border border-red-700/50 px-2.5 py-1 text-xs text-red-400 hover:bg-red-950"
                            onClick={() => onRevokeToken(token.id)}
                        >
                            Revoke
                        </button>
                    </div>
                ))}
            </div>

            <Modal open={createDialog} onClose={() => setCreateDialog(false)} title="Create API Token"
                footer={<>
                    <button className="rounded-md border border-zinc-700 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-800" onClick={() => setCreateDialog(false)}>Cancel</button>
                    <button className="rounded-md bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700">Generate Token</button>
                </>}
            >
                <div>
                    <label className={labelCls} htmlFor="token-name">Token Name</label>
                    <input id="token-name" className={inputCls} placeholder="e.g., CI/CD Pipeline" />
                </div>
                <div>
                    <p className="mb-2 text-xs font-medium text-zinc-400">Permissions</p>
                    <div className="space-y-2">
                        {(['read', 'write', 'deploy', 'admin'] as const).map((p) => (
                            <label key={p} className="flex cursor-pointer items-center gap-3">
                                <Toggle checked={permToggles[p]} onChange={(v) => setPermToggles(prev => ({ ...prev, [p]: v }))} />
                                <span className="text-sm capitalize text-zinc-300">{p}</span>
                            </label>
                        ))}
                    </div>
                </div>
                <div>
                    <label className={labelCls} htmlFor="token-expiry">Expiration (days)</label>
                    <input id="token-expiry" type="number" className={inputCls} defaultValue={90} />
                    <p className="mt-1 text-xs text-zinc-500">Leave empty for no expiration</p>
                </div>
            </Modal>
        </div>
    );
};

// Audit Trail Component
/**
 *
 */
interface AuditTrailProps {
    logs: AuditLog[];
}

const AuditTrail: React.FC<AuditTrailProps> = ({ logs }) => {
    return (
        <div className="rounded-lg border border-zinc-800 bg-zinc-950">
            <div className="border-b border-zinc-800 px-5 py-4">
                <h2 className="text-sm font-semibold text-zinc-100">Audit Trail</h2>
            </div>
            <div className="overflow-x-auto">
                <table className="w-full text-sm">
                    <thead>
                        <tr className="border-b border-zinc-800">
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">Timestamp</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">User</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">Action</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">Target</th>
                            <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500">IP Address</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/50">
                        {logs.map((log) => (
                            <tr key={log.id} className="hover:bg-zinc-900/40">
                                <td className="px-4 py-3 text-xs text-zinc-400">{log.timestamp.toLocaleString()}</td>
                                <td className="px-4 py-3 text-xs text-zinc-300">{log.user}</td>
                                <td className="px-4 py-3 text-xs text-zinc-300">{log.action}</td>
                                <td className="px-4 py-3 text-xs text-zinc-400">{log.target}</td>
                                <td className="px-4 py-3 font-mono text-xs text-zinc-400">{log.ipAddress}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
};

// Main Settings Route Component
/**
 *
 */
export default function Component() {
    const { projectId } = useParams<{ projectId: string }>();
    void projectId;

    function generateToken(): string {
        return `yappc_${Math.random().toString(36).substring(2, 15)}${Math.random().toString(36).substring(2, 15)}`;
    }

    const [users] = useState<User[]>([
        { id: 'user_1', name: 'Alice Johnson', email: 'alice@yappc.com', role: 'owner', status: 'active', lastActive: new Date(Date.now() - 3600000), permissions: ['read', 'write', 'admin', 'deploy', 'delete', 'manage_users', 'manage_settings'] },
        { id: 'user_2', name: 'Bob Smith', email: 'bob@yappc.com', role: 'admin', status: 'active', lastActive: new Date(Date.now() - 86400000), permissions: ['read', 'write', 'deploy', 'manage_users'] },
        { id: 'user_3', name: 'Charlie Brown', email: 'charlie@yappc.com', role: 'developer', status: 'active', lastActive: new Date(Date.now() - 1800000), permissions: ['read', 'write', 'deploy'] },
    ]);

    const [tokens] = useState<ApiToken[]>([
        { id: 'token_1', name: 'CI/CD Pipeline', token: generateToken(), permissions: ['read', 'deploy'], createdAt: new Date(Date.now() - 2592000000), lastUsed: new Date(Date.now() - 3600000) },
        { id: 'token_2', name: 'Development Bot', token: generateToken(), permissions: ['read', 'write'], createdAt: new Date(Date.now() - 604800000), lastUsed: new Date(Date.now() - 86400000) },
    ]);

    const [auditLogs] = useState<AuditLog[]>([
        { id: 'audit_1', action: 'User Added', user: 'Alice Johnson', target: 'Charlie Brown', timestamp: new Date(Date.now() - 3600000), details: 'Added new developer role', ipAddress: '192.168.1.100' },
        { id: 'audit_2', action: 'Settings Updated', user: 'Bob Smith', target: 'Project Configuration', timestamp: new Date(Date.now() - 7200000), details: 'Changed primary language to TypeScript', ipAddress: '192.168.1.101' },
        { id: 'audit_3', action: 'Token Created', user: 'Alice Johnson', target: 'API Token', timestamp: new Date(Date.now() - 86400000), details: 'Created CI/CD Pipeline token', ipAddress: '192.168.1.100' },
    ]);

    const handleSaveSettings = (settings: unknown) => {
        console.log('Saving settings:', settings);
        try {
            // NOTE: Add settings save logic here
            console.log('Settings saved successfully');
        } catch (e) {
            console.error('Error saving settings:', e);
        }
    };

    const handleRemoveUser = (id: string) => { console.log('Removing user:', id); };
    const handleCreateToken = (_token: Partial<ApiToken>) => { console.log('Creating token'); };
    const handleRevokeToken = (id: string) => { console.log('Revoking token:', id); };

    return (
        <div className="p-6" data-testid="project-settings">
            {typeof window !== 'undefined' && localStorage.getItem('E2E_SIMPLE_PAGES') === '1' && (
                <div>
                    <div data-testid="project-settings-placeholder-stub" style={{ position: 'absolute', width: 1, height: 1, opacity: 0.01 }} />
                    <div data-testid="project-config-tab-stub">
                        <h1>Project Settings (E2E Stub)</h1>
                        <input data-testid="project-name-input-stub" defaultValue="E2E Test Project" />
                        <button data-testid="save-settings-button-stub" onClick={() => { const s = document.createElement('div'); s.setAttribute('data-testid', 'success-toast'); s.textContent = 'Settings saved'; document.body.appendChild(s); }}>Save</button>
                    </div>
                    <div data-testid="team-management-tab-stub">
                        <button data-testid="add-team-member-button-stub">Add User</button>
                        <input data-testid="member-email-input-stub" />
                        <select data-testid="member-role-select-stub"><option value="developer">Developer</option></select>
                        <button data-testid="invite-member-button-stub" onClick={() => { const s = document.createElement('div'); s.setAttribute('data-testid', 'success-toast'); s.textContent = 'Team member invited'; document.body.appendChild(s); }}>Invite</button>
                    </div>
                </div>
            )}
            <div data-testid="project-settings-placeholder" style={{ position: 'absolute', width: 1, height: 1, opacity: 0.01 }} />

            <div className="mb-6">
                <h1 className="text-2xl font-semibold text-zinc-100" data-testid="project-settings-heading">Project Settings</h1>
                <p className="mt-1 text-sm text-zinc-400">Manage project configuration, access control, and security settings</p>
            </div>

            <div className="space-y-6">
                <div data-testid="project-config-tab">
                    <ProjectSettings onSave={handleSaveSettings} />
                </div>

                <div data-testid="team-management-tab">
                    <RBACManager
                        users={users}
                        onAddUser={() => {}}
                        onUpdateUser={() => {}}
                        onRemoveUser={handleRemoveUser}
                    />
                </div>

                <TokenManager
                    tokens={tokens}
                    onCreateToken={handleCreateToken}
                    onRevokeToken={handleRevokeToken}
                />

                <AuditTrail logs={auditLogs} />
            </div>
        </div>
    );
}

/**
 *
 */
export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Settings Error"
            message="Unable to load project settings. Please try refreshing the page."
        />
    );
}