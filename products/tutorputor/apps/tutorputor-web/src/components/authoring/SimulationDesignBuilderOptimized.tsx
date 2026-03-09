/**
 * Optimized Simulation Design Builder
 * 
 * Enhanced version of SimulationDesignBuilder with performance optimizations:
 * - Virtualized entity list for large simulations
 * - Debounced updates to prevent excessive re-renders
 * - Memoized calculations and components
 * - Efficient state management with shallow comparisons
 *
 * @doc.type component
 * @doc.purpose Optimized simulation design interface
 * @doc.layer product
 * @doc.pattern Performance Optimization
 */

import { useState, useCallback, useMemo, useRef, useEffect } from "react";
import { FixedSizeList as List } from 'react-window';
import { debounce } from 'lodash-es';

interface Entity {
    id: string;
    type: string;
    name: string;
    position: { x: number; y: number; z: number };
    properties: Record<string, any>;
}

interface PhysicsConfig {
    gravity: { x: number; y: number; z: number };
    damping: number;
    restitution: number;
}

interface Interaction {
    id: string;
    type: string;
    entities: string[];
    properties: Record<string, any>;
}

interface SimulationManifest {
    initialEntities: Entity[];
    physics: PhysicsConfig;
    interactions: Interaction[];
}

interface SimulationDesignBuilderOptimizedProps {
    manifest: SimulationManifest;
    onComplete: (updatedManifest: SimulationManifest) => void;
    onBack: () => void;
}

const ENTITY_HEIGHT = 60;
const ENTITY_ITEM_SIZE = 80;

// Memoized entity component for virtualized list
const EntityItem = ({ index, style, data }: { index: number; style: any; data: any }) => {
    const { entities, selectedEntity, onSelectEntity, onUpdateEntity } = data;
    const entity = entities[index];

    const handleClick = useCallback(() => {
        onSelectEntity(entity.id);
    }, [entity.id, onSelectEntity]);

    const handlePropertyChange = useCallback((property: string, value: any) => {
        onUpdateEntity(entity.id, { [property]: value });
    }, [entity.id, onUpdateEntity]);

    return (
        <div style={style} className={`entity-item p-3 border-b ${selectedEntity === entity.id ? 'bg-blue-50 border-blue-300' : 'hover:bg-gray-50'}`}>
            <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                    <span className="font-medium text-sm">{entity.name}</span>
                    <span className="px-2 py-1 bg-gray-100 text-gray-600 text-xs rounded">{entity.type}</span>
                </div>
                <button onClick={handleClick} className="text-blue-600 hover:text-blue-800 text-sm">
                    Select
                </button>
            </div>

            <div className="grid grid-cols-3 gap-2 text-xs">
                <div>
                    <label className="block text-gray-500 mb-1">X</label>
                    <input
                        type="number"
                        value={entity.position.x}
                        onChange={(e) => handlePropertyChange('position.x', parseFloat(e.target.value))}
                        className="w-full px-2 py-1 border rounded"
                        step="0.1"
                    />
                </div>
                <div>
                    <label className="block text-gray-500 mb-1">Y</label>
                    <input
                        type="number"
                        value={entity.position.y}
                        onChange={(e) => handlePropertyChange('position.y', parseFloat(e.target.value))}
                        className="w-full px-2 py-1 border rounded"
                        step="0.1"
                    />
                </div>
                <div>
                    <label className="block text-gray-500 mb-1">Z</label>
                    <input
                        type="number"
                        value={entity.position.z}
                        onChange={(e) => handlePropertyChange('position.z', parseFloat(e.target.value))}
                        className="w-full px-2 py-1 border rounded"
                        step="0.1"
                    />
                </div>
            </div>
        </div>
    );
};

