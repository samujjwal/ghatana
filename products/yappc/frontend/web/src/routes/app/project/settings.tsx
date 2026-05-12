import { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Save } from 'lucide-react';
import { yappcApi } from '@/lib/api';
import { useAtomValue } from 'jotai';
import { currentWorkspaceIdAtom } from '@/state/atoms/workspaceAtom';
import type {
    ProjectContract,
    ProjectTypeContract,
    SaveSyncStatusContract,
    UpdateProjectRequestContract,
} from '@/contracts/workspace-project';
import { SaveSyncStatusBadge } from '../../../components/status/SaveSyncStatusBadge';
import { Button } from '../../../components/ui/Button';
import { Input } from '../../../components/ui/Input';
import { Select } from '../../../components/ui/Select';
import { Textarea } from '../../../components/ui/Textarea';

import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';

interface ProjectSettingsFormState {
    name: string;
    description: string;
    type: ProjectTypeContract;
}

const projectTypeOptions: Array<{ value: ProjectTypeContract; label: string; description: string }> = [
    { value: 'FULL_STACK', label: 'Full Stack Application', description: 'Frontend and backend delivered together.' },
    { value: 'BACKEND', label: 'Backend Service', description: 'APIs, workers, and service integrations.' },
    { value: 'UI', label: 'UI or Library', description: 'Reusable interface components and browser-facing work.' },
    { value: 'MOBILE', label: 'Mobile App', description: 'Mobile-first product experience.' },
    { value: 'DESKTOP', label: 'Desktop App', description: 'Locally installed workstation application.' },
];

