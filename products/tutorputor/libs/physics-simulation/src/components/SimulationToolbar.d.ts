import React from 'react';
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
    historyStatus: {
        current: number;
        total: number;
    };
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
export declare const SimulationToolbar: React.FC<SimulationToolbarProps>;
//# sourceMappingURL=SimulationToolbar.d.ts.map