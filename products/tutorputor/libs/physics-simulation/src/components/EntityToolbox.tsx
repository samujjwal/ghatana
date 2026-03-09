import React from "react";
import { useDrag } from "react-dnd";
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
export const DraggableToolboxItem: React.FC<DraggableToolboxItemProps> = ({
  item,
  className = "",
  dndType = "PHYSICS_ENTITY",
}) => {
  const [{ isDragging }, drag] = useDrag<
    EntityDropPayload,
    unknown,
    { isDragging: boolean }
  >(() => ({
    type: dndType,
    item: { entityType: item.type, color: item.defaultColor },
    collect: (monitor) => ({
      isDragging: monitor.isDragging(),
    }),
  }));

  return (
    <div
      ref={drag as unknown as React.LegacyRef<HTMLDivElement>}
      role="button"
      tabIndex={0}
      aria-label={`Drag ${item.label}`}
      className={`
        flex flex-col items-center justify-center p-4 rounded-lg cursor-move
        border-2 border-gray-200 dark:border-gray-700
        hover:border-blue-400 dark:hover:border-blue-500
        focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent
        transition-all duration-200 select-none
        ${isDragging ? "opacity-50 scale-95" : "opacity-100"}
        ${className}
      `}
      style={{ backgroundColor: `${item.defaultColor}20` }}
      title={item.description}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          // Logic to simulate drag start or selection could go here
          // For now, at least allow focusing
        }
      }}
    >
      <div className="text-3xl mb-2">{item.icon}</div>
      <div className="text-xs font-medium text-gray-700 dark:text-gray-300">
        {item.label}
      </div>
      {item.isPremium && (
        <span className="mt-1 text-[10px] px-1.5 py-0.5 bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200 rounded">
          Premium
        </span>
      )}
    </div>
  );
};

DraggableToolboxItem.displayName = "DraggableToolboxItem";

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
export const EntityToolbox: React.FC<EntityToolboxProps> = ({
  items,
  columns = 2,
  title = "Entity Toolbox",
  dndType = "PHYSICS_ENTITY",
  className = "",
}) => {
  const gridCols = {
    2: "grid-cols-2",
    3: "grid-cols-3",
    4: "grid-cols-4",
  };

  return (
    <div className={`p-4 ${className}`}>
      {title && (
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          {title}
        </h3>
      )}
      <div className={`grid ${gridCols[columns]} gap-3`}>
        {items.map((item) => (
          <DraggableToolboxItem key={item.type} item={item} dndType={dndType} />
        ))}
      </div>
    </div>
  );
};

EntityToolbox.displayName = "EntityToolbox";