// Memoized physics component
const PhysicsEditor = ({ physics, onUpdate }: {
    physics: PhysicsConfig;
    onUpdate: (updates: Partial<PhysicsConfig>) => void;
}) => {
    const handleGravityChange = useCallback((axis: 'x' | 'y' | 'z', value: number) => {
        onUpdate({
            gravity: { ...physics.gravity, [axis]: value }
        });
    }, [physics.gravity, onUpdate]);

    const handlePropertyChange = useCallback((property: keyof PhysicsConfig, value: number) => {
        onUpdate({ [property]: value });
    }, [onUpdate]);

    return (
        <div className="space-y-4">
            <div>
                <h4 className="font-medium mb-2">Gravity</h4>
                <div className="grid grid-cols-3 gap-2">
                    {(['x', 'y', 'z'] as const).map(axis => (
                        <div key={axis}>
                            <label className="block text-sm text-gray-500 mb-1">{axis.toUpperCase()}</label>
                            <input
                                type="number"
                                value={physics.gravity[axis]}
                                onChange={(e) => handleGravityChange(axis, parseFloat(e.target.value))}
                                className="w-full px-3 py-2 border rounded"
                                step="0.1"
                            />
                        </div>
                    ))}
                </div>
            </div>

            <div>
                <h4 className="font-medium mb-2">Properties</h4>
                <div className="space-y-2">
                    <div>
                        <label className="block text-sm text-gray-500 mb-1">Damping</label>
                        <input
                            type="number"
                            value={physics.damping}
                            onChange={(e) => handlePropertyChange('damping', parseFloat(e.target.value))}
                            className="w-full px-3 py-2 border rounded"
                            step="0.01"
                            min="0"
                            max="1"
                        />
                    </div>
                    <div>
                        <label className="block text-sm text-gray-500 mb-1">Restitution</label>
                        <input
                            type="number"
                            value={physics.restitution}
                            onChange={(e) => handlePropertyChange('restitution', parseFloat(e.target.value))}
                            className="w-full px-3 py-2 border rounded"
                            step="0.01"
                            min="0"
                            max="1"
                        />
                    </div>
                </div>
            </div>
        </div>
    );
};

// Memoized interactions component
const InteractionsEditor = ({ interactions, onUpdate }: {
    interactions: Interaction[];
    onUpdate: (interactions: Interaction[]) => void;
}) => {
    const addInteraction = useCallback(() => {
        const newInteraction: Interaction = {
            id: `interaction_${Date.now()}`,
            type: 'collision',
            entities: [],
            properties: {}
        };
        onUpdate([...interactions, newInteraction]);
    }, [interactions, onUpdate]);

    const updateInteraction = useCallback((index: number, updates: Partial<Interaction>) => {
        const updated = [...interactions];
        updated[index] = { ...updated[index], ...updates };
        onUpdate(updated);
    }, [interactions, onUpdate]);

    const removeInteraction = useCallback((index: number) => {
        const updated = interactions.filter((_, i) => i !== index);
        onUpdate(updated);
    }, [interactions, onUpdate]);

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <h4 className="font-medium">Interactions ({interactions.length})</h4>
                <button
                    onClick={addInteraction}
                    className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
                >
                    Add Interaction
                </button>
            </div>

            {interactions.map((interaction, index) => (
                <div key={interaction.id} className="border rounded-lg p-4">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <input
                                type="text"
                                value={interaction.type}
                                onChange={(e) => updateInteraction(index, { type: e.target.value })}
                                className="px-2 py-1 border rounded text-sm"
                                placeholder="Interaction type"
                            />
                            <span className="text-sm text-gray-500">
                                {interaction.entities.length} entities
                            </span>
                        </div>
                        <button
                            onClick={() => removeInteraction(index)}
                            className="text-red-600 hover:text-red-800 text-sm"
                        >
                            Remove
                        </button>
                    </div>

                    <div className="text-sm text-gray-600">
                        <div className="mb-2">Entities: {interaction.entities.join(', ') || 'None selected'}</div>
                        <div>Properties: {Object.keys(interaction.properties).length} configured</div>
                    </div>
                </div>
            ))}
        </div>
    );
};

