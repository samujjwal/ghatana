import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useRef } from 'react';
/**
 * Toolbar component for simulation actions
 * @doc.type component
 * @doc.purpose Header toolbar with undo/redo and import/export
 * @doc.layer core
 * @doc.pattern Component
 */
export const SimulationToolbar = ({ title = 'Visual Simulation Builder', subtitle, entityCount, historyStatus, canUndo, canRedo, isPreviewMode, onUndo, onRedo, onTogglePreview, onImport, onExport, onClearAll, className = '', children, }) => {
    const fileInputRef = useRef(null);
    const handleImportClick = () => {
        fileInputRef.current?.click();
    };
    const handleFileChange = (e) => {
        const file = e.target.files?.[0];
        if (file) {
            onImport(file);
        }
        e.target.value = '';
    };
    const defaultSubtitle = subtitle ??
        `Drag entities from the toolbox to build your physics simulation · ${entityCount} entities · History: ${historyStatus.current}/${historyStatus.total}`;
    return (_jsxs("div", { className: `flex items-center justify-between px-6 py-4 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 ${className}`, children: [_jsxs("div", { children: [_jsx("h1", { className: "text-2xl font-bold text-gray-900 dark:text-white", children: title }), _jsx("p", { className: "text-sm text-gray-600 dark:text-gray-400 mt-1", children: defaultSubtitle })] }), _jsxs("div", { className: "flex items-center gap-3", children: [_jsxs("div", { className: "flex items-center gap-1 border border-gray-200 dark:border-gray-600 rounded-md", children: [_jsx("button", { onClick: onUndo, disabled: !canUndo, className: "px-3 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors", title: "Undo (Cmd/Ctrl+Z)", children: "\u21B6 Undo" }), _jsx("div", { className: "w-px h-6 bg-gray-200 dark:bg-gray-600" }), _jsx("button", { onClick: onRedo, disabled: !canRedo, className: "px-3 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors", title: "Redo (Cmd/Ctrl+Y)", children: "\u21B7 Redo" })] }), _jsx("button", { onClick: onTogglePreview, className: `px-4 py-2 text-sm rounded-md transition-colors ${isPreviewMode
                            ? 'bg-green-600 text-white hover:bg-green-700'
                            : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'}`, children: isPreviewMode ? '⏸ Edit Mode' : '▶ Preview' }), _jsx("button", { onClick: handleImportClick, className: "px-4 py-2 text-sm bg-gray-600 text-white rounded-md hover:bg-gray-700 transition-colors", children: "Import" }), _jsx("button", { onClick: onExport, className: "px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors", children: "Export" }), _jsx("button", { onClick: onClearAll, className: "px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-md transition-colors", children: "Clear All" }), children, _jsx("input", { ref: fileInputRef, type: "file", accept: ".json", onChange: handleFileChange, className: "hidden" })] })] }));
};
SimulationToolbar.displayName = 'SimulationToolbar';
//# sourceMappingURL=SimulationToolbar.js.map