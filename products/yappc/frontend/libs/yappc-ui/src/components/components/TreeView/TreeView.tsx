import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Tree node data structure
 */
export interface TreeNode {
  /**
   * Unique identifier
   */
  id: string | number;

  /**
   * Display label
   */
  label: string;

  /**
   * Child nodes
   */
  children?: TreeNode[];

  /**
   * Custom icon
   */
  icon?: React.ReactNode;

  /**
   * Whether the node is disabled
   */
  disabled?: boolean;

  /**
   * Additional data
   */
  data?: unknown;

  /**
   * Whether children are loading
   */
  isLoading?: boolean;
}

/**
 * TreeView component props
 */
export interface TreeViewProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onSelect'> {
  /**
   * Tree data
   */
  data: TreeNode[];

  /**
   * Selected node IDs
   */
  selected?: (string | number)[];

  /**
   * Called when selection changes
   */
  onSelect?: (selected: (string | number)[]) => void;

  /**
   * Expanded node IDs
   */
  expanded?: (string | number)[];

  /**
   * Called when expansion changes
   */
  onExpand?: (expanded: (string | number)[]) => void;

  /**
   * Enable multi-selection
   * @default false
   */
  multiSelect?: boolean;

  /**
   * Load children asynchronously
   */
  onLoadChildren?: (node: TreeNode) => Promise<TreeNode[]>;

  /**
   * Custom render function for nodes
   */
  renderNode?: (node: TreeNode, isSelected: boolean, isExpanded: boolean) => React.ReactNode;

  /**
   * Default expanded state
   * @default false
   */
  defaultExpanded?: boolean;

  /**
   * Show expand/collapse icons
   * @default true
   */
  showExpandIcon?: boolean;

  /**
   * Indent size in pixels
   * @default 24
   */
  indent?: number;

  /**
   * Whether the tree is disabled
   * @default false
   */
  disabled?: boolean;
}

/**
 * Chevron icon for expand/collapse
 */
const ChevronIcon: React.FC<{ className?: string; expanded?: boolean }> = ({ className, expanded }) => (
  <svg
    className={cn('transition-transform', expanded && 'rotate-90', className)}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={2}
  >
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
  </svg>
);

/**
 * Default folder icons
 */
const FolderIcon: React.FC<{ className?: string; expanded?: boolean }> = ({ className, expanded }) => (
  <svg
    className={className}
    fill="currentColor"
    viewBox="0 0 24 24"
  >
    {expanded ? (
      <path d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z" />
    ) : (
      <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
    )}
  </svg>
);

/**
 * File icon
 */
const FileIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="currentColor"
    viewBox="0 0 24 24"
  >
    <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
  </svg>
);

/**
 * Loading spinner
 */
const LoadingSpinner: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={cn('animate-spin', className)}
    fill="none"
    viewBox="0 0 24 24"
  >
    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
    <path
      className="opacity-75"
      fill="currentColor"
      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
    />
  </svg>
);

/**
 * TreeView component for hierarchical data
 *
 * @example
 * ```tsx
 * const treeData = [
 *   {
 *     id: '1',
 *     label: 'Documents',
 *     children: [
 *       { id: '1-1', label: 'Work' },
 *       { id: '1-2', label: 'Personal' },
 *     ],
 *   },
 * ];
 *
 * <TreeView data={treeData} />
 * ```
 */