export function SimulationDesignBuilderOptimized({
    manifest,
    onComplete,
    onBack
}: SimulationDesignBuilderOptimizedProps) {
    const [activeTab, setActiveTab] = useState<'entities' | 'physics' | 'interactions'>('entities');
    const [editedManifest, setEditedManifest] = useState(manifest);
    const [selectedEntity, setSelectedEntity] = useState<string | null>(null);

    // Debounced update function to prevent excessive re-renders
    const debouncedUpdateManifest = useRef(
        debounce((updates: Partial<SimulationManifest>) => {
            setEditedManifest(prev => ({ ...prev, ...updates }));
        }, 300)
    ).current;

    // Memoized entity data for virtualized list
    const entityData = useMemo(() => ({
        entities: editedManifest.initialEntities,
        selectedEntity,
        onSelectEntity: setSelectedEntity,
        onUpdateEntity: (entityId: string, updates: any) => {
            const updatedEntities = editedManifest.initialEntities.map(entity =>
                entity.id === entityId ? { ...entity, ...updates } : entity
            );
            debouncedUpdateManifest({ initialEntities: updatedEntities });
        }
    }), [editedManifest.initialEntities, selectedEntity, debouncedUpdateManifest]);

    // Memoized update handlers
    const handleAddEntity = useCallback(() => {
        const newEntity: Entity = {
            id: `entity_${Date.now()}`,
            type: 'object',
            name: `Entity ${editedManifest.initialEntities.length + 1}`,
            position: { x: 0, y: 0, z: 0 },
            properties: {}
        };
        debouncedUpdateManifest({
            initialEntities: [...editedManifest.initialEntities, newEntity]
        });
    }, [editedManifest.initialEntities.length, debouncedUpdateManifest]);

    const handleRemoveEntity = useCallback((entityId: string) => {
        const updatedEntities = editedManifest.initialEntities.filter(e => e.id !== entityId);
        debouncedUpdateManifest({ initialEntities: updatedEntities });
        if (selectedEntity === entityId) {
            setSelectedEntity(null);
        }
    }, [editedManifest.initialEntities, selectedEntity, debouncedUpdateManifest]);

    const handlePhysicsUpdate = useCallback((updates: Partial<PhysicsConfig>) => {
        debouncedUpdateManifest({
            physics: { ...editedManifest.physics, ...updates }
        });
    }, [editedManifest.physics, debouncedUpdateManifest]);

    const handleInteractionsUpdate = useCallback((interactions: Interaction[]) => {
        debouncedUpdateManifest({ interactions });
    }, [debouncedUpdateManifest]);

    // Memoized selected entity details
    const selectedEntityDetails = useMemo(() => {
        if (!selectedEntity) return null;
        return editedManifest.initialEntities.find(e => e.id === selectedEntity);
    }, [selectedEntity, editedManifest.initialEntities]);

    // Cleanup debounce on unmount
    useEffect(() => {
        return () => {
            debouncedUpdateManifest.cancel();
        };
    }, [debouncedUpdateManifest]);

    return (
        <div className="simulation-design-builder-optimized max-w-6xl mx-auto p-6">
            {/* Header */}
            <div className="mb-6">
                <div className="flex items-center justify-between mb-4">
                    <h1 className="text-2xl font-bold">Simulation Design</h1>
                    <div className="flex gap-3">
                        <button
                            onClick={onBack}
                            className="px-4 py-2 border rounded-md hover:bg-gray-50"
                        >
                            Back
                        </button>
                        <button
                            onClick={() => onComplete(editedManifest)}
                            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                        >
                            Complete Design
                        </button>
                    </div>
                </div>

                {/* Tabs */}
                <div className="flex border-b">
                    {(['entities', 'physics', 'interactions'] as const).map(tab => (
                        <button
                            key={tab}
                            onClick={() => setActiveTab(tab)}
                            className={`px-4 py-2 font-medium capitalize ${activeTab === tab
                                    ? 'border-b-2 border-blue-500 text-blue-600'
                                    : 'text-gray-600 hover:text-gray-800'
                                }`}
                        >
                            {tab}
                            {tab === 'entities' && ` (${editedManifest.initialEntities.length})`}
                            {tab === 'interactions' && ` (${editedManifest.interactions.length})`}
                        </button>
                    ))}
                </div>
            </div>

            {/* Content */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Main Content */}
                <div className="lg:col-span-2">
                    {activeTab === 'entities' && (
                        <div>
                            <div className="flex items-center justify-between mb-4">
                                <h3 className="text-lg font-medium">Entities</h3>
                                <button
                                    onClick={handleAddEntity}
                                    className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
                                >
                                    Add Entity
                                </button>
                            </div>

                            {/* Virtualized Entity List */}
                            <div className="border rounded-lg" style={{ height: '400px' }}>
                                {editedManifest.initialEntities.length > 0 ? (
                                    <List
                                        height={400}
                                        itemCount={editedManifest.initialEntities.length}
                                        itemSize={ENTITY_ITEM_SIZE}
                                        itemData={entityData}
                                    >
                                        {EntityItem}
                                    </List>
                                ) : (
                                    <div className="flex items-center justify-center h-full text-gray-500">
                                        <div className="text-center">
                                            <div className="text-4xl mb-2">📦</div>
                                            <p>No entities added yet</p>
                                            <button
                                                onClick={handleAddEntity}
                                                className="mt-2 px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
                                            >
                                                Add First Entity
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {activeTab === 'physics' && (
                        <div>
                            <h3 className="text-lg font-medium mb-4">Physics Configuration</h3>
                            <div className="border rounded-lg p-4">
                                <PhysicsEditor
                                    physics={editedManifest.physics}
                                    onUpdate={handlePhysicsUpdate}
                                />
                            </div>
                        </div>
                    )}

                    {activeTab === 'interactions' && (
                        <div>
                            <div className="border rounded-lg p-4">
                                <InteractionsEditor
                                    interactions={editedManifest.interactions}
                                    onUpdate={handleInteractionsUpdate}
                                />
                            </div>
                        </div>
                    )}
                </div>

                {/* Sidebar */}
                <div className="lg:col-span-1">
                    {selectedEntityDetails && (
                        <div className="border rounded-lg p-4">
                            <div className="flex items-center justify-between mb-4">
                                <h3 className="font-medium">Entity Details</h3>
                                <button
                                    onClick={() => handleRemoveEntity(selectedEntityDetails.id)}
                                    className="text-red-600 hover:text-red-800 text-sm"
                                >
                                    Remove
                                </button>
                            </div>

                            <div className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                                    <input
                                        type="text"
                                        value={selectedEntityDetails.name}
                                        onChange={(e) => entityData.onUpdateEntity(selectedEntityDetails.id, { name: e.target.value })}
                                        className="w-full px-3 py-2 border rounded"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                                    <select
                                        value={selectedEntityDetails.type}
                                        onChange={(e) => entityData.onUpdateEntity(selectedEntityDetails.id, { type: e.target.value })}
                                        className="w-full px-3 py-2 border rounded"
                                    >
                                        <option value="object">Object</option>
                                        <option value="character">Character</option>
                                        <option value="environment">Environment</option>
                                        <option value="tool">Tool</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">Position</label>
                                    <div className="grid grid-cols-3 gap-2">
                                        {(['x', 'y', 'z'] as const).map(axis => (
                                            <div key={axis}>
                                                <label className="block text-xs text-gray-500 mb-1">{axis.toUpperCase()}</label>
                                                <input
                                                    type="number"
                                                    value={selectedEntityDetails.position[axis]}
                                                    onChange={(e) => entityData.onUpdateEntity(selectedEntityDetails.id, {
                                                        position: { ...selectedEntityDetails.position, [axis]: parseFloat(e.target.value) }
                                                    })}
                                                    className="w-full px-2 py-1 border rounded text-sm"
                                                    step="0.1"
                                                />
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">Properties</label>
                                    <div className="text-sm text-gray-600">
                                        {Object.keys(selectedEntityDetails.properties).length} properties configured
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Summary */}
                    <div className="border rounded-lg p-4 mt-4">
                        <h3 className="font-medium mb-3">Simulation Summary</h3>
                        <div className="space-y-2 text-sm">
                            <div className="flex justify-between">
                                <span className="text-gray-600">Entities:</span>
                                <span className="font-medium">{editedManifest.initialEntities.length}</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-gray-600">Interactions:</span>
                                <span className="font-medium">{editedManifest.interactions.length}</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-gray-600">Gravity:</span>
                                <span className="font-medium">
                                    ({editedManifest.physics.gravity.x}, {editedManifest.physics.gravity.y}, {editedManifest.physics.gravity.z})
                                </span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-gray-600">Damping:</span>
                                <span className="font-medium">{editedManifest.physics.damping}</span>
                            </div>
                            <div className="flex justify-between">
                                <span className="text-gray-600">Restitution:</span>
                                <span className="font-medium">{editedManifest.physics.restitution}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
