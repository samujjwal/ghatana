/**
 * Visualization Editor Component
 * 
 * Provides interface for creating and editing interactive visualizations
 * within the domain authoring system. Supports configuration and preview.
 * 
 * @doc.type component
 * @doc.purpose Visualization configuration and management
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui';
import { Input, Button, Spinner, TextArea } from '@ghatana/ui';

interface Visualization {
    id: string;
    conceptId: string;
    visualizationType: string;
    title: string;
    description: string;
    configJson: Record<string, any>;
    previewUrl?: string;
    createdAt: string;
    updatedAt: string;
}

interface VisualizationEditorProps {
    conceptId: string;
    onClose?: () => void;
}

interface CreateVisualizationInput {
    visualizationType: string;
    title: string;
    description: string;
    configJson: Record<string, any>;
}

export function VisualizationEditor({
    conceptId,
    onClose,
}: VisualizationEditorProps) {
    const queryClient = useQueryClient();
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [formData, setFormData] = useState<CreateVisualizationInput>({
        visualizationType: '3D_MODEL',
        title: '',
        description: '',
        configJson: {},
    });
    const [configJsonText, setConfigJsonText] = useState('{}');

    // Fetch visualizations for this concept
    const { data: visualizationsData, isLoading } = useQuery({
        queryKey: ['visualizations', conceptId],
        queryFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/visualizations`
            );
            if (!res.ok) throw new Error('Failed to fetch visualizations');
            const json = await res.json();
            return json.visualizations as Visualization[];
        },
    });

    // Create mutation
    const createMutation = useMutation({
        mutationFn: async (data: CreateVisualizationInput) => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/visualizations`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to create visualization');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['visualizations', conceptId],
            });
            setShowForm(false);
            resetForm();
        },
    });

    // Update mutation
    const updateMutation = useMutation({
        mutationFn: async (data: CreateVisualizationInput) => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/visualizations/${editingId}`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to update visualization');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['visualizations', conceptId],
            });
            setShowForm(false);
            setEditingId(null);
            resetForm();
        },
    });

    // Delete mutation
    const deleteMutation = useMutation({
        mutationFn: async (id: string) => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/visualizations/${id}`,
                {
                    method: 'DELETE',
                }
            );
            if (!res.ok) throw new Error('Failed to delete visualization');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({
                queryKey: ['visualizations', conceptId],
            });
        },
    });

    // Get preview mutation
    const previewMutation = useMutation({
        mutationFn: async (id: string) => {
            const res = await fetch(
                `/admin/api/v1/content/visualizations/${id}/preview`
            );
            if (!res.ok) throw new Error('Failed to get preview');
            return res.json();
        },
    });

    const resetForm = () => {
        setFormData({
            visualizationType: '3D_MODEL',
            title: '',
            description: '',
            configJson: {},
        });
        setConfigJsonText('{}');
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const config = JSON.parse(configJsonText);
            const data = { ...formData, configJson: config };

            if (editingId) {
                updateMutation.mutate(data);
            } else {
                createMutation.mutate(data);
            }
        } catch (error) {
            alert('Invalid JSON in configuration');
        }
    };

    const handleEdit = (visualization: Visualization) => {
        setEditingId(visualization.id);
        setFormData({
            visualizationType: visualization.visualizationType,
            title: visualization.title,
            description: visualization.description,
            configJson: visualization.configJson,
        });
        setConfigJsonText(JSON.stringify(visualization.configJson, null, 2));
        setShowForm(true);
    };

    const handleCancel = () => {
        setShowForm(false);
        setEditingId(null);
        resetForm();
    };

    if (isLoading) {
        return <Spinner />;
    }

    return (
        <div className="space-y-4">
            <div className="flex justify-between items-center">
                <h2 className="text-2xl font-bold">Visualizations</h2>
                <div className="flex gap-2">
                    <Button onClick={() => setShowForm(true)}>Add Visualization</Button>
                    {onClose && (
                        <Button variant="outline" onClick={onClose}>
                            Close
                        </Button>
                    )}
                </div>
            </div>

            {showForm && (
                <Card className="p-4">
                    <h3 className="text-lg font-semibold mb-3">
                        {editingId ? 'Edit Visualization' : 'Add New Visualization'}
                    </h3>
                    <form onSubmit={handleSubmit} className="space-y-3">
                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Visualization Type
                            </label>
                            <select
                                value={formData.visualizationType}
                                onChange={(e) =>
                                    setFormData({
                                        ...formData,
                                        visualizationType: e.target.value,
                                    })
                                }
                                className="w-full p-2 border rounded"
                            >
                                <option value="3D_MODEL">3D Model</option>
                                <option value="GRAPH">Graph</option>
                                <option value="DIAGRAM">Diagram</option>
                                <option value="ANIMATION">Animation</option>
                                <option value="INTERACTIVE">Interactive</option>
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">Title</label>
                            <Input
                                value={formData.title}
                                onChange={(e) =>
                                    setFormData({ ...formData, title: e.target.value })
                                }
                                placeholder="Visualization title"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Description
                            </label>
                            <TextArea
                                value={formData.description}
                                onChange={(e) =>
                                    setFormData({ ...formData, description: e.target.value })
                                }
                                placeholder="What this visualization illustrates"
                                rows={3}
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Configuration (JSON)
                            </label>
                            <textarea
                                value={configJsonText}
                                onChange={(e) => setConfigJsonText(e.target.value)}
                                placeholder='{"width": 800, "height": 600}'
                                className="w-full p-2 border rounded font-mono text-sm"
                                rows={4}
                            />
                        </div>

                        <div className="flex gap-2">
                            <Button
                                type="submit"
                                disabled={
                                    updateMutation.isPending || createMutation.isPending
                                }
                            >
                                {editingId ? 'Update' : 'Create'}
                            </Button>
                            <Button type="button" variant="outline" onClick={handleCancel}>
                                Cancel
                            </Button>
                        </div>
                    </form>
                </Card>
            )}

            <div className="space-y-2">
                {visualizationsData?.map((visualization) => (
                    <Card key={visualization.id} className="p-3">
                        <div className="flex justify-between items-start">
                            <div className="flex-1">
                                <h4 className="font-semibold">{visualization.title}</h4>
                                <p className="text-sm text-gray-600">
                                    {visualization.description}
                                </p>
                                <p className="text-xs text-gray-500 mt-1">
                                    Type: {visualization.visualizationType}
                                </p>
                            </div>
                            <div className="flex gap-1">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() =>
                                        previewMutation.mutate(visualization.id)
                                    }
                                    disabled={previewMutation.isPending}
                                >
                                    Preview
                                </Button>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => handleEdit(visualization)}
                                >
                                    Edit
                                </Button>
                                <Button
                                    variant="danger"
                                    size="sm"
                                    onClick={() => {
                                        if (window.confirm('Delete this visualization?')) {
                                            deleteMutation.mutate(visualization.id);
                                        }
                                    }}
                                    disabled={deleteMutation.isPending}
                                >
                                    Delete
                                </Button>
                            </div>
                        </div>
                    </Card>
                ))}
            </div>
        </div>
    );
}

export default VisualizationEditor;
