import React from "react";
import type { EntityType, ToolboxItem } from "../types";
/**
 * @doc.type interface
 * @doc.purpose Props for draggable toolbox item
 * @doc.layer core
 * @doc.pattern Component
 */
export interface DraggableToolboxItemProps {
    /** Toolbox item configuration */
    item: ToolboxItem;
    /** Optional additional className */
    className?: string;
    /** DnD item type identifier */
    dndType?: string;
}
/**
 * @doc.type interface
 * @doc.purpose DnD drop payload
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export interface EntityDropPayload {
    entityType: EntityType;
    color: string;
}
/**
 * Draggable toolbox item component
 * @doc.type component
 * @doc.purpose Drag-and-drop entity palette item
 * @doc.layer core
 * @doc.pattern Component
 */
export declare const DraggableToolboxItem: React.FC<DraggableToolboxItemProps>;
/**
 * @doc.type interface
 * @doc.purpose Props for entity toolbox
 * @doc.layer core
 * @doc.pattern Component
 */
export interface EntityToolboxProps {
    /** List of toolbox items */
    items: ToolboxItem[];
    /** Number of columns in grid */
    columns?: 2 | 3 | 4;
    /** Optional title */
    title?: string;
    /** DnD item type identifier */
    dndType?: string;
    /** Optional additional className */
    className?: string;
}
/**
 * Entity toolbox grid component
 * @doc.type component
 * @doc.purpose Grid of draggable entity items
 * @doc.layer core
 * @doc.pattern Component
 */
export declare const EntityToolbox: React.FC<EntityToolboxProps>;
//# sourceMappingURL=EntityToolbox.d.ts.map