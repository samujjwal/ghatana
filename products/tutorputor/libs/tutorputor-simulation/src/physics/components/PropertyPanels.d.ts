import React from 'react';
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
export declare const PhysicsPropertyPanel: React.FC<PhysicsPropertyPanelProps>;
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
export declare const PhysicsConfigPanel: React.FC<PhysicsConfigPanelProps>;
//# sourceMappingURL=PropertyPanels.d.ts.map