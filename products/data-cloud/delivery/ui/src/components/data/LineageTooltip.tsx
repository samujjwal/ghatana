/**
 * Lineage Tooltip Component
 *
 * On-demand lineage popover for data fields and columns.
 * Shows upstream and downstream dependencies in a compact view.
 *
 * Features:
 * - Click any field to see lineage
 * - Shows upstream sources and downstream consumers
 * - Quick navigation to full lineage graph
 * - Compact inline display
 *
 * @doc.type component
 * @doc.purpose On-demand lineage tooltip/popover
 * @doc.layer frontend
 */

import React, { useState, useRef, useEffect } from 'react';
import {
  GitBranch,
  ArrowUpRight,
  ArrowDownRight,
  Database,
  Table,
  Columns,
  ExternalLink,
  X,
  ChevronRight,
} from 'lucide-react';
import { cn } from '../../lib/theme';

/**
 * Lineage node types
 */
export type LineageNodeType = 'dataset' | 'table' | 'column' | 'transformation';

/**
 * Lineage node interface
 */
export interface LineageNode {
  id: string;
  name: string;
  type: LineageNodeType;
  source?: string;
  path?: string;
}

/**
 * Lineage data for a field
 */
export interface LineageData {
  currentNode: LineageNode;
  upstream: LineageNode[];
  downstream: LineageNode[];
  lastUpdated?: string;
}

export interface LineageTooltipProps {
  fieldName: string;
  lineageData?: LineageData;
  onViewFullLineage?: (nodeId: string) => void;
  position?: 'top' | 'bottom' | 'left' | 'right';
  className?: string;
  children: React.ReactNode;
}

/**
 * Icon mapping for node types
 */
const NODE_ICONS: Record<LineageNodeType, React.ReactNode> = {
  dataset: <Database className="h-3 w-3" />,
  table: <Table className="h-3 w-3" />,
  column: <Columns className="h-3 w-3" />,
  transformation: <GitBranch className="h-3 w-3" />,
};

/**
 * Color mapping for node types
 */
const NODE_COLORS: Record<LineageNodeType, string> = {
  dataset: 'text-purple-500',
  table: 'text-blue-500',
  column: 'text-green-500',
  transformation: 'text-orange-500',
};

/**
 * Lineage Node Item
 */
function LineageNodeItem({
  node,
  direction,
  onClick,
}: {
  node: LineageNode;
  direction: 'upstream' | 'downstream';
  onClick?: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'w-full flex items-center gap-2 px-2 py-1.5 rounded',
        'hover:bg-gray-100 dark:hover:bg-gray-800',
        'text-left transition-colors'
      )}
    >
      <span className={NODE_COLORS[node.type]}>{NODE_ICONS[node.type]}</span>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate">
          {node.name}
        </p>
        {node.source && (
          <p className="text-xs text-gray-400 truncate">{node.source}</p>
        )}
      </div>
      <ChevronRight className="h-3 w-3 text-gray-400" />
    </button>
  );
}

/**
 * Lineage Tooltip Component
 */
