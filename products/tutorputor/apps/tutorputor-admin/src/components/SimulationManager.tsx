/**
 * Simulation Manager Component
 * 
 * Provides interface for creating, editing, and managing simulations
 * and visualizations for educational concepts.
 * 
 * @doc.type component
 * @doc.purpose Admin interface for simulation and visualization management
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Card } from './ui';
import { Input, Button, Select, Spinner } from '@ghatana/ui';
import { SimulationRenderer } from './SimulationRenderer';
import { TemplateLibrary } from './TemplateLibrary';

interface SimulationDefinition {
    id: string;
    conceptId: string;
    type: string;
    manifest: Record<string, unknown>;
    estimatedTimeMinutes: number;
    interactivityLevel: string;
    purpose: string;
    previewConfig?: Record<string, unknown>;
    status: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}

interface VisualizationDefinition {
    id: string;
    conceptId: string;
    type: string;
    config: Record<string, unknown>;
    dataSource: string;
    status: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}

interface SimulationManagerProps {
    domainId: string;
    conceptId: string;
    conceptName: string;
}

const SIMULATION_TYPES = [
    'physics-2D',
    'physics-3D',
    'chemistry-interactive',
    'biology-interactive',
    'mathematics-interactive',
];

const VISUALIZATION_TYPES = [
    'graph-2d',
    'graph-3d',
    'chart',
    'diagram',
    'molecule',
];

const INTERACTIVITY_LEVELS = ['low', 'medium', 'high'];
const DATA_SOURCES = ['simulation', 'static', 'dynamic'];

export function SimulationManager({
    domainId,
    conceptId,
    conceptName,
}: SimulationManagerProps) {
    const queryClient = useQueryClient();
    const [activeTab, setActiveTab] = useState<'view' | 'create-sim' | 'create-viz' | 'templates'>('view');
    const [editingSimulation, setEditingSimulation] = useState<SimulationDefinition | null>(null);
    const [editingVisualization, setEditingVisualization] = useState<VisualizationDefinition | null>(null);
    const [isGeneratingAI, setIsGeneratingAI] = useState(false);
    const [aiDescription, setAiDescription] = useState('');
    const [showAIGenerator, setShowAIGenerator] = useState(false);

    // Form states for simulation
    const [simFormData, setSimFormData] = useState({
        type: 'physics-2D',
        purpose: '',
        estimatedTimeMinutes: 15,
        interactivityLevel: 'medium',
        manifest: '{}',
        previewConfig: '{}',
    });

    // Form states for visualization
    const [vizFormData, setVizFormData] = useState({
        type: 'graph-2d',
        dataSource: 'simulation',
        config: '{}',
    });

    // Fetch simulation
    const { data: simulation, isLoading: simLoading } = useQuery({
        queryKey: ['simulation', conceptId],
        queryFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/simulation`
            );
            if (!res.ok) throw new Error('Failed to fetch simulation');
            return res.json() as Promise<SimulationDefinition | null>;
        },
    });

    // Fetch visualization
    const { data: visualization, isLoading: vizLoading } = useQuery({
        queryKey: ['visualization', conceptId],
        queryFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/visualization`
            );
            if (!res.ok) throw new Error('Failed to fetch visualization');
            return res.json() as Promise<VisualizationDefinition | null>;
        },
    });

    // Create/Update simulation mutation
    const simulationMutation = useMutation({
        mutationFn: async (data: typeof simFormData) => {
            const manifest = JSON.parse(data.manifest);
            const previewConfig = data.previewConfig ? JSON.parse(data.previewConfig) : undefined;

            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/simulation`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        type: data.type,
                        purpose: data.purpose,
                        estimatedTimeMinutes: parseInt(String(data.estimatedTimeMinutes)),
                        interactivityLevel: data.interactivityLevel,
                        manifest,
                        previewConfig,
                    }),
                }
            );
            if (!res.ok) throw new Error('Failed to save simulation');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['simulation', conceptId] });
            queryClient.invalidateQueries({ queryKey: ['domain', domainId] });
            setActiveTab('view');
            resetSimForm();
        },
    });

    // Create/Update visualization mutation
    const visualizationMutation = useMutation({
        mutationFn: async (data: typeof vizFormData) => {
            const config = JSON.parse(data.config);

            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/visualization`,
                {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        type: data.type,
                        dataSource: data.dataSource,
                        config,
                    }),
                }
            );
            if (!res.ok) throw new Error('Failed to save visualization');
            return res.json();
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['visualization', conceptId] });
            queryClient.invalidateQueries({ queryKey: ['domain', domainId] });
            setActiveTab('view');
            resetVizForm();
        },
    });

    // Delete simulation mutation
    const deleteSimulationMutation = useMutation({
        mutationFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/simulation`,
                { method: 'DELETE' }
            );
            if (!res.ok) throw new Error('Failed to delete simulation');
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['simulation', conceptId] });
            queryClient.invalidateQueries({ queryKey: ['domain', domainId] });
        },
    });

    // Delete visualization mutation
    const deleteVisualizationMutation = useMutation({
        mutationFn: async () => {
            const res = await fetch(
                `/admin/api/v1/content/db/domains/${domainId}/concepts/${conceptId}/visualization`,
                { method: 'DELETE' }
            );
            if (!res.ok) throw new Error('Failed to delete visualization');
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['visualization', conceptId] });
            queryClient.invalidateQueries({ queryKey: ['domain', domainId] });
        },
    });

    const handleGenerateSimulationWithAI = async () => {
        if (!aiDescription.trim()) {
            alert('Please enter a description for the simulation');
            return;
        }

        setIsGeneratingAI(true);
        try {
            const res = await fetch('/api/v1/ai/generate-simulation', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    description: aiDescription,
                    conceptName,
                    domain: domainId,
                }),
            });

            if (!res.ok) throw new Error('Failed to generate simulation with AI');

            const data = await res.json();
            const generated = data.simulation;

            // Update form with AI-generated content
            setSimFormData({
                type: generated.type,
                purpose: generated.purpose,
                estimatedTimeMinutes: generated.estimatedTimeMinutes,
                interactivityLevel: 'medium',
                manifest: JSON.stringify(generated.manifest, null, 2),
                previewConfig: '{}',
            });

            setShowAIGenerator(false);
            setAiDescription('');
            alert('AI generated simulation successfully! Review and edit as needed.');
        } catch (error) {
            alert('Error generating simulation with AI: ' + (error instanceof Error ? error.message : String(error)));
        } finally {
            setIsGeneratingAI(false);
        }
    };

    const resetSimForm = () => {
        setEditingSimulation(null);
        setSimFormData({
            type: 'physics-2D',
            purpose: '',
            estimatedTimeMinutes: 15,
            interactivityLevel: 'medium',
            manifest: '{}',
            previewConfig: '{}',
        });
        setShowAIGenerator(false);
        setAiDescription('');
    };

    const resetVizForm = () => {
        setEditingVisualization(null);
        setVizFormData({
            type: 'graph-2d',
            dataSource: 'simulation',
            config: '{}',
        });
    };

    const handleEditSimulation = () => {
        if (simulation) {
            setEditingSimulation(simulation);
            setSimFormData({
                type: simulation.type,
                purpose: simulation.purpose,
                estimatedTimeMinutes: simulation.estimatedTimeMinutes,
                interactivityLevel: simulation.interactivityLevel,
                manifest: JSON.stringify(simulation.manifest),
                previewConfig: JSON.stringify(simulation.previewConfig || {}),
            });
            setActiveTab('create-sim');
        }
    };

    const handleEditVisualization = () => {
        if (visualization) {
            setEditingVisualization(visualization);
            setVizFormData({
                type: visualization.type,
                dataSource: visualization.dataSource,
                config: JSON.stringify(visualization.config),
            });
            setActiveTab('create-viz');
        }
    };

    const handleSubmitSimulation = (e: React.FormEvent) => {
        e.preventDefault();
        try {
            JSON.parse(simFormData.manifest);
            if (simFormData.previewConfig) JSON.parse(simFormData.previewConfig);
            simulationMutation.mutate(simFormData);
        } catch (error) {
            alert('Invalid JSON in manifest or preview config');
        }
    };

    const handleSubmitVisualization = (e: React.FormEvent) => {
        e.preventDefault();
        try {
            JSON.parse(vizFormData.config);
            visualizationMutation.mutate(vizFormData);
        } catch (error) {
            alert('Invalid JSON in config');
        }
    };

    if (simLoading || vizLoading) {
        return <Spinner />;
    }

    return (
        <div className="space-y-4">
            {/* Tabs */}
            <div className="flex border-b border-gray-200 gap-4">
                <button
                    className={`px-4 py-2 font-medium border-b-2 transition ${activeTab === 'view'
                        ? 'border-blue-500 text-blue-600'
                        : 'border-transparent text-gray-600 hover:text-gray-900'
                        }`}
                    onClick={() => setActiveTab('view')}
                >
                    View
                </button>
                <button
                    className={`px-4 py-2 font-medium border-b-2 transition ${activeTab === 'templates'
                        ? 'border-blue-500 text-blue-600'
                        : 'border-transparent text-gray-600 hover:text-gray-900'
                        }`}
                    onClick={() => setActiveTab('templates')}
                >
                    📚 Template Library
                </button>
                <button
                    className={`px-4 py-2 font-medium border-b-2 transition ${activeTab === 'create-sim'
                        ? 'border-blue-500 text-blue-600'
                        : 'border-transparent text-gray-600 hover:text-gray-900'
                        }`}
                    onClick={() => {
                        resetSimForm();
                        setActiveTab('create-sim');
                    }}
                >
                    {simulation ? 'Edit Simulation' : 'Add Simulation'}
                </button>
                <button
                    className={`px-4 py-2 font-medium border-b-2 transition ${activeTab === 'create-viz'
                        ? 'border-blue-500 text-blue-600'
                        : 'border-transparent text-gray-600 hover:text-gray-900'
                        }`}
                    onClick={() => {
                        resetVizForm();
                        setActiveTab('create-viz');
                    }}
                >
                    {visualization ? 'Edit Visualization' : 'Add Visualization'}
                </button>
            </div>

            {/* View Tab */}
            {activeTab === 'view' && (
                <div className="space-y-4">
                    <h2 className="text-lg font-semibold">Concept: {conceptName}</h2>

                    {!simulation && !visualization ? (
                        <Card className="p-6 text-center bg-gray-50">
                            <p className="text-gray-600 mb-4">
                                No simulation or visualization available for this concept yet.
                            </p>
                            <div className="flex gap-2 justify-center">
                                <Button
                                    onClick={() => {
                                        resetSimForm();
                                        setActiveTab('create-sim');
                                    }}
                                >
                                    Add Simulation
                                </Button>
                                <Button
                                    onClick={() => {
                                        resetVizForm();
                                        setActiveTab('create-viz');
                                    }}
                                    variant="outline"
                                >
                                    Add Visualization
                                </Button>
                            </div>
                        </Card>
                    ) : (
                        <>
                            <SimulationRenderer simulation={simulation} visualization={visualization} />

                            <div className="flex gap-2 pt-4">
                                {simulation && (
                                    <>
                                        <Button variant="outline" onClick={handleEditSimulation}>
                                            Edit Simulation
                                        </Button>
                                        <Button
                                            variant="destructive"
                                            onClick={() => {
                                                if (
                                                    window.confirm(
                                                        'Delete this simulation? This action cannot be undone.'
                                                    )
                                                ) {
                                                    deleteSimulationMutation.mutate();
                                                }
                                            }}
                                            disabled={deleteSimulationMutation.isPending}
                                        >
                                            Delete Simulation
                                        </Button>
                                    </>
                                )}
                                {visualization && (
                                    <>
                                        <Button variant="outline" onClick={handleEditVisualization}>
                                            Edit Visualization
                                        </Button>
                                        <Button
                                            variant="destructive"
                                            onClick={() => {
                                                if (
                                                    window.confirm(
                                                        'Delete this visualization? This action cannot be undone.'
                                                    )
                                                ) {
                                                    deleteVisualizationMutation.mutate();
                                                }
                                            }}
                                            disabled={deleteVisualizationMutation.isPending}
                                        >
                                            Delete Visualization
                                        </Button>
                                    </>
                                )}
                            </div>
                        </>
                    )}
                </div>
            )}

            {/* Template Library Tab */}
            {activeTab === 'templates' && (
                <TemplateLibrary
                    conceptId={conceptId}
                    mode="select"
                    onCloneSuccess={() => {
                        queryClient.invalidateQueries({ queryKey: ['simulation', conceptId] });
                        setActiveTab('view');
                    }}
                />
            )}

            {/* Create/Edit Simulation Tab */}
            {activeTab === 'create-sim' && (
                <Card className="p-6">
                    <div className="flex justify-between items-center mb-4">
                        <h2 className="text-xl font-semibold">
                            {editingSimulation ? 'Edit Simulation' : 'Create New Simulation'}
                        </h2>
                        <div className="flex gap-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setActiveTab('templates')}
                            >
                                📚 Browse Templates
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setShowAIGenerator(!showAIGenerator)}
                                className="bg-gradient-to-r from-purple-500 to-pink-500 text-white hover:from-purple-600 hover:to-pink-600"
                            >
                                ✨ Generate with AI
                            </Button>
                        </div>
                    </div>

                    {showAIGenerator && (
                        <div className="mb-4 p-4 bg-purple-50 dark:bg-purple-900/20 rounded-lg border border-purple-200 dark:border-purple-800 space-y-3">
                            <label className="block text-sm font-medium">
                                Describe the simulation you want to create:
                            </label>
                            <textarea
                                value={aiDescription}
                                onChange={(e) => setAiDescription(e.target.value)}
                                placeholder="E.g., Create a pendulum simulation where users can adjust the length and mass of the bob"
                                className="w-full p-3 border rounded-lg"
                                rows={3}
                            />
                            <div className="flex gap-2">
                                <Button
                                    type="button"
                                    onClick={handleGenerateSimulationWithAI}
                                    disabled={isGeneratingAI || !aiDescription.trim()}
                                >
                                    {isGeneratingAI ? '🤖 Generating...' : '✨ Generate'}
                                </Button>
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => {
                                        setShowAIGenerator(false);
                                        setAiDescription('');
                                    }}
                                >
                                    Cancel
                                </Button>
                            </div>
                        </div>
                    )}

                    <form onSubmit={handleSubmitSimulation} className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium mb-1">Simulation Type</label>
                            <Select
                                value={simFormData.type}
                                onChange={(e) =>
                                    setSimFormData({ ...simFormData, type: e.target.value })
                                }
                            >
                                {SIMULATION_TYPES.map((type) => (
                                    <option key={type} value={type}>
                                        {type}
                                    </option>
                                ))}
                            </Select>
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">Purpose</label>
                            <textarea
                                value={simFormData.purpose}
                                onChange={(e) =>
                                    setSimFormData({ ...simFormData, purpose: e.target.value })
                                }
                                placeholder="Describe the purpose of this simulation"
                                className="w-full p-2 border rounded"
                                rows={3}
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium mb-1">
                                    Estimated Time (minutes)
                                </label>
                                <Input
                                    type="number"
                                    value={simFormData.estimatedTimeMinutes}
                                    onChange={(e) =>
                                        setSimFormData({
                                            ...simFormData,
                                            estimatedTimeMinutes: parseInt(e.target.value) || 15,
                                        })
                                    }
                                    min="1"
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium mb-1">
                                    Interactivity Level
                                </label>
                                <Select
                                    value={simFormData.interactivityLevel}
                                    onChange={(e) =>
                                        setSimFormData({
                                            ...simFormData,
                                            interactivityLevel: e.target.value,
                                        })
                                    }
                                >
                                    {INTERACTIVITY_LEVELS.map((level) => (
                                        <option key={level} value={level}>
                                            {level.charAt(0).toUpperCase() + level.slice(1)}
                                        </option>
                                    ))}
                                </Select>
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">Manifest (JSON)</label>
                            <textarea
                                value={simFormData.manifest}
                                onChange={(e) =>
                                    setSimFormData({ ...simFormData, manifest: e.target.value })
                                }
                                placeholder='{"title": "My Simulation", ...}'
                                className="w-full p-2 border rounded font-mono text-xs"
                                rows={6}
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">
                                Preview Config (JSON, optional)
                            </label>
                            <textarea
                                value={simFormData.previewConfig}
                                onChange={(e) =>
                                    setSimFormData({ ...simFormData, previewConfig: e.target.value })
                                }
                                placeholder='{"width": 800, "height": 600}'
                                className="w-full p-2 border rounded font-mono text-xs"
                                rows={4}
                            />
                        </div>

                        <div className="flex gap-2">
                            <Button
                                type="submit"
                                disabled={simulationMutation.isPending}
                            >
                                {editingSimulation ? 'Update' : 'Create'} Simulation
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => {
                                    resetSimForm();
                                    setActiveTab('view');
                                }}
                            >
                                Cancel
                            </Button>
                        </div>
                    </form>
                </Card>
            )}

            {/* Create/Edit Visualization Tab */}
            {activeTab === 'create-viz' && (
                <Card className="p-6">
                    <h2 className="text-xl font-semibold mb-4">
                        {editingVisualization ? 'Edit Visualization' : 'Create New Visualization'}
                    </h2>
                    <form onSubmit={handleSubmitVisualization} className="space-y-4">
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium mb-1">
                                    Visualization Type
                                </label>
                                <Select
                                    value={vizFormData.type}
                                    onChange={(e) =>
                                        setVizFormData({ ...vizFormData, type: e.target.value })
                                    }
                                >
                                    {VISUALIZATION_TYPES.map((type) => (
                                        <option key={type} value={type}>
                                            {type}
                                        </option>
                                    ))}
                                </Select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium mb-1">Data Source</label>
                                <Select
                                    value={vizFormData.dataSource}
                                    onChange={(e) =>
                                        setVizFormData({ ...vizFormData, dataSource: e.target.value })
                                    }
                                >
                                    {DATA_SOURCES.map((source) => (
                                        <option key={source} value={source}>
                                            {source.charAt(0).toUpperCase() + source.slice(1)}
                                        </option>
                                    ))}
                                </Select>
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-1">Config (JSON)</label>
                            <textarea
                                value={vizFormData.config}
                                onChange={(e) =>
                                    setVizFormData({ ...vizFormData, config: e.target.value })
                                }
                                placeholder='{"labels": ["A", "B", "C"], "dataPoints": [10, 20, 30]}'
                                className="w-full p-2 border rounded font-mono text-xs"
                                rows={8}
                            />
                        </div>

                        <div className="flex gap-2">
                            <Button
                                type="submit"
                                disabled={visualizationMutation.isPending}
                            >
                                {editingVisualization ? 'Update' : 'Create'} Visualization
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => {
                                    resetVizForm();
                                    setActiveTab('view');
                                }}
                            >
                                Cancel
                            </Button>
                        </div>
                    </form>
                </Card>
            )}
        </div>
    );
}

export default SimulationManager;
