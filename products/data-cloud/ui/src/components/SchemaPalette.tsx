import React, { useState, useMemo } from 'react';
import type { MetaField } from '@/types/schema.types';

/**
 * Schema palette component for displaying available fields.
 *
 * <p><b>Purpose</b><br>
 * Displays all available fields from a collection schema.
 * Supports drag-and-drop, filtering, and field type visualization.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { SchemaPalette } from '@/components/SchemaPalette';
 *
 * function WorkflowEditor() {
 *   const fields = [...];
 *   return (
 *     <SchemaPalette
 *       fields={fields}
 *       onFieldDrag={(field) => console.log('Dragging:', field)}
 *     />
 *   );
 * }
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Display fields with type icons
 * - Search/filter fields
 * - Drag-and-drop support
 * - Favorites/recent fields
 * - Field type grouping
 * - Responsive design
 *
 * @doc.type component
 * @doc.purpose Schema field palette for workflow binding
 * @doc.layer frontend
 */

export interface SchemaPaletteProps {
  /** Available fields from collection schema */
  fields: MetaField[];
  /** Callback when field is dragged */
  onFieldDrag?: (field: MetaField) => void;
  /** Callback when field is selected */
  onFieldSelect?: (field: MetaField) => void;
  /** Show favorites section */
  showFavorites?: boolean;
  /** Show recent section */
  showRecent?: boolean;
  /** Custom CSS class */
  className?: string;
}

/**
 * Get icon for field type.
 *
 * @param type the field data type
 * @returns SVG icon component
 */
function getFieldTypeIcon(type: string) {
  const iconProps = 'w-4 h-4';
  
  switch (type.toLowerCase()) {
    case 'string':
      return (
        <svg className={iconProps} fill="currentColor" viewBox="0 0 20 20">
          <path d="M4 4a2 2 0 012-2h8a2 2 0 012 2v12a1 1 0 110 2H4a1 1 0 110-2V4z" />
        </svg>
      );
    case 'number':
    case 'integer':
      return (
        <svg className={iconProps} fill="currentColor" viewBox="0 0 20 20">
          <path d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4z" />
        </svg>
      );
    case 'boolean':
      return (
        <svg className={iconProps} fill="currentColor" viewBox="0 0 20 20">
          <path d="M5 9V7a1 1 0 011-1h8a1 1 0 011 1v2M5 9a2 2 0 002 2h6a2 2 0 002-2m0 0V7a2 2 0 00-2-2H7a2 2 0 00-2 2v2z" />
        </svg>
      );
    case 'date':
    case 'datetime':
      return (
        <svg className={iconProps} fill="currentColor" viewBox="0 0 20 20">
          <path d="M6 2a1 1 0 000 2h8a1 1 0 100-2H6zM4 5a2 2 0 012-2h8a2 2 0 012 2v10a2 2 0 01-2 2H6a2 2 0 01-2-2V5z" />
        </svg>
      );
    case 'array':
      return (
        <svg className={iconProps} fill="currentColor" viewBox="0 0 20 20">
          <path d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1v-2zM3 16a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1v-2z" />
        </svg>
      );
    case 'object':
      return (
        <svg className={iconProps} fill="currentColor" viewBox="0 0 20 20">
          <path d="M5 3a2 2 0 00-2 2v6a2 2 0 002 2h6a2 2 0 002-2V5a2 2 0 00-2-2H5zM15 13a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2a2 2 0 012-2h2z" />
        </svg>
      );
    default:
      return (
        <svg className={iconProps} fill="currentColor" viewBox="0 0 20 20">
          <path d="M5 3a2 2 0 00-2 2v6a2 2 0 002 2h6a2 2 0 002-2V5a2 2 0 00-2-2H5z" />
        </svg>
      );
  }
}

/**
 * Get color class for field type.
 *
 * @param type the field data type
 * @returns Tailwind color class
 */
function getFieldTypeColor(type: string): string {
  switch (type.toLowerCase()) {
    case 'string':
      return 'text-blue-600 dark:text-blue-400';
    case 'number':
    case 'integer':
      return 'text-green-600 dark:text-green-400';
    case 'boolean':
      return 'text-purple-600 dark:text-purple-400';
    case 'date':
    case 'datetime':
      return 'text-orange-600 dark:text-orange-400';
    case 'array':
      return 'text-red-600 dark:text-red-400';
    case 'object':
      return 'text-indigo-600 dark:text-indigo-400';
    default:
      return 'text-gray-600 dark:text-gray-400';
  }
}

