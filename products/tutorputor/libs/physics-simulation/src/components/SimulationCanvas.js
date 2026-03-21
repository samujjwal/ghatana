import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useRef, useCallback } from "react";
import { Stage, Layer } from "react-konva";
import { useDrop } from "react-dnd";
import { KonvaEntityRenderer } from "../rendering";
/**
 * Main simulation canvas with Konva rendering
 * @doc.type component
 * @doc.purpose Physics simulation canvas with drag-and-drop
 * @doc.layer core
 * @doc.pattern Component
 */
export const SimulationCanvas = ({ entities, selectedEntityId, isPreviewMode, onSelectEntity, onEntityMove, onEntityDrop, dndType = "PHYSICS_ENTITY", width, height, className = "", }) => {
    const containerRef = useRef(null);
    const stageRef = useRef(null);
    // Drop handler for new entities
    const [{ isOver }, drop] = useDrop(() => ({
        accept: dndType,
        drop: (item, monitor) => {
            const offset = monitor.getClientOffset();
            if (!offset || !containerRef.current)
                return;
            const rect = containerRef.current.getBoundingClientRect();
            const x = offset.x - rect.left;
            const y = offset.y - rect.top;
            onEntityDrop(item.entityType, x, y, item.color);
        },
        collect: (monitor) => ({
            isOver: monitor.isOver(),
        }),
    }));
    // Combine refs
    const setRefs = useCallback((node) => {
        containerRef.current = node;
        drop(node);
    }, [drop]);
    // Get canvas dimensions
    const canvasWidth = width ?? containerRef.current?.clientWidth ?? 800;
    const canvasHeight = height ?? containerRef.current?.clientHeight ?? 600;
    return (_jsxs("div", { ref: setRefs, role: "application", "aria-label": "Physics Simulation Canvas", tabIndex: 0, className: `
        relative flex-1 outline-none
        ${isOver && !isPreviewMode ? "bg-blue-50 dark:bg-blue-900/10" : "bg-gray-100 dark:bg-gray-900"}
        ${className}
      `, children: [isPreviewMode && (_jsxs("div", { className: "absolute top-4 left-4 z-10 px-4 py-2 bg-green-600 text-white rounded-lg shadow-lg flex items-center gap-2", children: [_jsx("span", { className: "text-xl", children: "\u25B6" }), _jsxs("div", { children: [_jsx("div", { className: "font-semibold", children: "Preview Mode" }), _jsx("div", { className: "text-xs opacity-90", children: "Entities locked for viewing" })] })] })), entities.length === 0 && (_jsx("div", { className: "absolute inset-0 flex items-center justify-center pointer-events-none", children: _jsxs("div", { className: "text-center", children: [_jsx("p", { className: "text-xl text-gray-400 dark:text-gray-500", children: "Drag entities here to start building" }), _jsx("p", { className: "text-sm text-gray-400 dark:text-gray-500 mt-2", children: "Drop from the toolbox on the left" })] }) })), _jsx(Stage, { ref: stageRef, width: canvasWidth, height: canvasHeight, listening: !isPreviewMode, children: _jsx(Layer, { children: entities.map((entity) => (_jsx(KonvaEntityRenderer, { entity: entity, isSelected: !isPreviewMode && entity.id === selectedEntityId, isDraggable: !isPreviewMode, onSelect: isPreviewMode ? () => { } : onSelectEntity, onDragMove: isPreviewMode ? () => { } : onEntityMove }, entity.id))) }) })] }));
};
SimulationCanvas.displayName = "SimulationCanvas";
//# sourceMappingURL=SimulationCanvas.js.map