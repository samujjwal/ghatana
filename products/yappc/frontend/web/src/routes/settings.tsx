/**
 * Settings Route
 *
 * Workspace-level settings limited to supported fields and destructive actions.
 *
 * @doc.type route
 * @doc.purpose Workspace settings management
 * @doc.layer product
 * @doc.pattern Route Module
 */

import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { Settings, AlertTriangle, Save, AlertCircle } from 'lucide-react';
import { parseJsonResourceResponse, readErrorResponse } from '@/lib/http';
import type {
    SaveSyncStatusContract,
    UpdateWorkspaceRequestContract,
    WorkspaceContract,
} from '@/contracts/workspace-project';
import { SaveSyncStatusBadge } from '../components/status/SaveSyncStatusBadge';
import { currentWorkspaceIdAtom } from '../state/atoms/workspaceAtom';
import { useCurrentUser } from '../providers/AuthProvider';
import { RouteErrorBoundary } from '../components/route/ErrorBoundary';

interface SettingsFormState {
    name: string;
    description: string;
}

const inputCls =
    'w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg placeholder-fg-muted focus:outline-none focus:ring-2 focus:ring-brand';
const labelCls = 'block text-xs font-medium text-fg-muted mb-1';

type Tab = 'general' | 'danger';

const tabs: { id: Tab; label: string; icon: React.ReactNode }[] = [
    { id: 'general', label: 'General', icon: <Settings className="h-4 w-4" /> },
    { id: 'danger', label: 'Danger Zone', icon: <AlertTriangle className="h-4 w-4" /> },
];

/**
 * Settings Page Component
 */
export default function Component() {
    const [searchParams] = useSearchParams();
    const atomWorkspaceId = useAtomValue(currentWorkspaceIdAtom);
    const queryWorkspaceId = searchParams.get('workspaceId');
    const workspaceId = queryWorkspaceId || atomWorkspaceId;
    const currentUser = useCurrentUser();
    const queryClient = useQueryClient();
    const workspaceIdMismatch = Boolean(queryWorkspaceId && queryWorkspaceId !== atomWorkspaceId);
    const [activeTab, setActiveTab] = useState<Tab>('general');
    const [showAdvancedMetadata, setShowAdvancedMetadata] = useState(false);
    const [saveSuccess, setSaveSuccess] = useState(false);
    const [deleteConfirm, setDeleteConfirm] = useState('');
    const [saveStatus, setSaveStatus] = useState<SaveSyncStatusContract | null>(null);

    // ── Fetch workspace settings ───────────────────────────────────────────────
    const { data: workspace, isLoading } = useQuery<WorkspaceContract>({
        queryKey: ['workspace-settings', workspaceId],
        queryFn: async () => {
            const res = await fetch(`/api/workspaces/${workspaceId}`);
            if (!res.ok) throw new Error('Failed to load workspace settings');
            return parseJsonResourceResponse<WorkspaceContract>(res, 'workspace settings load', 'workspace');
        },
        enabled: !!workspaceId && currentUser.isAuthenticated,
    });

    // ── Form state ─────────────────────────────────────────────────────────────
    const [form, setForm] = useState<SettingsFormState>({
        name: '',
        description: '',
    });

    useEffect(() => {
        if (workspace) {
            setForm({
                name: workspace.name ?? '',
                description: workspace.description ?? '',
            });
            setSaveStatus(null);
        }
    }, [workspace]);

    const hasUnsavedChanges = form.name !== (workspace?.name ?? '') || form.description !== (workspace?.description ?? '');

    // ── Save settings ──────────────────────────────────────────────────────────
    const saveMutation = useMutation({
        mutationFn: async (data: SettingsFormState) => {
            const request: UpdateWorkspaceRequestContract = {
                name: data.name,
                description: data.description,
            };
            const res = await fetch(`/api/workspaces/${workspaceId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(request),
            });
            if (!res.ok) {
                throw new Error(await readErrorResponse(res, 'Failed to save settings'));
            }
            return parseJsonResourceResponse<WorkspaceContract>(res, 'workspace settings save', 'workspace');
        },
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['workspace-settings', workspaceId] });
            setSaveSuccess(true);
            setSaveStatus('remote-saved');
            setTimeout(() => setSaveSuccess(false), 3000);
        },
        onError: () => {
            setSaveStatus('remote-failed');
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
                    Manage the supported workspace identity fields and destructive actions.
                </p>
                {workspaceIdMismatch && (
                    <div className="mt-3 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-100" role="alert">
                        <AlertCircle className="h-4 w-4 shrink-0" />
                        <span>
                            You are editing a different workspace than your current context. Changes will apply to this workspace.
                        </span>
                    </div>
                )}
                {(saveMutation.isPending || hasUnsavedChanges || saveStatus) && (
                    <div className="mt-3">
                        <SaveSyncStatusBadge
                            labels={{
                                'local-only': 'Unsaved local edits',
                                syncing: 'Saving settings',
                                'remote-saved': 'Settings saved',
                                'remote-failed': 'Settings save failed',
                            }}
                            status={saveMutation.isPending ? 'syncing' : hasUnsavedChanges ? 'local-only' : saveStatus ?? 'remote-saved'}
                        />
                    </div>
                )}
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
                                onChange={e => {
                                    setSaveStatus('local-only');
                                    setForm(prev => ({ ...prev, name: e.target.value }));
                                }}
                                placeholder="My Workspace"
                            />
                        </div>

                        <div>
                            <label className={labelCls} htmlFor="ws-desc">Description</label>
                            <textarea
                                id="ws-desc"
                                rows={3}
                                className={inputCls}
                                value={form.description}
                                onChange={e => {
                                    setSaveStatus('local-only');
                                    setForm(prev => ({ ...prev, description: e.target.value }));
                                }}
                                placeholder="What is this workspace for?"
                            />
                        </div>

                        <div className="rounded-lg border border-border bg-surface-muted/40 p-4">
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-sm font-medium text-fg">Advanced metadata</p>
                                    <p className="mt-1 text-sm text-fg-muted">
                                        Read-only identity metadata is available on demand without crowding the primary settings flow.
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-fg-muted transition-colors hover:bg-surface"
                                    onClick={() => setShowAdvancedMetadata((currentValue) => !currentValue)}
                                    data-testid="workspace-advanced-metadata-toggle"
                                >
                                    {showAdvancedMetadata ? 'Hide details' : 'Show details'}
                                </button>
                            </div>

                            {showAdvancedMetadata && workspace && (
                                <dl className="mt-4 grid gap-3 text-sm text-fg-muted sm:grid-cols-2" data-testid="workspace-advanced-metadata">
                                    <div>
                                        <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Workspace ID</dt>
                                        <dd className="mt-1 break-all font-mono text-fg">{workspace.id}</dd>
                                    </div>
                                    <div>
                                        <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Owner ID</dt>
                                        <dd className="mt-1 break-all font-mono text-fg">{workspace.ownerId}</dd>
                                    </div>
                                    <div>
                                        <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Created</dt>
                                        <dd className="mt-1 text-fg">{new Date(workspace.createdAt).toLocaleString()}</dd>
                                    </div>
                                    <div>
                                        <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Updated</dt>
                                        <dd className="mt-1 text-fg">{new Date(workspace.updatedAt).toLocaleString()}</dd>
                                    </div>
                                </dl>
                            )}
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
                                description: workspace.description ?? '',
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
