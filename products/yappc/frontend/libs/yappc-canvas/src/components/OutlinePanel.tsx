/**
 * Outline Panel
 *
 * Tree-based navigator for frames and artifacts.
 * Provides hierarchical view of canvas structure with:
 * - Frame grouping
 * - Artifact nesting
 * - Search/filter
 * - Drag to reorder
 * - Quick navigation
 *
 * @doc.type component
 * @doc.purpose Canvas structure navigation
 * @doc.layer core
 * @doc.pattern Panel
 */

import React, { useState, useMemo, useCallback } from 'react';
import { useAtom, useAtomValue } from 'jotai';
import { canvasDocumentAtom, canvasSelectionAtom } from '../state/atoms';
import { getPhaseDefinition } from '../config/phase-colors';
import { LifecyclePhase } from '../types/lifecycle';

interface OutlineTreeNode {
  id: string;
  type: 'frame' | 'artifact';
  label: string;
  phase?: LifecyclePhase;
  icon?: string;
  children?: OutlineTreeNode[];
  depth: number;
}

export interface OutlinePanelProps {
  /** Callback when item is selected */
  onItemSelect?: (id: string, type: 'frame' | 'artifact') => void;
  /** Callback when item is double-clicked (focus/zoom) */
  onItemFocus?: (id: string, type: 'frame' | 'artifact') => void;
}

/**
 * Outline Panel Component
 */
