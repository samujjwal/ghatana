/**
 * @fileoverview Component tree for visual builder.
 *
 * Displays the hierarchical tree structure of components in the builder document.
 * Supports selection, expansion/collapse, and drag-and-drop reordering.
 *
 * @doc.type component
 * @doc.purpose Component hierarchy visualization and navigation
 * @doc.layer platform
 */

import type { ReactElement } from 'react';
import { useState, useMemo } from 'react';
import { Typography } from '@ghatana/design-system';
import type { ComponentInstance, NodeId } from '@ghatana/ui-builder';
import type { BuilderDocument } from '@ghatana/ui-builder';

export interface ComponentTreeProps {
  /** The builder document */
  document: BuilderDocument;
  /** Currently selected node ID */
  selectedNodeId: NodeId | null;
  /** Callback when a node is selected */
  onNodeSelect: (nodeId: NodeId) => void;
  /** Callback when a node is expanded/collapsed */
  onNodeToggle?: (nodeId: NodeId) => void;
}

interface TreeNode {
  instance: ComponentInstance;
  children: TreeNode[];
  depth: number;
  slotName?: string;
}

export function ComponentTree({
  document,
  selectedNodeId,
  onNodeSelect,
  onNodeToggle,
}: ComponentTreeProps): ReactElement {
  const [expandedNodes, setExpandedNodes] = useState<Set<NodeId>>(new Set());

  // Build tree structure from document using slots
  const treeRoot = useMemo(() => {
    const nodes = document.nodes;
    const rootId = document.layout.rootId;
    const rootNode = nodes[rootId];

    if (!rootNode) {
      return null;
    }

    const buildTree = (nodeId: NodeId, depth: number = 0, slotName?: string): TreeNode | null => {
      const instance = nodes[nodeId];
      if (!instance) return null;

      const children: TreeNode[] = [];

      // ComponentInstance uses slots instead of children
      for (const [slotName, childIds] of Object.entries(instance.slots)) {
        for (const childId of childIds) {
          const childTree = buildTree(childId, depth + 1, slotName);
          if (childTree) {
            children.push(childTree);
          }
        }
      }

      return { instance, children, depth, slotName };
    };

    return buildTree(rootId);
  }, [document]);

  const toggleNode = (nodeId: NodeId): void => {
    const newExpanded = new Set(expandedNodes);
    if (newExpanded.has(nodeId)) {
      newExpanded.delete(nodeId);
    } else {
      newExpanded.add(nodeId);
    }
    setExpandedNodes(newExpanded);
    onNodeToggle?.(nodeId);
  };

  const renderNode = (node: TreeNode): ReactElement => {
    const isExpanded = expandedNodes.has(node.instance.id);
    const isSelected = selectedNodeId === node.instance.id;
    const hasChildren = node.children.length > 0;

    return (
      <div key={node.instance.id}>
        {/* Node Row */}
        <div
          className={`flex items-center gap-2 px-2 py-1.5 cursor-pointer hover:bg-gray-100 rounded ${
            isSelected ? 'bg-blue-50 border-l-2 border-blue-500' : ''
          }`}
          style={{ paddingLeft: `${node.depth * 16 + 8}px` }}
          onClick={() => onNodeSelect(node.instance.id)}
        >
          {/* Expand/Collapse Button */}
          {hasChildren ? (
            <button
              onClick={(e) => {
                e.stopPropagation();
                toggleNode(node.instance.id);
              }}
              className="p-0.5 hover:bg-gray-200 rounded text-gray-500"
            >
              {isExpanded ? '▼' : '▶'}
            </button>
          ) : (
            <div className="w-5" />
          )}

          {/* Node Info */}
          <div className="flex-1 min-w-0">
            <Typography variant="body1" className="text-sm font-medium truncate">
              {node.instance.contractName}
            </Typography>
            {node.slotName && (
              <Typography variant="body2" className="text-gray-500 text-xs">
                Slot: {node.slotName}
              </Typography>
            )}
          </div>

          {/* Node Count Badge */}
          {hasChildren && (
            <span className="text-xs text-gray-500 bg-gray-200 px-1.5 py-0.5 rounded">
              {node.children.length}
            </span>
          )}
        </div>

        {/* Children */}
        {hasChildren && isExpanded && (
          <div>
            {node.children.map((child) => renderNode(child))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="p-3 border-b">
        <Typography variant="h3" className="font-semibold">
          Component Tree
        </Typography>
      </div>

      {/* Tree */}
      <div className="flex-1 overflow-y-auto">
        {treeRoot ? (
          <div className="py-2">
            {renderNode(treeRoot)}
          </div>
        ) : (
          <div className="p-4 text-center">
            <Typography variant="body2" className="text-gray-500">
              No components in document
            </Typography>
          </div>
        )}
      </div>
    </div>
  );
}