export default function Component() {
    const { projectId } = useParams<{ projectId: string }>();
    const currentWorkspaceId = useAtomValue(currentWorkspaceIdAtom);
    const queryClient = useQueryClient();
    const [showAdvancedMetadata, setShowAdvancedMetadata] = useState(false);
    const [saveSuccess, setSaveSuccess] = useState(false);
    const [saveStatus, setSaveStatus] = useState<SaveSyncStatusContract | null>(null);

    const { data: project, isLoading } = useQuery<ProjectContract>({
        queryKey: ['project', projectId, currentWorkspaceId],
        queryFn: async () => {
            if (!projectId) {
                throw new Error('Project id is required to load project settings');
            }
            if (!currentWorkspaceId) {
                throw new Error('Workspace context is required. Please access this project from within a workspace.');
            }
            return yappcApi.projects.getScoped(projectId, currentWorkspaceId) as unknown as Promise<ProjectContract>;
        },
        enabled: Boolean(projectId) && Boolean(currentWorkspaceId),
    });

    const [form, setForm] = useState<ProjectSettingsFormState>({
        name: '',
        description: '',
        type: 'FULL_STACK',
    });

    useEffect(() => {
        if (project) {
            setForm({
                name: project.name ?? '',
                description: project.description ?? '',
                type: project.type ?? 'FULL_STACK',
            });
            setSaveStatus(null);
        }
    }, [project]);

    const hasUnsavedChanges =
        form.name !== (project?.name ?? '') ||
        form.description !== (project?.description ?? '') ||
        form.type !== (project?.type ?? 'FULL_STACK');

    const saveSettingsMutation = useMutation({
        mutationFn: async (settings: ProjectSettingsFormState) => {
            const workspaceScopeId = currentWorkspaceId || project?.ownerWorkspaceId;

            if (!projectId || !workspaceScopeId) {
                throw new Error('Project workspace context is missing');
            }

            const request: UpdateProjectRequestContract = {
                name: settings.name,
                description: settings.description,
                type: settings.type,
            };

            return yappcApi.projects.updateScoped(projectId, workspaceScopeId, request) as unknown as ProjectContract;
        },
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['project', projectId] });
            setSaveSuccess(true);
            setSaveStatus('remote-saved');
            setTimeout(() => setSaveSuccess(false), 3000);
        },
        onError: () => {
            setSaveStatus('remote-failed');
        },
    });

    if (isLoading) {
        return (
            <div className="p-6">
                <div className="animate-pulse space-y-4">
                    <div className="h-8 w-48 rounded bg-surface" />
                    <div className="h-4 w-72 rounded bg-surface" />
                    <div className="h-48 rounded-lg bg-surface" />
                </div>
            </div>
        );
    }

    return (
        <div className="p-6" data-testid="project-settings">
            <div className="mb-6">
                <h1 className="text-2xl font-semibold text-fg-muted" data-testid="project-settings-heading">Project Settings</h1>
                <p className="mt-1 text-sm text-fg-muted">Manage the project fields that are backed by the current API contract.</p>
                {(saveSettingsMutation.isPending || hasUnsavedChanges || saveStatus) && (
                    <div className="mt-3">
                        <SaveSyncStatusBadge
                            labels={{
                                'local-only': 'Unsaved local edits',
                                syncing: 'Saving project settings',
                                'remote-saved': 'Project settings saved',
                                'remote-failed': 'Project settings save failed',
                            }}
                            status={saveSettingsMutation.isPending ? 'syncing' : hasUnsavedChanges ? 'local-only' : saveStatus ?? 'remote-saved'}
                        />
                    </div>
                )}
            </div>

            <div className="mb-6 rounded-lg border border-warning-border/40 bg-warning-bg/30 p-4 text-sm text-warning-color">
                <div className="flex items-start gap-3">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning-color" />
                    <div>
                        <p className="font-medium">Advanced capabilities unavailable</p>
                        <p className="mt-1 text-warning-color/80">
                            Team access management, API tokens, and audit feeds remain hidden until their backing APIs are restored.
                        </p>
                    </div>
                </div>
            </div>

            <div className="rounded-xl border border-border bg-surface-raised shadow-sm">
                <div className="space-y-5 p-6" data-testid="project-config-tab">
                    <div>
                        <Input
                            id="project-name"
                            label="Project Name"
                            value={form.name}
                            onChange={(event) => {
                                setSaveStatus('local-only');
                                setForm((prev) => ({ ...prev, name: event.target.value }));
                            }}
                            data-testid="project-name-input"
                        />
                    </div>

                    <div>
                        <Textarea
                            id="project-description"
                            label="Description"
                            rows={4}
                            value={form.description}
                            onChange={(event) => {
                                setSaveStatus('local-only');
                                setForm((prev) => ({ ...prev, description: event.target.value }));
                            }}
                            data-testid="project-description-input"
                        />
                    </div>

                    <div>
                        <Select
                            id="project-type"
                            label="Project Type"
                            options={projectTypeOptions}
                            value={form.type}
                            onChange={(event) => {
                                setSaveStatus('local-only');
                                setForm((prev) => ({ ...prev, type: event.target.value as ProjectTypeContract }));
                            }}
                            data-testid="project-type-select"
                        />
                        <p className="mt-2 text-xs text-fg-muted">
                            {projectTypeOptions.find((option) => option.value === form.type)?.description}
                        </p>
                    </div>

                    <div className="rounded-lg border border-border bg-surface-muted/40 p-4">
                        <div className="flex items-start justify-between gap-4">
                            <div>
                                <p className="text-sm font-medium text-fg">Advanced metadata</p>
                                <p className="mt-1 text-sm text-fg-muted">
                                    Backed project metadata stays available on demand without turning settings into an admin panel.
                                </p>
                            </div>
                            <Button
                                type="button"
                                variant="outline"
                                size="small"
                                className="border-border text-fg-muted hover:bg-surface"
                                onClick={() => setShowAdvancedMetadata((currentValue) => !currentValue)}
                                data-testid="project-advanced-metadata-toggle"
                            >
                                {showAdvancedMetadata ? 'Hide details' : 'Show details'}
                            </Button>
                        </div>

                        {showAdvancedMetadata && project && (
                            <dl className="mt-4 grid gap-3 text-sm text-fg-muted sm:grid-cols-2" data-testid="project-advanced-metadata">
                                <div>
                                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Project ID</dt>
                                    <dd className="mt-1 break-all font-mono text-fg">{project.id}</dd>
                                </div>
                                <div>
                                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Workspace ID</dt>
                                    <dd className="mt-1 break-all font-mono text-fg">{project.ownerWorkspaceId}</dd>
                                </div>
                                <div>
                                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Status</dt>
                                    <dd className="mt-1 text-fg">{project.status}</dd>
                                </div>
                                <div>
                                    <dt className="text-xs font-medium uppercase tracking-[0.12em] text-fg-muted">Lifecycle phase</dt>
                                    <dd className="mt-1 text-fg">{project.lifecyclePhase}</dd>
                                </div>
                            </dl>
                        )}
                    </div>

                    {saveSettingsMutation.isError && (
                        <p className="text-sm text-destructive" role="alert">
                            {saveSettingsMutation.error instanceof Error
                                ? saveSettingsMutation.error.message
                                : 'Failed to save project settings.'}
                        </p>
                    )}

                    {saveSuccess && (
                        <p className="text-sm text-success-color" role="status">
                            Project settings saved.
                        </p>
                    )}
                </div>

                <div className="flex justify-end gap-2 border-t border-border px-6 py-4">
                    <Button
                        type="button"
                        variant="outline"
                        className="border-border text-fg-muted hover:bg-surface-muted"
                        onClick={() => {
                            if (project) {
                                setForm({
                                    name: project.name ?? '',
                                    description: project.description ?? '',
                                    type: project.type ?? 'FULL_STACK',
                                });
                            }
                        }}
                    >
                        Discard
                    </Button>
                    <Button
                        type="button"
                        onClick={() => saveSettingsMutation.mutate(form)}
                        disabled={saveSettingsMutation.isPending}
                        data-testid="save-settings-button"
                        startIcon={<Save className="h-4 w-4" />}
                    >
                        {saveSettingsMutation.isPending ? 'Saving…' : 'Save Changes'}
                    </Button>
                </div>
            </div>
        </div>
    );
}

export function ErrorBoundary() {
    return (
        <RouteErrorBoundary
            title="Settings Error"
            message="Unable to load project settings. Please try refreshing the page."
        />
    );
}
