import * as React from 'react';
import { tokens } from '@ghatana/tokens';

export interface TreeNode {
  key: string;
  label: string;
  icon?: React.ReactNode;
  children?: TreeNode[];
  disabled?: boolean;
  selected?: boolean;
}

export type TreeViewExpandedNodes = Set<string> | string[];

function normalizeExpanded(value?: TreeViewExpandedNodes): Set<string> {
  if (value instanceof Set) return value;
  if (Array.isArray(value)) return new Set(value);
  return new Set();
}

export interface TreeViewNodesProps {
  /** Tree nodes */
  nodes: TreeNode[];
  /** Node selection handler */
  onNodeSelect?: (key: string) => void;
  /** Expanded nodes */
  expandedNodes?: TreeViewExpandedNodes;
  /** Expand handler */
  onExpandChange?: (key: string, expanded: boolean) => void;
  /** Allow multiple selection */
  multiSelect?: boolean;
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  /** Additional class name */
  className?: string;
}

export interface TreeViewDataNodeBase {
  id: string;
  children?: TreeViewDataNodeBase[];
  disabled?: boolean;
  selected?: boolean;
}

export interface TreeViewDataProps<TNode extends { id: string; children?: TNode[]; disabled?: boolean; selected?: boolean }> {
  data: TNode;
  renderNode: (node: TNode) => React.ReactNode;
  expandedNodes?: TreeViewExpandedNodes;
  selectedNode?: string;
  onNodeToggle?: (nodeId: string) => void;
  onNodeSelect?: (nodeId: string) => void;
  enableDragDrop?: boolean;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export type TreeViewProps<
  TNode extends { id: string; children?: TNode[]; disabled?: boolean; selected?: boolean } = TreeViewDataNodeBase
> = TreeViewNodesProps | TreeViewDataProps<TNode>;

export function TreeView<
  TNode extends { id: string; children?: TNode[]; disabled?: boolean; selected?: boolean } = TreeViewDataNodeBase
>(props: TreeViewProps<TNode>) {
  const size = 'size' in props && props.size ? props.size : 'md';
  const className = 'className' in props ? props.className : undefined;

  const expandedNodesProp = 'expandedNodes' in props ? props.expandedNodes : undefined;
  const [localExpanded, setLocalExpanded] = React.useState<Set<string>>(() => normalizeExpanded(expandedNodesProp));

  React.useEffect(() => {
    if (expandedNodesProp === undefined) return;
    setLocalExpanded(normalizeExpanded(expandedNodesProp));
  }, [expandedNodesProp]);

  const sizeConfig = {
    sm: { padding: tokens.spacing.scale[1], fontSize: tokens.typography.fontSize.sm, iconSize: '16px' },
    md: { padding: tokens.spacing.scale[2], fontSize: tokens.typography.fontSize.base, iconSize: '20px' },
    lg: { padding: tokens.spacing.scale[3], fontSize: tokens.typography.fontSize.lg, iconSize: '24px' },
  };

  const config = sizeConfig[size];

  const containerStyles: React.CSSProperties = {
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: config.fontSize,
  };

  const nodeContainerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: tokens.spacing.scale[1],
  };

  const getNodeItemStyles = (node: { disabled?: boolean; selected?: boolean }): React.CSSProperties => ({
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing.scale[2],
    padding: config.padding,
    borderRadius: tokens.borderRadius.md,
    cursor: node.disabled ? 'not-allowed' : 'pointer',
    backgroundColor: node.selected ? tokens.colors.palette.primary[50] : 'transparent',
    color: node.disabled ? tokens.colors.palette.neutral[400] : tokens.colors.palette.neutral[900],
    opacity: node.disabled ? 0.5 : 1,
    transition: `all ${tokens.transitions.durations.fast} ${tokens.transitions.easings.easeInOut}`,
  });

  const expandButtonStyles: React.CSSProperties = {
    width: config.iconSize,
    height: config.iconSize,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'none',
    border: 'none',
    padding: 0,
    cursor: 'pointer',
    color: tokens.colors.palette.neutral[600],
    transition: `transform ${tokens.transitions.durations.fast} ${tokens.transitions.easings.easeInOut}`,
  };