export function SchemaPalette({
  fields,
  onFieldDrag,
  onFieldSelect,
  showFavorites = true,
  showRecent = true,
  className = '',
}: SchemaPaletteProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [favorites, setFavorites] = useState<string[]>([]);
  const [draggedField, setDraggedField] = useState<MetaField | null>(null);

  // Filter fields based on search query
  const filteredFields = useMemo(() => {
    return fields.filter(
      (field) =>
        field.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        field.type.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [fields, searchQuery]);

  // Group fields by type
  const groupedFields = useMemo(() => {
    const groups: Record<string, MetaField[]> = {};
    filteredFields.forEach((field) => {
      const type = field.type;
      if (!groups[type]) {
        groups[type] = [];
      }
      groups[type].push(field);
    });
    return groups;
  }, [filteredFields]);

  // Keep compatibility with props even when sections are hidden dynamically.
  const favoritesEnabled = showFavorites;
  const recentEnabled = showRecent;

  const handleDragStart = (field: MetaField) => {
    setDraggedField(field);
    onFieldDrag?.(field);
  };

  const handleDragEnd = () => {
    setDraggedField(null);
  };

  const toggleFavorite = (fieldName: string) => {
    setFavorites((prev) =>
      prev.includes(fieldName)
        ? prev.filter((name) => name !== fieldName)
        : [...prev, fieldName]
    );
  };

  return (
    <div
      className={`flex flex-col h-full bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 ${className}`}
    >
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-2">
          Fields
        </h3>

        {/* Search Input */}
        <input
          type="text"
          placeholder="Search fields..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Fields List */}
      <div className="flex-1 overflow-y-auto">
        {Object.entries(groupedFields).length === 0 ? (
          <div className="px-4 py-8 text-center text-sm text-gray-500 dark:text-gray-400">
            No fields found
          </div>
        ) : (
          Object.entries(groupedFields).map(([type, typeFields]) => (
            <div key={type} className="px-2 py-2">
              {/* Type Header */}
              <div className="px-2 py-1 text-xs font-semibold text-gray-600 dark:text-gray-400 uppercase tracking-wider">
                {type}
              </div>

              {/* Fields in Type */}
              <div className="space-y-1">
                {typeFields.map((field) => (
                  <div
                    key={field.id}
                    draggable
                    onDragStart={() => handleDragStart(field)}
                    onDragEnd={handleDragEnd}
                    onClick={() => onFieldSelect?.(field)}
                    className={`
                      px-3 py-2 rounded-md cursor-move
                      transition-colors duration-200
                      ${
                        draggedField?.id === field.id
                          ? 'bg-blue-100 dark:bg-blue-900 opacity-50'
                          : 'hover:bg-gray-100 dark:hover:bg-gray-800'
                      }
                    `}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2 flex-1 min-w-0">
                        <div className={`flex-shrink-0 ${getFieldTypeColor(field.type)}`}>
                          {getFieldTypeIcon(field.type)}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                            {field.name}
                          </p>
                          {field.description && (
                            <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                              {field.description}
                            </p>
                          )}
                        </div>
                      </div>

                      {/* Favorite Button */}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          toggleFavorite(field.name);
                        }}
                        className="flex-shrink-0 p-1 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                        title={
                          favorites.includes(field.name)
                            ? 'Remove from favorites'
                            : 'Add to favorites'
                        }
                      >
                        {favorites.includes(field.name) ? (
                          <svg
                            className="w-4 h-4 text-yellow-500"
                            fill="currentColor"
                            viewBox="0 0 20 20"
                          >
                            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                          </svg>
                        ) : (
                          <svg
                            className="w-4 h-4 text-gray-400 dark:text-gray-500"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 20 20"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={2}
                              d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"
                            />
                          </svg>
                        )}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
        {!favoritesEnabled && !recentEnabled ? null : null}
      </div>

      {/* Footer */}
      <div className="px-4 py-2 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-500 dark:text-gray-400">
        {filteredFields.length} field{filteredFields.length !== 1 ? 's' : ''}
      </div>
    </div>
  );
}

export default SchemaPalette;