export const OutlinePanel: React.FC<OutlinePanelProps> = ({
  onItemSelect,
  onItemFocus,
}) => {
  const canvasDoc = useAtomValue(canvasDocumentAtom);
  const [selection, setSelection] = useAtom(canvasSelectionAtom);
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());

  // Build tree structure
  const treeNodes = useMemo((): OutlineTreeNode[] => {
    if (!canvasDoc) return [];

    const frames = canvasDoc.frames || [];
    const artifacts = canvasDoc.artifacts || [];

    return frames.map((frame) => {
      const phaseDefinition = getPhaseDefinition(frame.phase as LifecyclePhase);

      // Find artifacts in this frame
      const frameArtifacts = artifacts.filter((artifact) => {
        // Check if artifact is within frame bounds
        const ax = artifact.position?.x || 0;
        const ay = artifact.position?.y || 0;
        const fx = frame.position?.x || 0;
        const fy = frame.position?.y || 0;
        const fw = frame.size?.width || 0;
        const fh = frame.size?.height || 0;

        return ax >= fx && ax <= fx + fw && ay >= fy && ay <= fy + fh;
      });

      return {
        id: frame.id,
        type: 'frame' as const,
        label: frame.name || 'Untitled Frame',
        phase: frame.phase as LifecyclePhase,
        icon: phaseDefinition.metadata.icon,
        depth: 0,
        children: frameArtifacts.map((artifact) => ({
          id: artifact.id,
          type: 'artifact' as const,
          label: artifact.name || 'Untitled Artifact',
          icon: '📄',
          depth: 1,
        })),
      };
    });
  }, [canvasDoc]);

  // Filter tree based on search
  const filteredNodes = useMemo(() => {
    if (!searchQuery.trim()) return treeNodes;

    const query = searchQuery.toLowerCase();

    return treeNodes
      .map((node) => {
        const matchesFrame = node.label.toLowerCase().includes(query);
        const matchingChildren = node.children?.filter((child) =>
          child.label.toLowerCase().includes(query)
        );

        if (matchesFrame) {
          return node; // Include entire frame
        }

        if (matchingChildren && matchingChildren.length > 0) {
          return { ...node, children: matchingChildren }; // Include frame with matching children
        }

        return null;
      })
      .filter((node): node is OutlineTreeNode => node !== null);
  }, [treeNodes, searchQuery]);

  // Handle node toggle
  const handleToggleNode = useCallback((nodeId: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  }, []);

  // Handle node selection
  const handleNodeClick = useCallback(
    (node: OutlineTreeNode, e: React.MouseEvent) => {
      e.stopPropagation();

      // Update selection
      setSelection({
        selectedIds: [node.id],
        selectedType: node.type === 'frame' ? 'frame' : 'artifact',
        anchorId: node.id,
      });

      onItemSelect?.(node.id, node.type);
    },
    [setSelection, onItemSelect]
  );

  // Handle node double-click
  const handleNodeDoubleClick = useCallback(
    (node: OutlineTreeNode, e: React.MouseEvent) => {
      e.stopPropagation();
      onItemFocus?.(node.id, node.type);
    },
    [onItemFocus]
  );

  // Render tree node
  const renderNode = (node: OutlineTreeNode) => {
    const isExpanded = expandedNodes.has(node.id);
    const isSelected = selection.selectedIds.includes(node.id);
    const hasChildren = node.children && node.children.length > 0;

    const phaseDefinition = node.phase ? getPhaseDefinition(node.phase) : null;

    return (
      <div key={node.id} className="outline-tree-node">
        <div
          className="outline-tree-node-content"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '6px 12px',
            paddingLeft: `${12 + node.depth * 20}px`,
            cursor: 'pointer',
            background: isSelected
              ? phaseDefinition
                ? phaseDefinition.colors.background
                : 'var(--color-selection-background, #e3f2fd)'
              : 'transparent',
            borderLeft:
              isSelected && phaseDefinition
                ? `3px solid ${phaseDefinition.colors.primary}`
                : '3px solid transparent',
            transition: 'all 0.15s ease-in-out',
          }}
          onClick={(e) => handleNodeClick(node, e)}
          onDoubleClick={(e) => handleNodeDoubleClick(node, e)}
          onMouseEnter={(e) => {
            if (!isSelected) {
              e.currentTarget.style.background =
                'var(--color-hover-background, #f5f5f5)';
            }
          }}
          onMouseLeave={(e) => {
            if (!isSelected) {
              e.currentTarget.style.background = 'transparent';
            }
          }}
        >
          {/* Expand/Collapse Toggle */}
          {hasChildren ? (
            <button
              className="outline-node-toggle"
              onClick={(e) => {
                e.stopPropagation();
                handleToggleNode(node.id);
              }}
              style={{
                width: '16px',
                height: '16px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: 'none',
                background: 'transparent',
                cursor: 'pointer',
                fontSize: '10px',
                color: 'var(--color-text-secondary, #757575)',
              }}
            >
              {isExpanded ? '▼' : '▶'}
            </button>
          ) : (
            <div style={{ width: '16px' }} />
          )}

          {/* Icon */}
          <div
            className="outline-node-icon"
            style={{
              fontSize: '14px',
              flexShrink: 0,
            }}
          >
            {node.icon}
          </div>

          {/* Label */}
          <div
            className="outline-node-label"
            style={{
              flex: 1,
              fontSize: '13px',
              fontWeight: node.type === 'frame' ? 600 : 400,
              color: isSelected
                ? phaseDefinition
                  ? phaseDefinition.colors.text
                  : 'var(--color-text-primary, #212121)'
                : 'var(--color-text-primary, #212121)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {node.label}
          </div>

          {/* Count Badge */}
          {hasChildren && (
            <div
              className="outline-node-count"
              style={{
                padding: '2px 6px',
                background: phaseDefinition
                  ? phaseDefinition.colors.background
                  : 'var(--color-surface-elevated, #f5f5f5)',
                borderRadius: '10px',
                fontSize: '10px',
                fontWeight: 600,
                color: phaseDefinition
                  ? phaseDefinition.colors.text
                  : 'var(--color-text-secondary, #757575)',
              }}
            >
              {node.children?.length}
            </div>
          )}
        </div>

        {/* Children */}
        {hasChildren && isExpanded && (
          <div className="outline-tree-children">
            {node.children?.map(renderNode)}
          </div>
        )}
      </div>
    );
  };

  return (
    <div
      className="outline-panel"
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--color-surface-paper, #fafafa)',
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <div
        className="outline-panel-header"
        style={{
          padding: '12px',
          borderBottom: '1px solid var(--color-border, #e0e0e0)',
          background: 'var(--color-surface-elevated, #ffffff)',
        }}
      >
        <div
          style={{
            fontSize: '14px',
            fontWeight: 600,
            color: 'var(--color-text-primary, #212121)',
            marginBottom: '8px',
          }}
        >
          Outline
        </div>

        {/* Search Input */}
        <input
          type="text"
          placeholder="Search frames and artifacts..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={{
            width: '100%',
            padding: '6px 8px',
            border: '1px solid var(--color-border, #e0e0e0)',
            borderRadius: '4px',
            fontSize: '12px',
            outline: 'none',
          }}
        />
      </div>

      {/* Tree */}
      <div
        className="outline-panel-tree"
        style={{
          flex: 1,
          overflowY: 'auto',
          overflowX: 'hidden',
        }}
      >
        {filteredNodes.length > 0 ? (
          filteredNodes.map(renderNode)
        ) : (
          <div
            style={{
              padding: '24px',
              textAlign: 'center',
              color: 'var(--color-text-secondary, #757575)',
              fontSize: '12px',
            }}
          >
            {searchQuery ? 'No matches found' : 'No frames on canvas'}
          </div>
        )}
      </div>
    </div>
  );
};
