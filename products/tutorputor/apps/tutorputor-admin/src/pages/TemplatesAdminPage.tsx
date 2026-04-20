import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, Input, Modal, Spinner } from '../components/ui';
import { useAuth } from '../hooks/useAuth';

type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

interface TemplateStats {
    totalTemplates: number;
    verifiedTemplates: number;
    premiumTemplates: number;
    byDomain: Record<string, number>;
}

interface TemplateRecord {
    id: string;
    title: string;
    description: string;
    domain: string;
    difficulty: string;
    tags: string[];
    isVerified: boolean;
    isPremium: boolean;
    version: string;
    publishedAt: string;
    status: TemplateStatus;
    stats: {
        views: number;
        uses: number;
        rating: number;
        ratingCount: number;
        favorites: number;
        completionRate: number;
        avgTimeMinutes: number;
    };
}

interface TemplatesResponse {
    templates: TemplateRecord[];
    total: number;
    page: number;
    pageSize: number;
    hasMore: boolean;
}

interface CreateTemplateFormState {
    title: string;
    description: string;
}

export function TemplatesAdminPage() {
    const { tenantId } = useAuth();
    const queryClient = useQueryClient();
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [templatePendingDelete, setTemplatePendingDelete] = useState<TemplateRecord | null>(null);
    const [createForm, setCreateForm] = useState<CreateTemplateFormState>({
        title: '',
        description: '',
    });

    const { data: stats } = useQuery({
        queryKey: ['template-stats', tenantId],
        queryFn: async () => {
            const response = await fetch('/admin/api/v1/templates/stats');
            if (!response.ok) {
                throw new Error('Failed to fetch template stats');
            }
            return response.json() as Promise<TemplateStats>;
        },
    });

    const { data: templatesData, isLoading } = useQuery({
        queryKey: ['templates', tenantId],
        queryFn: async () => {
            const response = await fetch('/admin/api/v1/templates');
            if (!response.ok) {
                throw new Error('Failed to fetch templates');
            }
            return response.json() as Promise<TemplatesResponse>;
        },
    });

    const invalidateTemplates = async (): Promise<void> => {
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: ['templates'] }),
            queryClient.invalidateQueries({ queryKey: ['template-stats'] }),
        ]);
    };

    const createTemplateMutation = useMutation({
        mutationFn: async (payload: CreateTemplateFormState) => {
            const response = await fetch('/admin/api/v1/templates', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!response.ok) {
                throw new Error('Failed to create template');
            }
            return response.json();
        },
        onSuccess: async () => {
            setIsCreateModalOpen(false);
            setCreateForm({ title: '', description: '' });
            await invalidateTemplates();
        },
    });

    const updateTemplateMutation = useMutation({
        mutationFn: async ({ id, payload }: { id: string; payload: Record<string, unknown> }) => {
            const response = await fetch(`/admin/api/v1/templates/${id}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!response.ok) {
                throw new Error('Failed to update template');
            }
            return response.json();
        },
        onSuccess: async () => {
            await invalidateTemplates();
        },
    });

    const deleteTemplateMutation = useMutation({
        mutationFn: async (id: string) => {
            const response = await fetch(`/admin/api/v1/templates/${id}`, {
                method: 'DELETE',
            });
            if (!response.ok) {
                throw new Error('Failed to delete template');
            }
            return response.json();
        },
        onSuccess: async () => {
            setTemplatePendingDelete(null);
            await invalidateTemplates();
        },
    });

    const templates = templatesData?.templates ?? [];

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
                        Simulation Template Curation
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400">
                        Review, verify, and maintain reusable learning simulation templates.
                    </p>
                </div>
                <Button type="button" onClick={() => setIsCreateModalOpen(true)}>
                    + New Template
                </Button>
            </div>

            {stats ? (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
                    <Card className="p-4">
                        <div className="text-sm text-gray-600 dark:text-gray-400">Total Templates</div>
                        <div className="mt-1 text-2xl font-bold text-gray-900 dark:text-white">
                            {stats.totalTemplates}
                        </div>
                    </Card>
                    <Card className="p-4">
                        <div className="text-sm text-gray-600 dark:text-gray-400">Verified Templates</div>
                        <div className="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
                            {stats.verifiedTemplates}
                        </div>
                    </Card>
                    <Card className="p-4">
                        <div className="text-sm text-gray-600 dark:text-gray-400">Premium Templates</div>
                        <div className="mt-1 text-2xl font-bold text-purple-600 dark:text-purple-400">
                            {stats.premiumTemplates}
                        </div>
                    </Card>
                    <Card className="p-4">
                        <div className="text-sm text-gray-600 dark:text-gray-400">Domains</div>
                        <div className="mt-1 text-2xl font-bold text-gray-900 dark:text-white">
                            {Object.keys(stats.byDomain).length}
                        </div>
                    </Card>
                </div>
            ) : null}

            {isLoading ? (
                <div className="flex justify-center py-12">
                    <Spinner size="lg" />
                </div>
            ) : templates.length === 0 ? (
                <Card className="p-12 text-center">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white">No templates found</h2>
                    <p className="mt-2 text-gray-500">Create the first template to seed the simulation catalog.</p>
                </Card>
            ) : (
                <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
                    {templates.map((template) => (
                        <Card key={template.id} className="p-6">
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                                        {template.title}
                                    </h2>
                                    <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
                                        {template.description}
                                    </p>
                                </div>
                                <div className="flex gap-2">
                                    <Badge variant={template.isVerified ? 'default' : 'secondary'}>
                                        {template.isVerified ? 'Verified' : 'Needs review'}
                                    </Badge>
                                    <Badge variant={template.isPremium ? 'outline' : 'secondary'}>
                                        {template.status}
                                    </Badge>
                                </div>
                            </div>

                            <div className="mt-4 flex flex-wrap gap-2 text-xs text-gray-500">
                                <span>{template.domain}</span>
                                <span>•</span>
                                <span>{template.difficulty}</span>
                                <span>•</span>
                                <span>v{template.version}</span>
                                <span>•</span>
                                <span>{template.stats.uses} uses</span>
                            </div>

                            <div className="mt-4 flex flex-wrap gap-2">
                                {template.tags.map((tag) => (
                                    <Badge key={tag} variant="outline">
                                        {tag}
                                    </Badge>
                                ))}
                            </div>

                            <div className="mt-6 flex gap-3">
                                {!template.isVerified ? (
                                    <Button
                                        type="button"
                                        onClick={() => updateTemplateMutation.mutate({
                                            id: template.id,
                                            payload: { isVerified: true },
                                        })}
                                        disabled={updateTemplateMutation.isPending}
                                    >
                                        Verify
                                    </Button>
                                ) : null}
                                <Button
                                    type="button"
                                    onClick={() => setTemplatePendingDelete(template)}
                                    disabled={deleteTemplateMutation.isPending}
                                >
                                    Delete
                                </Button>
                            </div>
                        </Card>
                    ))}
                </div>
            )}

            <Modal isOpen={isCreateModalOpen} className="w-full max-w-lg">
                <div className="space-y-4">
                    <div>
                        <h2 className="text-xl font-semibold text-gray-900">Create Template</h2>
                        <p className="mt-1 text-sm text-gray-500">
                            Add a new template to the reusable simulation catalog.
                        </p>
                    </div>

                    <Input
                        placeholder="Title"
                        value={createForm.title}
                        onChange={(event) => setCreateForm((current) => ({
                            ...current,
                            title: event.target.value,
                        }))}
                    />
                    <Input
                        placeholder="Description"
                        value={createForm.description}
                        onChange={(event) => setCreateForm((current) => ({
                            ...current,
                            description: event.target.value,
                        }))}
                    />

                    <div className="flex justify-end gap-3">
                        <Button type="button" onClick={() => setIsCreateModalOpen(false)}>
                            Cancel
                        </Button>
                        <Button
                            type="button"
                            onClick={() => createTemplateMutation.mutate(createForm)}
                            disabled={createTemplateMutation.isPending || !createForm.title.trim() || !createForm.description.trim()}
                        >
                            Create Template
                        </Button>
                    </div>
                </div>
            </Modal>

            <Modal isOpen={templatePendingDelete !== null} className="w-full max-w-lg">
                <div className="space-y-4">
                    <div>
                        <h2 className="text-xl font-semibold text-gray-900">Confirm Template Deletion</h2>
                        <p className="mt-1 text-sm text-gray-500">
                            {templatePendingDelete
                                ? `Delete ${templatePendingDelete.title}? This action cannot be undone.`
                                : 'Delete this template?'}
                        </p>
                    </div>

                    <div className="flex justify-end gap-3">
                        <Button type="button" onClick={() => setTemplatePendingDelete(null)}>
                            Cancel
                        </Button>
                        <Button
                            type="button"
                            onClick={() => {
                                if (templatePendingDelete) {
                                    deleteTemplateMutation.mutate(templatePendingDelete.id);
                                }
                            }}
                            disabled={deleteTemplateMutation.isPending}
                        >
                            Delete Template
                        </Button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
