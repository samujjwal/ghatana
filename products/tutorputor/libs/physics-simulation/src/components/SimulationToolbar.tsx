import React, { useRef } from 'react';

/**
 * @doc.type interface
 * @doc.purpose Props for simulation header toolbar
 * @doc.layer core
 * @doc.pattern Component
 */
export interface SimulationToolbarProps {
    /** Title for the simulation */
    title?: string;
    /** Subtitle/description */
    subtitle?: string;
    /** Number of entities */
    entityCount: number;
    /** History status */
    historyStatus: { current: number; total: number };
    /** Whether undo is available */
    canUndo: boolean;
    /** Whether redo is available */
    canRedo: boolean;
    /** Whether in preview mode */
    isPreviewMode: boolean;
    /** Undo callback */
    onUndo: () => void;
    /** Redo callback */
    onRedo: () => void;
    /** Toggle preview mode */
    onTogglePreview: () => void;
    /** Import callback */
    onImport: (file: File) => void;
    /** Export callback */
    onExport: () => void;
    /** Clear all callback */
    onClearAll: () => void;
    /** Optional additional className */
    className?: string;
    /** Optional children to render in the toolbar */
    children?: React.ReactNode;
}

/**
 * Toolbar component for simulation actions
 * @doc.type component
 * @doc.purpose Header toolbar with undo/redo and import/export
 * @doc.layer core
 * @doc.pattern Component
 */
export const SimulationToolbar: React.FC<SimulationToolbarProps> = ({
    title = 'Visual Simulation Builder',
    subtitle,
    entityCount,
    historyStatus,
    canUndo,
    canRedo,
    isPreviewMode,
    onUndo,
    onRedo,
    onTogglePreview,
    onImport,
    onExport,
    onClearAll,
    className = '',
    children,
}) => {
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleImportClick = () => {
        fileInputRef.current?.click();
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            onImport(file);
        }
        e.target.value = '';
    };

    const defaultSubtitle =
        subtitle ??
        `Drag entities from the toolbox to build your physics simulation · ${entityCount} entities · History: ${historyStatus.current}/${historyStatus.total}`;

    return (
        <div
            className={`flex items-center justify-between px-6 py-4 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 ${className}`}
        >
            {/* Title Section */}
            <div>
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white">{title}</h1>
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{defaultSubtitle}</p>
            </div>

            {/* Actions */}
            <div className="flex items-center gap-3">
                {/* Undo/Redo */}
                <div className="flex items-center gap-1 border border-gray-200 dark:border-gray-600 rounded-md">
                    <button
                        onClick={onUndo}
                        disabled={!canUndo}
                        className="px-3 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                        title="Undo (Cmd/Ctrl+Z)"
                    >
                        ↶ Undo
                    </button>
                    <div className="w-px h-6 bg-gray-200 dark:bg-gray-600" />
                    <button
                        onClick={onRedo}
                        disabled={!canRedo}
                        className="px-3 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                        title="Redo (Cmd/Ctrl+Y)"
                    >
                        ↷ Redo
                    </button>
                </div>

                {/* Preview Mode Toggle */}
                <button
                    onClick={onTogglePreview}
                    className={`px-4 py-2 text-sm rounded-md transition-colors ${isPreviewMode
                        ? 'bg-green-600 text-white hover:bg-green-700'
                        : 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
                        }`}
                >
                    {isPreviewMode ? '⏸ Edit Mode' : '▶ Preview'}
                </button>

                {/* Import */}
                <button
                    onClick={handleImportClick}
                    className="px-4 py-2 text-sm bg-gray-600 text-white rounded-md hover:bg-gray-700 transition-colors"
                >
                    Import
                </button>

                {/* Export */}
                <button
                    onClick={onExport}
                    className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                >
                    Export
                </button>

                {/* Clear All */}
                <button
                    onClick={onClearAll}
                    className="px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-md transition-colors"
                >
                    Clear All
                </button>

                {children}

                {/* Hidden file input */}
                <input
                    ref={fileInputRef}
                    type="file"
                    accept=".json"
                    onChange={handleFileChange}
                    className="hidden"
                />
            </div>
        </div>
    );
};

SimulationToolbar.displayName = 'SimulationToolbar';
