/**
 * Settings Route
 *
 * Workspace-level settings: general info, notifications, billing (placeholder),
 * and danger zone (delete workspace).
 *
 * @doc.type route
 * @doc.purpose Workspace settings management
 * @doc.layer product
 * @doc.pattern Route Module
 */

import { useState, useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { Settings, Bell, CreditCard, AlertTriangle, Save } from 'lucide-react';
import { currentWorkspaceIdAtom } from '../state/atoms/workspaceAtom';
import { useCurrentUser } from '../providers/AuthProvider';
import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

interface WorkspaceSettings {
    id: string;
    name: string;
    slug: string;
    description?: string;
    notifyOnNewMember?: boolean;
    notifyOnProjectCreate?: boolean;
    notifyOnDeployment?: boolean;
}

interface SettingsFormState {
    name: string;
    slug: string;
    description: string;
    notifyOnNewMember: boolean;
    notifyOnProjectCreate: boolean;
    notifyOnDeployment: boolean;
}

const inputCls =
    'w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg placeholder-fg-muted focus:outline-none focus:ring-2 focus:ring-brand';
const labelCls = 'block text-xs font-medium text-fg-muted mb-1';

type Tab = 'general' | 'notifications' | 'billing' | 'danger';

const tabs: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: 'general', label: 'General', icon: <Settings className="h-4 w-4" /> },
    { id: 'notifications', label: 'Notifications', icon: <Bell className="h-4 w-4" /> },
    { id: 'billing', label: 'Billing', icon: <CreditCard className="h-4 w-4" /> },
    { id: 'danger', label: 'Danger Zone', icon: <AlertTriangle className="h-4 w-4" /> },
];

const Toggle: React.FC<{ checked: boolean; onChange: (v: boolean) => void; id: string }> = ({ checked, onChange, id }) => (
    <button
        type="button"
        role="switch"
        id={id}
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors ${checked ? 'bg-brand' : 'bg-surface-muted'}`}
    >
        <span className={`inline-block h-4 w-4 translate-y-0.5 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-4' : 'translate-x-0.5'}`} />
    </button>
);

/**
 * Settings Page Component
 */