  const childrenContainerStyles: React.CSSProperties = {
    marginLeft: tokens.spacing.scale[4],
    display: 'flex',
    flexDirection: 'column',
    gap: tokens.spacing.scale[1],
  };

  const toggleExpanded = (id: string) => {
    const newExpanded = new Set(localExpanded);
    const isExpanded = newExpanded.has(id);

    if (isExpanded) newExpanded.delete(id);
    else newExpanded.add(id);

    setLocalExpanded(newExpanded);

    if ('onExpandChange' in props) {
      props.onExpandChange?.(id, !isExpanded);
    }
    if ('onNodeToggle' in props) {
      props.onNodeToggle?.(id);
    }
  };

  if ('data' in props && 'renderNode' in props) {
    const renderDataNode = (node: TNode): React.ReactNode => {
      const hasChildren = !!node.children && node.children.length > 0;
      const isExpanded = localExpanded.has(node.id);
      const isSelected = props.selectedNode ? props.selectedNode === node.id : !!node.selected;

      return (
        <div key={node.id}>
          <div
            style={getNodeItemStyles({ disabled: node.disabled, selected: isSelected })}
            onClick={() => {
              if (!node.disabled) props.onNodeSelect?.(node.id);
            }}
            role="treeitem"
            aria-expanded={hasChildren ? isExpanded : undefined}
            aria-selected={isSelected}
            aria-disabled={node.disabled}
          >
            {hasChildren ? (
              <button
                type="button"
                style={{
                  ...expandButtonStyles,
                  transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
                }}
                onClick={(e) => {
                  e.stopPropagation();
                  toggleExpanded(node.id);
                }}
                aria-label={isExpanded ? 'Collapse' : 'Expand'}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polyline points="9 18 15 12 9 6" />
                </svg>
              </button>
            ) : (
              <div style={{ width: config.iconSize }} />
            )}

            <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: tokens.spacing.scale[2] }}>
              {props.renderNode(node)}
            </div>
          </div>

          {hasChildren && isExpanded ? (
            <div style={childrenContainerStyles} role="group">
              {node.children?.map((child) => renderDataNode(child))}
            </div>
          ) : null}
        </div>
      );
    };

    return (
      <div style={containerStyles} className={className} role="tree">
        <div style={nodeContainerStyles}>{renderDataNode(props.data)}</div>
      </div>
    );
  }

  const renderNode = (node: TreeNode): React.ReactNode => {
    const hasChildren = node.children && node.children.length > 0;
    const isExpanded = localExpanded.has(node.key);

    return (
      <div key={node.key}>
        <div
          style={getNodeItemStyles(node)}
          onClick={() => {
            if (!node.disabled) props.onNodeSelect?.(node.key);
          }}
          onMouseEnter={(e) => {
            if (!node.disabled) {
              e.currentTarget.style.backgroundColor = tokens.colors.palette.neutral[50];
            }
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = node.selected ? tokens.colors.palette.primary[50] : 'transparent';
          }}
          role="treeitem"
          aria-expanded={hasChildren ? isExpanded : undefined}
          aria-selected={node.selected}
          aria-disabled={node.disabled}
        >
          {hasChildren ? (
            <button
              type="button"
              style={{
                ...expandButtonStyles,
                transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
              }}
              onClick={(e) => {
                e.stopPropagation();
                toggleExpanded(node.key);
              }}
              aria-label={isExpanded ? 'Collapse' : 'Expand'}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </button>
          ) : (
            <div style={{ width: config.iconSize }} />
          )}

          {node.icon ? <span style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>{node.icon}</span> : null}

          <span style={{ flex: 1 }}>{node.label}</span>
        </div>

        {hasChildren && isExpanded ? (
          <div style={childrenContainerStyles} role="group">
            {node.children?.map((child) => renderNode(child))}
          </div>
        ) : null}
      </div>
    );
  };

  return (
    <div style={containerStyles} className={className} role="tree">
      <div style={nodeContainerStyles}>{props.nodes.map((node) => renderNode(node))}</div>
    </div>
  );
}

TreeView.displayName = 'TreeView';
