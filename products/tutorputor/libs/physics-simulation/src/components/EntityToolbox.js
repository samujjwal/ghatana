import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useDrag } from "react-dnd";
/**
 * Draggable toolbox item component
 * @doc.type component
 * @doc.purpose Drag-and-drop entity palette item
 * @doc.layer core
 * @doc.pattern Component
 */
export const DraggableToolboxItem = ({ item, className = "", dndType = "PHYSICS_ENTITY", }) => {
    const [{ isDragging }, drag] = useDrag(() => ({
        type: dndType,
        item: { entityType: item.type, color: item.defaultColor },
        collect: (monitor) => ({
            isDragging: monitor.isDragging(),
        }),
    }));
    return (_jsxs("div", { ref: drag, role: "button", tabIndex: 0, "aria-label": `Drag ${item.label}`, className: `
        flex flex-col items-center justify-center p-4 rounded-lg cursor-move
        border-2 border-gray-200 dark:border-gray-700
        hover:border-blue-400 dark:hover:border-blue-500
        focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
        transition-all duration-200 select-none
        ${isDragging ? "opacity-50 scale-95" : "opacity-100"}
        ${className}
      `, style: { backgroundColor: `${item.defaultColor}20` }, title: item.description, onKeyDown: (e) => {
            if (e.key === "Enter" || e.key === " ") {
                // Logic to simulate drag start or selection could go here
                // For now, at least allow focusing
            }
        }, children: [_jsx("div", { className: "text-3xl mb-2", children: item.icon }), _jsx("div", { className: "text-xs font-medium text-gray-700 dark:text-gray-300", children: item.label }), item.isPremium && (_jsx("span", { className: "mt-1 text-[10px] px-1.5 py-0.5 bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200 rounded", children: "Premium" }))] }));
};
DraggableToolboxItem.displayName = "DraggableToolboxItem";
/**
 * Entity toolbox grid component
 * @doc.type component
 * @doc.purpose Grid of draggable entity items
 * @doc.layer core
 * @doc.pattern Component
 */
export const EntityToolbox = ({ items, columns = 2, title = "Entity Toolbox", dndType = "PHYSICS_ENTITY", className = "", }) => {
    const gridCols = {
        2: "grid-cols-2",
        3: "grid-cols-3",
        4: "grid-cols-4",
    };
    return (_jsxs("div", { className: `p-4 ${className}`, children: [title && (_jsx("h3", { className: "text-lg font-semibold text-gray-900 dark:text-white mb-4", children: title })), _jsx("div", { className: `grid ${gridCols[columns]} gap-3`, children: items.map((item) => (_jsx(DraggableToolboxItem, { item: item, dndType: dndType }, item.type))) })] }));
};
EntityToolbox.displayName = "EntityToolbox";
//# sourceMappingURL=EntityToolbox.js.map