export default function Component() {
    const workspaceId = useAtomValue(currentWorkspaceIdAtom);
    const currentUser = useCurrentUser();
    const queryClient = useQueryClient();
    const [activeTab, setActiveTab] = useState<Tab>('general');
    const [saveSuccess, setSaveSuccess] = useState(false);
    const [deleteConfirm, setDeleteConfirm] = useState('');

    // ── Fetch workspace settings ───────────────────────────────────────────────
    const { data: workspace, isLoading } = useQuery<WorkspaceSettings>({
        queryKey: ['workspace-settings', workspaceId],
        queryFn: async () => {
            const res = await fetch(`/api/workspaces/${workspaceId}`);
            if (!res.ok) throw new Error('Failed to load workspace settings');
            return res.json() as Promise<WorkspaceSettings>;
        },
        enabled: !!workspaceId && currentUser.isAuthenticated,
    });

    // ── Form state ─────────────────────────────────────────────────────────────
    const [form, setForm] = useState<SettingsFormState>({
        name: '',
        slug: '',
        description: '',
        notifyOnNewMember: true,
        notifyOnProjectCreate: true,
        notifyOnDeployment: false,
    });

    useEffect(() => {
        if (workspace) {
            setForm({
                name: workspace.name ?? '',
                slug: workspace.slug ?? '',
                description: workspace.description ?? '',
                notifyOnNewMember: workspace.notifyOnNewMember ?? true,
                notifyOnProjectCreate: workspace.notifyOnProjectCreate ?? true,
                notifyOnDeployment: workspace.notifyOnDeployment ?? false,
            });
        }
    }, [workspace]);

    // ── Save settings ──────────────────────────────────────────────────────────
    const saveMutation = useMutation({
        mutationFn: async (data: SettingsFormState) => {
            const res = await fetch(`/api/workspaces/${workspaceId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
            });
            if (!res.ok) throw new Error('Failed to save settings');
            return res.json();
        },
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['workspace-settings', workspaceId] });
            setSaveSuccess(true);
            setTimeout(() => setSaveSuccess(false), 3000);
        },
    });

    // ── Delete workspace ───────────────────────────────────────────────────────
    const deleteMutation = useMutation({
        mutationFn: async () => {
            const res = await fetch(`/api/workspaces/${workspaceId}`, { method: 'DELETE' });
            if (!res.ok) throw new Error('Failed to delete workspace');
        },
        onSuccess: () => {
            window.location.href = '/';
        },
    });

    if (!currentUser.isAuthenticated) {
        return (
            <div className="flex items-center justify-center min-h-[60vh]">
                <p className="text-fg-muted">Please log in to manage settings.</p>
            </div>
        );
    }

    if (isLoading) {
        return (
            <div className="p-6 max-w-3xl mx-auto">
                <div className="animate-pulse space-y-4">
                    <div className="h-8 w-48 rounded bg-surface-muted" />
                    <div className="h-12 rounded-lg bg-surface-muted" />
                    <div className="h-48 rounded-xl bg-surface-muted" />
                </div>
            </div>
        );
    }

    return (
        <div className="p-6 max-w-3xl mx-auto">
            {/* Page header */}
            <div className="mb-6">
                <h1 className="text-2xl font-semibold text-fg">Workspace Settings</h1>
                <p className="mt-1 text-sm text-fg-muted">
                    Configure your workspace preferences and integrations.
                </p>
            </div>

            {/* Tab nav */}
            <div className="flex gap-1 border-b border-border mb-6">
                {tabs.map(tab => (
                    <button
                        key={tab.id}
                        onClick={() => setActiveTab(tab.id)}
                        className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium rounded-t-lg border-b-2 transition-colors
                            ${activeTab === tab.id
                                ? 'border-brand text-brand'
                                : 'border-transparent text-fg-muted hover:text-fg hover:border-border'
                            }
                            ${tab.id === 'danger' ? 'text-error-500 hover:text-error-600' : ''}
                        `}
                    >
                        {tab.icon}
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* ── General Tab ──────────────────────────────────────────────────── */}
            {activeTab === 'general' && (
                <div className="rounded-xl border border-border bg-surface-raised shadow-sm">
                    <div className="space-y-4 p-6">
                        <div>
                            <label className={labelCls} htmlFor="ws-name">Workspace Name</label>
                            <input
                                id="ws-name"
                                className={inputCls}
                                value={form.name}
                                onChange={e => setForm(prev => ({ ...prev, name: e.target.value }))}
                                placeholder="My Workspace"
                            />
                        </div>

                        <div>
                            <label className={labelCls} htmlFor="ws-slug">Workspace Slug</label>
                            <div className="flex items-center">
                                <span className="rounded-l-lg border border-r-0 border-border bg-surface-muted px-3 py-2 text-sm text-fg-muted">
                                    yappc.io/
                                </span>
                                <input
                                    id="ws-slug"
                                    className={`${inputCls} rounded-l-none`}
                                    value={form.slug}
                                    onChange={e => setForm(prev => ({ ...prev, slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-') }))}
                                    placeholder="my-workspace"
                                />
                            </div>
                        </div>

                        <div>
                            <label className={labelCls} htmlFor="ws-desc">Description</label>
                            <textarea
                                id="ws-desc"
                                rows={3}
                                className={inputCls}
                                value={form.description}
                                onChange={e => setForm(prev => ({ ...prev, description: e.target.value }))}
                                placeholder="What is this workspace for?"
                            />
                        </div>

                        {saveMutation.isError && (
                            <p className="text-sm text-error-600" role="alert">
                                Failed to save. Please try again.
                            </p>
                        )}

                        {saveSuccess && (
                            <p className="text-sm text-success-600" role="status">
                                Settings saved successfully.
                            </p>
                        )}
                    </div>

                    <div className="flex justify-end gap-2 border-t border-border px-6 py-4">
                        <button
                            className="rounded-lg border border-border px-4 py-2 text-sm text-fg-muted hover:bg-surface-muted transition-colors"
                            onClick={() => workspace && setForm({
                                name: workspace.name ?? '',
                                slug: workspace.slug ?? '',
                                description: workspace.description ?? '',
                                notifyOnNewMember: workspace.notifyOnNewMember ?? true,
                                notifyOnProjectCreate: workspace.notifyOnProjectCreate ?? true,
                                notifyOnDeployment: workspace.notifyOnDeployment ?? false,
                            })}
                        >
                            Discard
                        </button>
                        <button
                            className="flex items-center gap-2 rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark transition-colors disabled:opacity-60"
                            onClick={() => saveMutation.mutate(form)}
                            disabled={saveMutation.isPending}
                        >
                            <Save className="h-4 w-4" />
                            {saveMutation.isPending ? 'Saving…' : 'Save Changes'}
                        </button>
                    </div>
                </div>
            )}

            {/* ── Notifications Tab ─────────────────────────────────────────────── */}
            {activeTab === 'notifications' && (
                <div className="rounded-xl border border-border bg-surface-raised shadow-sm">
                    <div className="space-y-5 p-6">
                        <p className="text-sm text-fg-muted">
                            Choose which events send email notifications to workspace members.
                        </p>

                        {([
                            { key: 'notifyOnNewMember' as const, label: 'New member joins workspace', description: 'Notify admins when someone accepts an invitation.' },
                            { key: 'notifyOnProjectCreate' as const, label: 'Project created', description: 'Notify relevant members when a new project is added.' },
                            { key: 'notifyOnDeployment' as const, label: 'Deployment events', description: 'Notify on successful or failed deployments.' },
                        ]).map(({ key, label, description }) => (
                            <label key={key} className="flex items-start gap-4 cursor-pointer">
                                <Toggle
                                    id={`notify-${key}`}
                                    checked={form[key]}
                                    onChange={v => setForm(prev => ({ ...prev, [key]: v }))}
                                />
                                <div>
                                    <p className="text-sm font-medium text-fg">{label}</p>
                                    <p className="text-xs text-fg-muted">{description}</p>
                                </div>
                            </label>
                        ))}
                    </div>

                    <div className="flex justify-end gap-2 border-t border-border px-6 py-4">
                        <button
                            className="flex items-center gap-2 rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark transition-colors disabled:opacity-60"
                            onClick={() => saveMutation.mutate(form)}
                            disabled={saveMutation.isPending}
                        >
                            <Save className="h-4 w-4" />
                            {saveMutation.isPending ? 'Saving…' : 'Save Preferences'}
                        </button>
                    </div>
                </div>
            )}

            {/* ── Billing Tab (placeholder) ─────────────────────────────────────── */}
            {activeTab === 'billing' && (
                <div className="rounded-xl border border-border bg-surface-raised shadow-sm p-8 flex flex-col items-center gap-4 text-center">
                    <CreditCard className="h-10 w-10 text-fg-muted" />
                    <div>
                        <h2 className="text-sm font-semibold text-fg">Billing & Plans</h2>
                        <p className="mt-1 text-sm text-fg-muted max-w-sm">
                            Billing management is coming soon. Contact support for plan changes.
                        </p>
                    </div>
                    <a
                        href="mailto:support@yappc.io"
                        className="rounded-lg border border-border px-4 py-2 text-sm text-fg hover:bg-surface-muted transition-colors"
                    >
                        Contact Support
                    </a>
                </div>
            )}

            {/* ── Danger Zone Tab ───────────────────────────────────────────────── */}
            {activeTab === 'danger' && (
                <div className="rounded-xl border border-error-200 dark:border-error-800 bg-surface-raised shadow-sm">
                    <div className="p-6">
                        <h2 className="text-sm font-semibold text-error-600 mb-1">Delete Workspace</h2>
                        <p className="text-sm text-fg-muted mb-4">
                            Permanently delete this workspace, including all projects, pipelines, and data. This action
                            <strong className="text-fg"> cannot be undone</strong>.
                        </p>

                        <label className={labelCls} htmlFor="delete-confirm">
                            Type <span className="font-mono text-fg">{form.name || workspace?.name}</span> to confirm
                        </label>
                        <input
                            id="delete-confirm"
                            className={inputCls}
                            value={deleteConfirm}
                            onChange={e => setDeleteConfirm(e.target.value)}
                            placeholder={form.name || workspace?.name}
                        />

                        {deleteMutation.isError && (
                            <p className="mt-2 text-sm text-error-600" role="alert">
                                Failed to delete workspace. Please try again.
                            </p>
                        )}
                    </div>

                    <div className="flex justify-end border-t border-error-200 dark:border-error-800 px-6 py-4">
                        <button
                            className="rounded-lg bg-error-600 px-4 py-2 text-sm font-medium text-white hover:bg-error-700 transition-colors disabled:opacity-60"
                            disabled={deleteConfirm !== (form.name || workspace?.name) || deleteMutation.isPending}
                            onClick={() => deleteMutation.mutate()}
                        >
                            {deleteMutation.isPending ? 'Deleting…' : 'Delete Workspace'}
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * Route Error Boundary
 */
export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Settings Error"
            message="Something went wrong while loading workspace settings."
        />
    );
}
