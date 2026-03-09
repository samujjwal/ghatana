/**
 * Node palette component for workflow designer.
 *
 * <p><b>Purpose</b><br>
 * Displays available node types for drag-drop into canvas.
 * Provides search and categorization.
 *
 * <p><b>Architecture</b><br>
 * - Node type library
 * - Search functionality
 * - Drag-drop support
 * - Categorization
 *
 * @doc.type component
 * @doc.purpose Node palette for workflow designer
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useState, useMemo } from 'react';
import { NodeType } from '../types/workflow.types';

/**
 * Node palette item.
 *
 * @doc.type interface
 */
interface PaletteItem {
  type: NodeType;
  label: string;
  description: string;
  category: string;
  icon: string;
  color: string;
}

/**
 * Available palette items.
 *
 * @doc.type constant
 */
const PALETTE_ITEMS: PaletteItem[] = [
  {
    type: NodeType.START,
    label: 'Start',
    description: 'Workflow entry point',
    category: 'Flow',
    icon: '▶',
    color: 'bg-green-100 border-green-500',
  },
  {
    type: NodeType.END,
    label: 'End',
    description: 'Workflow exit point',
    category: 'Flow',
    icon: '■',
    color: 'bg-red-100 border-red-500',
  },
  {
    type: NodeType.API_CALL,
    label: 'API Call',
    description: 'Call external API',
    category: 'Integration',
    icon: '⚙',
    color: 'bg-blue-100 border-blue-500',
  },
  {
    type: NodeType.DECISION,
    label: 'Decision',
    description: 'Conditional branching',
    category: 'Logic',
    icon: '◇',
    color: 'bg-yellow-100 border-yellow-500',
  },
  {
    type: NodeType.APPROVAL,
    label: 'Approval',
    description: 'Human approval required',
    category: 'Human',
    icon: '✓',
    color: 'bg-purple-100 border-purple-500',
  },
  {
    type: NodeType.TRANSFORM,
    label: 'Transform',
    description: 'Transform data',
    category: 'Data',
    icon: '⟷',
    color: 'bg-indigo-100 border-indigo-500',
  },
  {
    type: NodeType.QUERY,
    label: 'Query',
    description: 'Query collection',
    category: 'Data',
    icon: '🔍',
    color: 'bg-cyan-100 border-cyan-500',
  },
  {
    type: NodeType.LOOP,
    label: 'Loop',
    description: 'Iterate over items',
    category: 'Logic',
    icon: '↻',
    color: 'bg-orange-100 border-orange-500',
  },
];

/**
 * NodePalette component props.
 *
 * @doc.type interface
 */
export interface NodePaletteProps {
  onDragStart?: (nodeType: NodeType) => void;
}

/**
 * NodePalette component.
 *
 * Displays available node types for drag-drop into canvas.
 *
 * @param props component props
 * @returns JSX element
 *
 * @doc.type function
 */
export const NodePalette: React.FC<NodePaletteProps> = ({ onDragStart }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);

  /**
   * Filtered items based on search and category.
   */
  const filteredItems = useMemo(() => {
    return PALETTE_ITEMS.filter((item) => {
      const matchesSearch =
        item.label.toLowerCase().includes(searchTerm.toLowerCase()) ||
        item.description.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = !selectedCategory || item.category === selectedCategory;
      return matchesSearch && matchesCategory;
    });
  }, [searchTerm, selectedCategory]);

  /**
   * Unique categories.
   */
  const categories = useMemo(
    () => [...new Set(PALETTE_ITEMS.map((item) => item.category))],
    []
  );

  /**
   * Handles drag start.
   */
  const handleDragStart = (e: React.DragEvent, nodeType: NodeType) => {
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('application/reactflow', nodeType);
    onDragStart?.(nodeType);
  };

  return (
    <div className="flex flex-col h-full bg-white border-r border-gray-200">
      {/* Header */}
      <div className="p-4 border-b border-gray-200">
        <h3 className="font-semibold text-gray-900 mb-3">Node Palette</h3>

        {/* Search */}
        <input
          type="text"
          placeholder="Search nodes..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Categories */}
      <div className="px-4 py-2 border-b border-gray-200 flex flex-wrap gap-2">
        <button
          onClick={() => setSelectedCategory(null)}
          className={`px-3 py-1 text-xs rounded-full transition-colors ${
            selectedCategory === null
              ? 'bg-blue-500 text-white'
              : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
        >
          All
        </button>
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={`px-3 py-1 text-xs rounded-full transition-colors ${
              selectedCategory === cat
                ? 'bg-blue-500 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Items */}
      <div className="flex-1 overflow-y-auto p-4 space-y-2">
        {filteredItems.map((item) => (
          <div
            key={item.type}
            draggable
            onDragStart={(e) => handleDragStart(e, item.type)}
            className={`p-3 rounded-lg border-2 cursor-move transition-all hover:shadow-md ${item.color}`}
          >
            <div className="flex items-start gap-2">
              <span className="text-xl">{item.icon}</span>
              <div className="flex-1 min-w-0">
                <div className="font-medium text-sm">{item.label}</div>
                <div className="text-xs text-gray-600 truncate">{item.description}</div>
              </div>
            </div>
          </div>
        ))}

        {filteredItems.length === 0 && (
          <div className="text-center py-8 text-gray-500">
            <p className="text-sm">No nodes found</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default NodePalette;