export const TreeView = React.forwardRef<HTMLDivElement, TreeViewProps>(
  (
    {
      data,
      selected = [],
      onSelect,
      expanded: controlledExpanded,
      onExpand,
      multiSelect = false,
      onLoadChildren,
      renderNode,
      defaultExpanded = false,
      showExpandIcon = true,
      indent = 24,
      disabled = false,
      className,
      ...props
    },
    ref
  ) => {
    const [internalExpanded, setInternalExpanded] = React.useState<Set<string | number>>(() => {
      if (defaultExpanded) {
        const allIds = new Set<string | number>();
        const collectIds = (nodes: TreeNode[]) => {
          nodes.forEach((node) => {
            if (node.children && node.children.length > 0) {
              allIds.add(node.id);
              collectIds(node.children);
            }
          });
        };
        collectIds(data);
        return allIds;
      }
      return new Set();
    });

    const [loadingNodes, setLoadingNodes] = React.useState<Set<string | number>>(new Set());
    const [loadedChildren, setLoadedChildren] = React.useState<Map<string | number, TreeNode[]>>(new Map());

    const expanded = controlledExpanded !== undefined 
      ? new Set(controlledExpanded) 
      : internalExpanded;

    const isExpanded = (id: string | number) => expanded.has(id);
    const isSelected = (id: string | number) => selected.includes(id);

    const handleToggleExpand = async (node: TreeNode) => {
      if (disabled || node.disabled) return;

      const newExpanded = new Set(expanded);

      if (expanded.has(node.id)) {
        newExpanded.delete(node.id);
      } else {
        newExpanded.add(node.id);

        // Load children if async and not loaded
        if (onLoadChildren && !loadedChildren.has(node.id) && (!node.children || node.children.length === 0)) {
          setLoadingNodes((prev) => new Set(prev).add(node.id));
          
          try {
            const children = await onLoadChildren(node);
            setLoadedChildren((prev) => new Map(prev).set(node.id, children));
          } catch (error) {
            console.error('Failed to load children:', error);
          } finally {
            setLoadingNodes((prev) => {
              const next = new Set(prev);
              next.delete(node.id);
              return next;
            });
          }
        }
      }

      if (controlledExpanded !== undefined) {
        onExpand?.(Array.from(newExpanded));
      } else {
        setInternalExpanded(newExpanded);
      }
    };

    const handleSelect = (node: TreeNode) => {
      if (disabled || node.disabled) return;

      let newSelected: (string | number)[];

      if (multiSelect) {
        if (selected.includes(node.id)) {
          newSelected = selected.filter((id) => id !== node.id);
        } else {
          newSelected = [...selected, node.id];
        }
      } else {
        newSelected = selected.includes(node.id) ? [] : [node.id];
      }

      onSelect?.(newSelected);
    };

    const handleKeyDown = (e: React.KeyboardEvent, node: TreeNode) => {
      if (disabled || node.disabled) return;

      switch (e.key) {
        case 'Enter':
        case ' ':
          e.preventDefault();
          handleSelect(node);
          break;
        case 'ArrowRight':
          e.preventDefault();
          if (!isExpanded(node.id) && (node.children || onLoadChildren)) {
            handleToggleExpand(node);
          }
          break;
        case 'ArrowLeft':
          e.preventDefault();
          if (isExpanded(node.id)) {
            handleToggleExpand(node);
          }
          break;
      }
    };

    const renderTreeNode = (node: TreeNode, level: number = 0): React.ReactNode => {
      const nodeExpanded = isExpanded(node.id);
      const nodeSelected = isSelected(node.id);
      const hasChildren = (node.children && node.children.length > 0) || onLoadChildren;
      const isLoading = loadingNodes.has(node.id);
      const children = loadedChildren.get(node.id) || node.children || [];

      return (
        <div key={node.id} role="treeitem" aria-expanded={hasChildren ? nodeExpanded : undefined}>
          <div
            className={cn(
              'flex items-center gap-1 py-1.5 px-2 rounded cursor-pointer transition-colors',
              'hover:bg-grey-100',
              nodeSelected && 'bg-primary-100 text-primary-700',
              node.disabled && 'opacity-50 cursor-not-allowed hover:bg-transparent'
            )}
            style={{ paddingLeft: `${level * indent + 8}px` }}
            onClick={() => handleSelect(node)}
            onKeyDown={(e) => handleKeyDown(e, node)}
            tabIndex={disabled || node.disabled ? -1 : 0}
          >
            {/* Expand/collapse icon */}
            {showExpandIcon && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleToggleExpand(node);
                }}
                disabled={!hasChildren || disabled || node.disabled}
                className={cn(
                  'w-5 h-5 flex items-center justify-center flex-shrink-0',
                  !hasChildren && 'invisible'
                )}
                aria-label={nodeExpanded ? 'Collapse' : 'Expand'}
              >
                {isLoading ? (
                  <LoadingSpinner className="w-4 h-4 text-grey-500" />
                ) : (
                  <ChevronIcon className="w-4 h-4 text-grey-600" expanded={nodeExpanded} />
                )}
              </button>
            )}

            {/* Node icon */}
            {renderNode ? (
              renderNode(node, nodeSelected, nodeExpanded)
            ) : (
              <>
                {node.icon ? (
                  <span className="w-5 h-5 flex-shrink-0">{node.icon}</span>
                ) : hasChildren ? (
                  <FolderIcon className="w-5 h-5 flex-shrink-0 text-warning-600" expanded={nodeExpanded} />
                ) : (
                  <FileIcon className="w-5 h-5 flex-shrink-0 text-grey-500" />
                )}
                <span className="text-sm truncate">{node.label}</span>
              </>
            )}
          </div>

          {/* Children */}
          {nodeExpanded && children.length > 0 && (
            <div role="group">
              {children.map((child) => renderTreeNode(child, level + 1))}
            </div>
          )}
        </div>
      );
    };

    return (
      <div
        ref={ref}
        className={cn('py-2', disabled && 'opacity-60', className)}
        role="tree"
        aria-multiselectable={multiSelect}
        aria-disabled={disabled}
        {...props}
      >
        {data.map((node) => renderTreeNode(node))}
      </div>
    );
  }
);

TreeView.displayName = 'TreeView';
