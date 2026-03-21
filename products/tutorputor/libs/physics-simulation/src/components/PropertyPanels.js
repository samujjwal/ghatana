import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useCallback } from 'react';
/**
 * Property panel for editing physics entity properties
 * @doc.type component
 * @doc.purpose Entity property editor
 * @doc.layer core
 * @doc.pattern Component
 */
export const PhysicsPropertyPanel = ({ selectedEntity, onUpdateEntity, onDeleteEntity, className = '', }) => {
    const handlePropertyChange = useCallback((key, value) => {
        if (!selectedEntity)
            return;
        onUpdateEntity(selectedEntity.id, {
            physics: { ...selectedEntity.physics, [key]: value },
        });
    }, [selectedEntity, onUpdateEntity]);
    const handlePositionChange = useCallback((axis, value) => {
        if (!selectedEntity)
            return;
        onUpdateEntity(selectedEntity.id, { [axis]: value });
    }, [selectedEntity, onUpdateEntity]);
    const handleDimensionChange = useCallback((key, value) => {
        if (!selectedEntity)
            return;
        onUpdateEntity(selectedEntity.id, { [key]: value });
    }, [selectedEntity, onUpdateEntity]);
    const handleColorChange = useCallback((color) => {
        if (!selectedEntity)
            return;
        onUpdateEntity(selectedEntity.id, {
            appearance: { ...selectedEntity.appearance, color },
        });
    }, [selectedEntity, onUpdateEntity]);
    if (!selectedEntity) {
        return (_jsx("div", { className: `h-full flex items-center justify-center text-gray-500 dark:text-gray-400 ${className}`, children: _jsx("p", { className: "text-center px-4", children: "Select an entity to edit properties" }) }));
    }
    return (_jsxs("div", { className: `h-full overflow-y-auto p-4 space-y-4 ${className}`, children: [_jsxs("div", { className: "flex items-center justify-between mb-4", children: [_jsx("h3", { className: "text-lg font-semibold text-gray-900 dark:text-white", children: "Properties" }), _jsx("button", { onClick: () => onDeleteEntity(selectedEntity.id), className: "px-3 py-1 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors", children: "Delete" })] }), _jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Type" }), _jsx("input", { type: "text", value: selectedEntity.type, disabled: true, className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-white cursor-not-allowed" })] }), _jsxs("div", { className: "grid grid-cols-2 gap-2", children: [_jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "X Position" }), _jsx("input", { type: "number", value: Math.round(selectedEntity.x), onChange: (e) => handlePositionChange('x', parseFloat(e.target.value) || 0), className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white" })] }), _jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Y Position" }), _jsx("input", { type: "number", value: Math.round(selectedEntity.y), onChange: (e) => handlePositionChange('y', parseFloat(e.target.value) || 0), className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white" })] })] }), (selectedEntity.width !== undefined || selectedEntity.height !== undefined) && (_jsxs("div", { className: "grid grid-cols-2 gap-2", children: [selectedEntity.width !== undefined && (_jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Width" }), _jsx("input", { type: "number", value: selectedEntity.width, onChange: (e) => handleDimensionChange('width', parseFloat(e.target.value) || 1), min: 1, className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white" })] })), selectedEntity.height !== undefined && (_jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Height" }), _jsx("input", { type: "number", value: selectedEntity.height, onChange: (e) => handleDimensionChange('height', parseFloat(e.target.value) || 1), min: 1, className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white" })] }))] })), selectedEntity.radius !== undefined && (_jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Radius" }), _jsx("input", { type: "number", value: selectedEntity.radius, onChange: (e) => handleDimensionChange('radius', parseFloat(e.target.value) || 1), min: 1, className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white" })] })), _jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Color" }), _jsx("input", { type: "color", value: selectedEntity.appearance.color, onChange: (e) => handleColorChange(e.target.value), className: "w-full h-10 border border-gray-300 dark:border-gray-600 rounded-md cursor-pointer" })] }), _jsxs("div", { className: "pt-4 border-t border-gray-200 dark:border-gray-700", children: [_jsx("h4", { className: "text-sm font-semibold text-gray-900 dark:text-white mb-3", children: "Physics" }), _jsxs("div", { className: "space-y-3", children: [_jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Mass (kg)" }), _jsx("input", { type: "number", step: "0.1", min: "0", value: selectedEntity.physics.mass, onChange: (e) => handlePropertyChange('mass', parseFloat(e.target.value) || 0), className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white" })] }), _jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Friction" }), _jsx("input", { type: "range", min: "0", max: "1", step: "0.1", value: selectedEntity.physics.friction, onChange: (e) => handlePropertyChange('friction', parseFloat(e.target.value)), className: "w-full accent-blue-500" }), _jsx("span", { className: "text-xs text-gray-600 dark:text-gray-400", children: selectedEntity.physics.friction.toFixed(1) })] }), _jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Restitution (Bounciness)" }), _jsx("input", { type: "range", min: "0", max: "1", step: "0.1", value: selectedEntity.physics.restitution, onChange: (e) => handlePropertyChange('restitution', parseFloat(e.target.value)), className: "w-full accent-blue-500" }), _jsx("span", { className: "text-xs text-gray-600 dark:text-gray-400", children: selectedEntity.physics.restitution.toFixed(1) })] }), _jsxs("div", { className: "flex items-center", children: [_jsx("input", { type: "checkbox", id: "isStatic", checked: selectedEntity.physics.isStatic, onChange: (e) => handlePropertyChange('isStatic', e.target.checked), className: "mr-2 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" }), _jsx("label", { htmlFor: "isStatic", className: "text-sm text-gray-700 dark:text-gray-300", children: "Static (Non-moving)" })] })] })] })] }));
};
PhysicsPropertyPanel.displayName = 'PhysicsPropertyPanel';
/**
 * Panel for global physics configuration
 * @doc.type component
 * @doc.purpose Physics world settings editor
 * @doc.layer core
 * @doc.pattern Component
 */
export const PhysicsConfigPanel = ({ config, onConfigChange, className = '', }) => {
    return (_jsxs("div", { className: `p-4 ${className}`, children: [_jsx("h3", { className: "text-lg font-semibold text-gray-900 dark:text-white mb-4", children: "Physics Settings" }), _jsxs("div", { className: "space-y-3", children: [_jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Gravity (m/s\u00B2)" }), _jsx("input", { type: "number", step: "0.1", min: "0", max: "100", value: config.gravity, onChange: (e) => onConfigChange({ gravity: parseFloat(e.target.value) || 0 }), className: "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-white" })] }), _jsxs("div", { children: [_jsx("label", { className: "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1", children: "Time Scale" }), _jsx("input", { type: "range", min: "0.1", max: "2", step: "0.1", value: config.timeScale, onChange: (e) => onConfigChange({ timeScale: parseFloat(e.target.value) }), className: "w-full accent-blue-500" }), _jsxs("span", { className: "text-xs text-gray-600 dark:text-gray-400", children: [config.timeScale.toFixed(1), "x"] })] }), _jsxs("div", { className: "flex items-center", children: [_jsx("input", { type: "checkbox", id: "collisionEnabled", checked: config.collisionEnabled, onChange: (e) => onConfigChange({ collisionEnabled: e.target.checked }), className: "mr-2 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" }), _jsx("label", { htmlFor: "collisionEnabled", className: "text-sm text-gray-700 dark:text-gray-300", children: "Enable Collisions" })] })] })] }));
};
PhysicsConfigPanel.displayName = 'PhysicsConfigPanel';
//# sourceMappingURL=PropertyPanels.js.map