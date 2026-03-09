import React, { useCallback } from 'react';
import type { PhysicsEntity, PhysicsConfig } from '../types';

/**
 * @doc.type interface
 * @doc.purpose Props for physics property panel
 * @doc.layer core
 * @doc.pattern Component
 */
export interface PhysicsPropertyPanelProps {
    /** Currently selected entity */
    selectedEntity: PhysicsEntity | null;
    /** Callback to update entity properties */
    onUpdateEntity: (id: string, changes: Partial<PhysicsEntity>) => void;
    /** Callback to delete entity */
    onDeleteEntity: (id: string) => void;
    /** Optional additional className */
    className?: string;
}

/**
 * Property panel for editing physics entity properties
 * @doc.type component
 * @doc.purpose Entity property editor
 * @doc.layer core
 * @doc.pattern Component
 */
export const PhysicsPropertyPanel: React.FC<PhysicsPropertyPanelProps> = ({
    selectedEntity,
    onUpdateEntity,
    onDeleteEntity,
    className = '',
}) => {
    const handlePropertyChange = useCallback(
        (key: string, value: unknown) => {
            if (!selectedEntity) return;
            onUpdateEntity(selectedEntity.id, {
                physics: { ...selectedEntity.physics, [key]: value },
            });
        },
        [selectedEntity, onUpdateEntity]
    );

    const handlePositionChange = useCallback(
        (axis: 'x' | 'y', value: number) => {
            if (!selectedEntity) return;
            onUpdateEntity(selectedEntity.id, { [axis]: value });
        },
        [selectedEntity, onUpdateEntity]
    );

    const handleDimensionChange = useCallback(
        (key: 'width' | 'height' | 'radius', value: number) => {
            if (!selectedEntity) return;
            onUpdateEntity(selectedEntity.id, { [key]: value });
        },
        [selectedEntity, onUpdateEntity]
    );

    const handleColorChange = useCallback(
        (color: string) => {
            if (!selectedEntity) return;
            onUpdateEntity(selectedEntity.id, {
                appearance: { ...selectedEntity.appearance, color },
            });
        },
        [selectedEntity, onUpdateEntity]
    );

    if (!selectedEntity) {
        return (
            <div className={`h-full flex items-center justify-center text-gray-500 dark:text-gray-400 ${className}`}>
                <p className="text-center px-4">Select an entity to edit properties</p>
            </div>
        );
    }

    return (
        <div className={`h-full overflow-y-auto p-4 space-y-4 ${className}`}>
            {/* Header */}
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Properties</h3>
                <button
                    onClick={() => onDeleteEntity(selectedEntity.id)}
                    className="px-3 py-1 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors"
                >
                    Delete
                </button>
            </div>

            {/* Entity Type (read-only) */}
            <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Type
                </label>
                <input
                    type="text"
                    value={selectedEntity.type}
                    disabled
                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white cursor-not-allowed"
                />
            </div>

            {/* Position */}
            <div className="grid grid-cols-2 gap-2">
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        X Position
                    </label>
                    <input
                        type="number"
                        value={Math.round(selectedEntity.x)}
                        onChange={(e) => handlePositionChange('x', parseFloat(e.target.value) || 0)}
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Y Position
                    </label>
                    <input
                        type="number"
                        value={Math.round(selectedEntity.y)}
                        onChange={(e) => handlePositionChange('y', parseFloat(e.target.value) || 0)}
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>
            </div>

            {/* Dimensions */}
            {(selectedEntity.width !== undefined || selectedEntity.height !== undefined) && (
                <div className="grid grid-cols-2 gap-2">
                    {selectedEntity.width !== undefined && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                Width
                            </label>
                            <input
                                type="number"
                                value={selectedEntity.width}
                                onChange={(e) => handleDimensionChange('width', parseFloat(e.target.value) || 1)}
                                min={1}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                            />
                        </div>
                    )}
                    {selectedEntity.height !== undefined && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                Height
                            </label>
                            <input
                                type="number"
                                value={selectedEntity.height}
                                onChange={(e) => handleDimensionChange('height', parseFloat(e.target.value) || 1)}
                                min={1}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                            />
                        </div>
                    )}
                </div>
            )}

            {/* Radius */}
            {selectedEntity.radius !== undefined && (
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Radius
                    </label>
                    <input
                        type="number"
                        value={selectedEntity.radius}
                        onChange={(e) => handleDimensionChange('radius', parseFloat(e.target.value) || 1)}
                        min={1}
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>
            )}

            {/* Color */}
            <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Color
                </label>
                <input
                    type="color"
                    value={selectedEntity.appearance.color}
                    onChange={(e) => handleColorChange(e.target.value)}
                    className="w-full h-10 border border-gray-300 dark:border-gray-600 rounded-md cursor-pointer"
                />
            </div>

            {/* Physics Properties */}
            <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
                <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-3">Physics</h4>

                <div className="space-y-3">
                    {/* Mass */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Mass (kg)
                        </label>
                        <input
                            type="number"
                            step="0.1"
                            min="0"
                            value={selectedEntity.physics.mass}
                            onChange={(e) => handlePropertyChange('mass', parseFloat(e.target.value) || 0)}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                        />
                    </div>

                    {/* Friction */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Friction
                        </label>
                        <input
                            type="range"
                            min="0"
                            max="1"
                            step="0.1"
                            value={selectedEntity.physics.friction}
                            onChange={(e) => handlePropertyChange('friction', parseFloat(e.target.value))}
                            className="w-full accent-blue-500"
                        />
                        <span className="text-xs text-gray-600 dark:text-gray-400">
                            {selectedEntity.physics.friction.toFixed(1)}
                        </span>
                    </div>

                    {/* Restitution */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                            Restitution (Bounciness)
                        </label>
                        <input
                            type="range"
                            min="0"
                            max="1"
                            step="0.1"
                            value={selectedEntity.physics.restitution}
                            onChange={(e) => handlePropertyChange('restitution', parseFloat(e.target.value))}
                            className="w-full accent-blue-500"
                        />
                        <span className="text-xs text-gray-600 dark:text-gray-400">
                            {selectedEntity.physics.restitution.toFixed(1)}
                        </span>
                    </div>

                    {/* Static toggle */}
                    <div className="flex items-center">
                        <input
                            type="checkbox"
                            id="isStatic"
                            checked={selectedEntity.physics.isStatic}
                            onChange={(e) => handlePropertyChange('isStatic', e.target.checked)}
                            className="mr-2 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                        />
                        <label htmlFor="isStatic" className="text-sm text-gray-700 dark:text-gray-300">
                            Static (Non-moving)
                        </label>
                    </div>
                </div>
            </div>
        </div>
    );
};

PhysicsPropertyPanel.displayName = 'PhysicsPropertyPanel';

/**
 * @doc.type interface
 * @doc.purpose Props for physics config panel
 * @doc.layer core
 * @doc.pattern Component
 */
export interface PhysicsConfigPanelProps {
    /** Current physics configuration */
    config: PhysicsConfig;
    /** Callback to update config */
    onConfigChange: (config: Partial<PhysicsConfig>) => void;
    /** Optional additional className */
    className?: string;
}

/**
 * Panel for global physics configuration
 * @doc.type component
 * @doc.purpose Physics world settings editor
 * @doc.layer core
 * @doc.pattern Component
 */
export const PhysicsConfigPanel: React.FC<PhysicsConfigPanelProps> = ({
    config,
    onConfigChange,
    className = '',
}) => {
    return (
        <div className={`p-4 ${className}`}>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                Physics Settings
            </h3>

            <div className="space-y-3">
                {/* Gravity */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Gravity (m/s²)
                    </label>
                    <input
                        type="number"
                        step="0.1"
                        min="0"
                        max="100"
                        value={config.gravity}
                        onChange={(e) => onConfigChange({ gravity: parseFloat(e.target.value) || 0 })}
                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>

                {/* Time Scale */}
                <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        Time Scale
                    </label>
                    <input
                        type="range"
                        min="0.1"
                        max="2"
                        step="0.1"
                        value={config.timeScale}
                        onChange={(e) => onConfigChange({ timeScale: parseFloat(e.target.value) })}
                        className="w-full accent-blue-500"
                    />
                    <span className="text-xs text-gray-600 dark:text-gray-400">
                        {config.timeScale.toFixed(1)}x
                    </span>
                </div>

                {/* Collision toggle */}
                <div className="flex items-center">
                    <input
                        type="checkbox"
                        id="collisionEnabled"
                        checked={config.collisionEnabled}
                        onChange={(e) => onConfigChange({ collisionEnabled: e.target.checked })}
                        className="mr-2 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                    <label htmlFor="collisionEnabled" className="text-sm text-gray-700 dark:text-gray-300">
                        Enable Collisions
                    </label>
                </div>
            </div>
        </div>
    );
};

PhysicsConfigPanel.displayName = 'PhysicsConfigPanel';
