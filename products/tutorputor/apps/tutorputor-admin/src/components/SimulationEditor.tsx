/**
 * Simulation Editor Component
 * 
 * Enables creation and editing of interactive simulations within the domain authoring system.
 * Allows configuration of simulation parameters and preview functionality.
 * 
 * @doc.type component
 * @doc.purpose Simulation configuration and management
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '../components/ui';
import { Input, Button, Spinner, TextArea } from '@ghatana/design-system';

interface Simulation {
    id: string;
    conceptId: string;
    simulationType: string;
    title: string;
    description: string;
    parameterJson: Record<string, any>;
    previewUrl?: string;
    createdAt: string;
    updatedAt: string;
}

interface SimulationEditorProps {
    conceptId: string;
    onClose?: () => void;
}

interface CreateSimulationInput {
    simulationType: string;
    title: string;
    description: string;
    parameterJson: Record<string, any>;
}

export function SimulationEditor({ conceptId, onClose }: SimulationEditorProps) {
    const queryClient = useQueryClient();
    const [showForm, setShowForm] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [formData, setFormData] = useState<CreateSimulationInput>({
        simulationType: 'PHYSICS',
        title: '',
        description: '',
        parameterJson: {},
    });
    const [parameterJsonText, setParameterJsonText] = useState('{}');

    // Fetch simulations for this concept
    const { data: simulationsData, isLoading } = useQuery({
        queryKey: ['simulations', conceptId],
        queryFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/simulations`
            );
            if (!res.ok) throw new Error('Failed to fetch simulations');
            const json = await res.json();
            return json.simulations as Simulation[];
        },
    });

    // Create mutation
    const createMutation = useMutation({
        mutationFn: async (data: CreateSimulationInput) => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/simulations`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to create simulation');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['simulations', conceptId] });
            setShowForm(false);
            resetForm();
        },
    });

    // Update mutation
    const updateMutation = useMutation({
        mutationFn: async (data: CreateSimulationInput) => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/simulations/${editingId}`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(data),
                }
            );
            if (!res.ok) throw new Error('Failed to update simulation');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['simulations', conceptId] });
            setShowForm(false);
            setEditingId(null);
            resetForm();
        },
    });

    // Delete mutation
    const deleteMutation = useMutation({
        mutationFn: async (id: string) => {
            const res = await fetch(
                `/admin/api/v1/content/concepts/${conceptId}/simulations/${id}`,
                {
                    method: 'DELETE',
                }
            );
            if (!res.ok) throw new Error('Failed to delete simulation');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['simulations', conceptId] });
        },
    });

    // Get preview mutation
    const previewMutation = useMutation({
        mutationFn: async (id: string) => {
            const res = await fetch(
                `/admin/api/v1/content/simulations/${id}/preview`
            );
            if (!res.ok) throw new Error('Failed to get preview');
            return res.json();
        },
    });

    const resetForm = () => {
        setFormData({
            simulationType: 'PHYSICS',
            title: '',
            description: '',
            parameterJson: {},
        });
        setParameterJsonText('{}');
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const params = JSON.parse(parameterJsonText);
            const data = { ...formData, parameterJson: params };

            if (editingId) {
                updateMutation.mutate(data);
            } else {
                createMutation.mutate(data);
            }
        } catch (error) {
            alert('Invalid JSON in parameters');
        }
    };

    const handleEdit = (simulation: Simulation) => {
        setEditingId(simulation.id);
        setFormData({
            simulationType: simulation.simulationType,
            title: simulation.title,
            description: simulation.description,
            parameterJson: simulation.parameterJson,
        });
        setParameterJsonText(JSON.stringify(simulation.parameterJson, null, 2));
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
                <h2 className="text-2xl font-bold">Simulations</h2>
                <div className="flex gap-2">
                    <Button onClick={() => setShowForm(true)}>Add Simulation</Button>
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
                        {editingId ? 'Edit Simulation' : 'Add New Simulation'}
                    </h3>
                    <form onSubmit={handleSubmit} className="space-y-3">
                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Simulation Type
                            </label>
                            <select
                                value={formData.simulationType}
                                onChange={(e) =>
                                    setFormData({
                                        ...formData,
                                        simulationType: e.target.value,
                                    })
                                }
                                className="w-full p-2 border rounded"
                            >
                                <option value="PHYSICS">Physics</option>
                                <option value="CHEMISTRY">Chemistry</option>
                                <option value="BIOLOGY">Biology</option>
                                <option value="MATHEMATICS">Mathematics</option>
                                <option value="ENGINEERING">Engineering</option>
                            </select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">Title</label>
                            <Input
                                value={formData.title}
                                onChange={(e) =>
                                    setFormData({ ...formData, title: e.target.value })
                                }
                                placeholder="Simulation title"
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
                                placeholder="What this simulation demonstrates"
                                rows={3}
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Parameters (JSON)
                            </label>
                            <TextArea
                                value={parameterJsonText}
                                onChange={(e) => setParameterJsonText(e.target.value)}
                                placeholder='{"gravity": 9.81, "mass": 1.0}'
                                className="w-full p-2 border rounded font-mono text-sm"
                                rows={4}
                            />
                        </div>

                        <div className="flex gap-2">
                            <Button
                                type="submit"
                                disabled={updateMutation.isPending || createMutation.isPending}
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
                {simulationsData?.map((simulation) => (
                    <Card key={simulation.id} className="p-3">
                        <div className="flex justify-between items-start">
                            <div className="flex-1">
                                <h4 className="font-semibold">{simulation.title}</h4>
                                <p className="text-sm text-gray-600">{simulation.description}</p>
                                <p className="text-xs text-gray-500 mt-1">
                                    Type: {simulation.simulationType}
                                </p>
                            </div>
                            <div className="flex gap-1">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() =>
                                        previewMutation.mutate(simulation.id)
                                    }
                                    disabled={previewMutation.isPending}
                                >
                                    Preview
                                </Button>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => handleEdit(simulation)}
                                >
                                    Edit
                                </Button>
                                <Button
                                    variant="danger"
                                    size="sm"
                                    onClick={() => {
                                        if (window.confirm('Delete this simulation?')) {
                                            deleteMutation.mutate(simulation.id);
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

export default SimulationEditor;
