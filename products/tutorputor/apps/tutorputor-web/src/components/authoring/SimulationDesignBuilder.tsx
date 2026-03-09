/**
 * Simulation Design Builder
 * 
 * @doc.type component
 * @doc.purpose Phase 3 of authoring flow - Entity configuration and physics setup
 * @doc.layer product
 * @doc.pattern Builder
 */

import { useState } from "react";

interface SimulationDesignBuilderProps {
    manifest: any;
    onComplete: (updatedManifest: any) => void;
    onBack: () => void;
}

export function SimulationDesignBuilder({ manifest, onComplete, onBack }: SimulationDesignBuilderProps) {
    const [activeTab, setActiveTab] = useState<'entities' | 'physics' | 'interactions'>('entities');
    const [editedManifest, setEditedManifest] = useState(manifest);
    const [selectedEntity, setSelectedEntity] = useState<string | null>(null);

    const updateEntity = (entityId: string, updates: any) => {
        const updatedEntities = editedManifest.initialEntities.map((e: any) =>
            e.id === entityId ? { ...e, ...updates } : e
        );
        setEditedManifest({ ...editedManifest, initialEntities: updatedEntities });
    };

    const addEntity = () => {
        const newEntity = {
            id: `entity_${Date.now()}`,
            type: 'physics_object',
            label: 'New Entity',
            position: { x: 100, y: 100 },
            properties: {}
        };
        setEditedManifest({
            ...editedManifest,
            initialEntities: [...editedManifest.initialEntities, newEntity]
        });
    };

    const removeEntity = (entityId: string) => {
        setEditedManifest({
            ...editedManifest,
            initialEntities: editedManifest.initialEntities.filter((e: any) => e.id !== entityId)
        });
    };

    return (
        <div className="simulation-design-builder max-w-6xl mx-auto p-6">
            <div className="mb-8">
                <h1 className="text-3xl font-bold mb-2">Design Your Simulation</h1>
                <p className="text-gray-600">Configure entities, physics, and interactions</p>
            </div>

            {/* Tabs */}
            <div className="border-b mb-6">
                <div className="flex gap-4">
                    <button
                        onClick={() => setActiveTab('entities')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'entities'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Entities
                    </button>
                    <button
                        onClick={() => setActiveTab('physics')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'physics'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Physics
                    </button>
                    <button
                        onClick={() => setActiveTab('interactions')}
                        className={`px-4 py-2 font-medium border-b-2 transition-colors ${activeTab === 'interactions'
                                ? 'border-blue-500 text-blue-600'
                                : 'border-transparent text-gray-600 hover:text-gray-900'
                            }`}
                    >
                        Interactions
                    </button>
                </div>
            </div>

            <div className="grid grid-cols-3 gap-6">
                {/* Left Panel - Entity List / Config */}
                <div className="col-span-1 space-y-4">
                    {activeTab === 'entities' && (
                        <>
                            <div className="flex items-center justify-between">
                                <h3 className="font-semibold">Entities</h3>
                                <button
                                    onClick={addEntity}
                                    className="px-3 py-1 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
                                >
                                    + Add
                                </button>
                            </div>

                            <div className="space-y-2">
                                {editedManifest.initialEntities.map((entity: any) => (
                                    <div
                                        key={entity.id}
                                        onClick={() => setSelectedEntity(entity.id)}
                                        className={`p-3 border rounded-lg cursor-pointer transition-all ${selectedEntity === entity.id
                                                ? 'border-blue-500 bg-blue-50'
                                                : 'border-gray-200 hover:border-gray-300'
                                            }`}
                                    >
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <div className="font-medium">{entity.label || entity.id}</div>
                                                <div className="text-xs text-gray-600">{entity.type}</div>
                                            </div>
                                            <button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    removeEntity(entity.id);
                                                }}
                                                className="text-red-600 hover:text-red-800"
                                            >
                                                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                                </svg>
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </>
                    )}

                    {activeTab === 'physics' && (
                        <>
                            <h3 className="font-semibold">Physics Configuration</h3>
                            <div className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Physics Engine
                                    </label>
                                    <select className="w-full px-3 py-2 border rounded-md">
                                        <option value="matter.js">Matter.js</option>
                                        <option value="box2d">Box2D</option>
                                        <option value="custom">Custom</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Gravity (m/s²)
                                    </label>
                                    <input
                                        type="number"
                                        className="w-full px-3 py-2 border rounded-md"
                                        defaultValue="9.8"
                                        step="0.1"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Friction Coefficient
                                    </label>
                                    <input
                                        type="number"
                                        className="w-full px-3 py-2 border rounded-md"
                                        defaultValue="0.1"
                                        step="0.01"
                                        min="0"
                                        max="1"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Restitution (Bounciness)
                                    </label>
                                    <input
                                        type="number"
                                        className="w-full px-3 py-2 border rounded-md"
                                        defaultValue="0.3"
                                        step="0.1"
                                        min="0"
                                        max="1"
                                    />
                                </div>
                            </div>
                        </>
                    )}

                    {activeTab === 'interactions' && (
                        <>
                            <h3 className="font-semibold">Interactive Elements</h3>
                            <div className="space-y-4">
                                <button className="w-full px-4 py-2 border-2 border-dashed rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all">
                                    + Add Slider
                                </button>
                                <button className="w-full px-4 py-2 border-2 border-dashed rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all">
                                    + Add Button
                                </button>
                                <button className="w-full px-4 py-2 border-2 border-dashed rounded-lg hover:border-blue-500 hover:bg-blue-50 transition-all">
                                    + Add Input Field
                                </button>
                            </div>
                        </>
                    )}
                </div>

                {/* Center Panel - Canvas Preview */}
                <div className="col-span-2">
                    <div className="border rounded-lg bg-gray-50 aspect-video flex items-center justify-center">
                        <div className="text-center text-gray-500">
                            <svg className="w-16 h-16 mx-auto mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                            </svg>
                            <p className="font-medium">Canvas Preview</p>
                            <p className="text-sm">Simulation visualization will appear here</p>
                        </div>
                    </div>

                    {/* Entity Properties (when selected) */}
                    {selectedEntity && activeTab === 'entities' && (
                        <div className="mt-4 p-4 border rounded-lg bg-white">
                            <h4 className="font-semibold mb-4">Entity Properties</h4>
                            {(() => {
                                const entity = editedManifest.initialEntities.find((e: any) => e.id === selectedEntity);
                                return (
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Label
                                            </label>
                                            <input
                                                type="text"
                                                className="w-full px-3 py-2 border rounded-md text-sm"
                                                value={entity?.label || ''}
                                                onChange={(e) => updateEntity(selectedEntity, { label: e.target.value })}
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Type
                                            </label>
                                            <select
                                                className="w-full px-3 py-2 border rounded-md text-sm"
                                                value={entity?.type || ''}
                                                onChange={(e) => updateEntity(selectedEntity, { type: e.target.value })}
                                            >
                                                <option value="physics_object">Physics Object</option>
                                                <option value="vector">Vector</option>
                                                <option value="particle">Particle</option>
                                                <option value="constraint">Constraint</option>
                                            </select>
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Position X
                                            </label>
                                            <input
                                                type="number"
                                                className="w-full px-3 py-2 border rounded-md text-sm"
                                                value={entity?.position?.x || 0}
                                                onChange={(e) => updateEntity(selectedEntity, {
                                                    position: { ...entity.position, x: Number(e.target.value) }
                                                })}
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Position Y
                                            </label>
                                            <input
                                                type="number"
                                                className="w-full px-3 py-2 border rounded-md text-sm"
                                                value={entity?.position?.y || 0}
                                                onChange={(e) => updateEntity(selectedEntity, {
                                                    position: { ...entity.position, y: Number(e.target.value) }
                                                })}
                                            />
                                        </div>
                                    </div>
                                );
                            })()}
                        </div>
                    )}
                </div>
            </div>

            {/* Navigation */}
            <div className="flex justify-between items-center mt-8 pt-6 border-t">
                <button
                    onClick={onBack}
                    className="px-6 py-2 border rounded-md hover:bg-gray-50"
                >
                    Back
                </button>
                <button
                    onClick={() => onComplete(editedManifest)}
                    className="px-8 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                >
                    Continue to Animation
                </button>
            </div>
        </div>
    );
}