export function LineageTooltip({
  fieldName,
  lineageData,
  children,
  onViewFullLineage,
  position = 'top',
  className,
}: LineageTooltipProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close on click outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen]);

  // Position classes
  const positionClasses = {
    top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
    bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
    left: 'right-full top-1/2 -translate-y-1/2 mr-2',
    right: 'left-full top-1/2 -translate-y-1/2 ml-2',
  };

  const handleViewFullLineage = () => {
    if (lineageData && onViewFullLineage) {
      onViewFullLineage(lineageData.currentNode.id);
    }
    setIsOpen(false);
  };

  return (
    <div ref={containerRef} className={cn('relative inline-flex', className)}>
      {/* Trigger */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          'inline-flex items-center gap-1',
          'hover:text-primary-600 dark:hover:text-primary-400',
          'transition-colors cursor-pointer'
        )}
      >
        {children}
        <GitBranch className="h-3 w-3 text-gray-400 hover:text-primary-500" />
      </button>

      {/* Popover */}
      {isOpen && (
        <div
          className={cn(
            'absolute z-50',
            'w-72 bg-white dark:bg-gray-900',
            'border border-gray-200 dark:border-gray-700',
            'rounded-lg shadow-xl',
            positionClasses[position]
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-3 py-2 border-b border-gray-100 dark:border-gray-800">
            <div className="flex items-center gap-2">
              <GitBranch className="h-4 w-4 text-primary-500" />
              <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                Lineage
              </span>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded"
            >
              <X className="h-3 w-3 text-gray-400" />
            </button>
          </div>

          {/* Content */}
          {lineageData ? (
            <div className="p-2">
              {/* Current Node */}
              <div className="px-2 py-1.5 mb-2 bg-primary-50 dark:bg-primary-900/20 rounded border border-primary-200 dark:border-primary-800">
                <div className="flex items-center gap-2">
                  <span className={NODE_COLORS[lineageData.currentNode.type]}>
                    {NODE_ICONS[lineageData.currentNode.type]}
                  </span>
                  <span className="text-xs font-medium text-primary-700 dark:text-primary-300">
                    {lineageData.currentNode.name}
                  </span>
                </div>
              </div>

              {/* Upstream */}
              {lineageData.upstream.length > 0 && (
                <div className="mb-2">
                  <div className="flex items-center gap-1 px-2 py-1">
                    <ArrowUpRight className="h-3 w-3 text-blue-500" />
                    <span className="text-xs font-medium text-gray-500 uppercase">
                      Sources ({lineageData.upstream.length})
                    </span>
                  </div>
                  <div className="space-y-0.5">
                    {lineageData.upstream.slice(0, 3).map((node) => (
                      <LineageNodeItem
                        key={node.id}
                        node={node}
                        direction="upstream"
                        onClick={handleViewFullLineage}
                      />
                    ))}
                    {lineageData.upstream.length > 3 && (
                      <p className="text-xs text-gray-400 px-2 py-1">
                        +{lineageData.upstream.length - 3} more sources
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* Downstream */}
              {lineageData.downstream.length > 0 && (
                <div className="mb-2">
                  <div className="flex items-center gap-1 px-2 py-1">
                    <ArrowDownRight className="h-3 w-3 text-green-500" />
                    <span className="text-xs font-medium text-gray-500 uppercase">
                      Consumers ({lineageData.downstream.length})
                    </span>
                  </div>
                  <div className="space-y-0.5">
                    {lineageData.downstream.slice(0, 3).map((node) => (
                      <LineageNodeItem
                        key={node.id}
                        node={node}
                        direction="downstream"
                        onClick={handleViewFullLineage}
                      />
                    ))}
                    {lineageData.downstream.length > 3 && (
                      <p className="text-xs text-gray-400 px-2 py-1">
                        +{lineageData.downstream.length - 3} more consumers
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* No lineage */}
              {lineageData.upstream.length === 0 &&
                lineageData.downstream.length === 0 && (
                  <p className="text-xs text-gray-400 text-center py-3">
                    No lineage information available
                  </p>
                )}
            </div>
          ) : (
            <div className="p-4 text-center">
              <GitBranch className="h-8 w-8 text-gray-300 mx-auto mb-2" />
              <p className="text-xs text-gray-400">Loading lineage...</p>
            </div>
          )}

          {/* Footer */}
          {lineageData && onViewFullLineage && (
            <div className="px-3 py-2 border-t border-gray-100 dark:border-gray-800">
              <button
                onClick={handleViewFullLineage}
                className={cn(
                  'w-full flex items-center justify-center gap-2',
                  'px-3 py-1.5 rounded',
                  'text-xs font-medium text-primary-600 dark:text-primary-400',
                  'hover:bg-primary-50 dark:hover:bg-primary-900/20',
                  'transition-colors'
                )}
              >
                View Full Lineage
                <ExternalLink className="h-3 w-3" />
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default LineageTooltip;

