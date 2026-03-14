/**
 * Org Tree View Component
 *
 * Interactive tree visualization of organization structure.
 * Uses @ghatana/design-system TreeView for display and interaction.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState, useMemo } from 'react';
import { TreeView, Card, Box, Stack, TextField, Chip, Button } from '@ghatana/design-system';
import type { OrgNode, OrgSearchFilter, TreeViewOptions } from '@/types/org.types';

/**
 * Mock organization data
 * TODO: Replace with API calls
 */
const mockOrgData: OrgNode = {
  id: 'org-1',
  type: 'organization',
  name: 'Acme Corporation',
  description: 'Leading technology company',
  metadata: { headcount: 320 },
  children: [
    {
      id: 'dept-eng',
      type: 'department',
      name: 'Engineering',
      description: 'Product development',
      parent: 'org-1',
      metadata: { headcount: 150, status: 'optimal' },
      children: [
        {
          id: 'team-backend',
          type: 'team',
          name: 'Backend Team',
          parent: 'dept-eng',
          metadata: { headcount: 12, status: 'optimal' },
          children: [],
        },
        {
          id: 'team-frontend',
          type: 'team',
          name: 'Frontend Team',
          parent: 'dept-eng',
          metadata: { headcount: 10, status: 'high-load' },
          children: [],
        },
      ],
    },
    {
      id: 'dept-product',
      type: 'department',
      name: 'Product',
      description: 'Product management',
      parent: 'org-1',
      metadata: { headcount: 25, status: 'optimal' },
      children: [
        {
          id: 'team-pm',
          type: 'team',
          name: 'Product Managers',
          parent: 'dept-product',
          metadata: { headcount: 8, status: 'optimal' },
          children: [],
        },
      ],
    },
    {
      id: 'dept-sales',
      type: 'department',
      name: 'Sales',
      description: 'Revenue generation',
      parent: 'org-1',
      metadata: { headcount: 80, status: 'high-load' },
      children: [],
    },
  ],
};

export interface OrgTreeViewProps {
  /** Organization data to display */
  data?: OrgNode;

  /** Callback when node is selected */
  onNodeSelect?: (node: OrgNode) => void;

  /** Callback when node is expanded/collapsed */
  onNodeToggle?: (nodeId: string, expanded: boolean) => void;

  /** Show search and filters */
  showSearch?: boolean;

  /** Show metrics on nodes */
  showMetrics?: boolean;

  /** Enable drag and drop */
  enableDragDrop?: boolean;
}

/**
 * Org Tree View Component
 *
 * Displays organization hierarchy as an interactive tree.
 *
 * @example
 * ```tsx
 * <OrgTreeView
 *   data={orgData}
 *   onNodeSelect={(node) => console.log(node)}
 *   showSearch
 *   showMetrics
 * />
 * ```
 */
export function OrgTreeView({
  data = mockOrgData,
  onNodeSelect,
  onNodeToggle,
  showSearch = true,
  showMetrics = true,
  enableDragDrop = false,
}: OrgTreeViewProps) {
  const [searchFilter, setSearchFilter] = useState<OrgSearchFilter>({});
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['org-1']));

  /**
   * Filter tree based on search criteria
   */
  const filteredTree = useMemo(() => {
    if (!searchFilter.query) return data;

    const filterNode = (node: OrgNode): OrgNode | null => {
      const matchesQuery = node.name
        .toLowerCase()
        .includes(searchFilter.query!.toLowerCase());

      const filteredChildren = node.children
        .map(filterNode)
        .filter((n): n is OrgNode => n !== null);

      if (matchesQuery || filteredChildren.length > 0) {
        return {
          ...node,
          children: filteredChildren,
        };
      }

      return null;
    };

    return filterNode(data);
  }, [data, searchFilter]);

  /**
   * Handle node selection
   */
  const handleNodeSelect = (nodeId: string) => {
    setSelectedNodeId(nodeId);

    // Find node and call callback
    const findNode = (node: OrgNode): OrgNode | null => {
      if (node.id === nodeId) return node;
      for (const child of node.children) {
        const found = findNode(child);
        if (found) return found;
      }
      return null;
    };

    const node = findNode(data);
    if (node && onNodeSelect) {
      onNodeSelect(node);
    }
  };

  /**
   * Handle node expand/collapse
   */
  const handleNodeToggle = (nodeId: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(nodeId)) {
        next.delete(nodeId);
        onNodeToggle?.(nodeId, false);
      } else {
        next.add(nodeId);
        onNodeToggle?.(nodeId, true);
      }
      return next;
    });
  };

  /**
   * Render node with custom styling
   */
  const renderNode = (node: OrgNode) => {
    const isSelected = selectedNodeId === node.id;
    const isExpanded = expandedNodes.has(node.id);

    return (
      <div
        className={`
          flex items-center gap-2 p-2 rounded cursor-pointer
          ${isSelected ? 'bg-blue-50 border border-blue-200' : 'hover:bg-slate-50'}
        `}
        onClick={() => handleNodeSelect(node.id)}
      >
        {/* Node icon based on type */}
        <span className="text-xl">
          {node.type === 'organization' && '🏢'}
          {node.type === 'department' && '📁'}
          {node.type === 'team' && '👥'}
        </span>

        {/* Node name */}
        <span className="font-medium text-slate-900 dark:text-neutral-100">{node.name}</span>

        {/* Metrics badge */}
        {showMetrics && node.metadata?.headcount && (
          <Chip
            label={`${node.metadata.headcount} people`}
            size="small"
            variant="outlined"
          />
        )}

        {/* Status indicator */}
        {showMetrics && node.metadata?.status && (
          <div
            className={`
              w-2 h-2 rounded-full
              ${node.metadata.status === 'optimal' ? 'bg-green-500' : ''}
              ${node.metadata.status === 'high-load' ? 'bg-yellow-500' : ''}
              ${node.metadata.status === 'overloaded' ? 'bg-red-500' : ''}
            `}
            title={node.metadata.status}
          />
        )}
      </div>
    );
  };

  if (!filteredTree) {
    return (
      <Card>
        <Box className="p-8 text-center text-slate-500 dark:text-neutral-400">
          No results found
        </Box>
      </Card>
    );
  }

  return (
    <Card>
      {/* Search and Filters */}
      {showSearch && (
        <Box className="p-4 border-b">
          <Stack spacing={2}>
            <TextField
              placeholder="Search organization..."
              value={searchFilter.query || ''}
              onChange={(e) =>
                setSearchFilter({ ...searchFilter, query: e.target.value })
              }
              fullWidth
            />

            {/* Filter chips */}
            <Stack direction="row" spacing={1}>
              <Chip
                label="All"
                variant={!searchFilter.nodeTypes ? 'filled' : 'outlined'}
                onClick={() => setSearchFilter({ ...searchFilter, nodeTypes: undefined })}
              />
              <Chip
                label="Departments"
                variant={
                  searchFilter.nodeTypes?.includes('department') ? 'filled' : 'outlined'
                }
                onClick={() =>
                  setSearchFilter({ ...searchFilter, nodeTypes: ['department'] })
                }
              />
              <Chip
                label="Teams"
                variant={searchFilter.nodeTypes?.includes('team') ? 'filled' : 'outlined'}
                onClick={() => setSearchFilter({ ...searchFilter, nodeTypes: ['team'] })}
              />
            </Stack>
          </Stack>
        </Box>
      )}

      {/* Tree View */}
      <Box className="p-4">
        <TreeView
          data={filteredTree}
          renderNode={renderNode}
          expandedNodes={Array.from(expandedNodes)}
          selectedNode={selectedNodeId}
          onNodeToggle={handleNodeToggle}
          onNodeSelect={handleNodeSelect}
          enableDragDrop={enableDragDrop}
        />
      </Box>

      {/* Actions */}
      <Box className="p-4 border-t flex gap-2">
        <Button variant="outline" size="sm" onClick={() => setExpandedNodes(new Set())}>
          Collapse All
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            const allIds = new Set<string>();
            const collectIds = (node: OrgNode) => {
              allIds.add(node.id);
              node.children.forEach(collectIds);
            };
            collectIds(data);
            setExpandedNodes(allIds);
          }}
        >
          Expand All
        </Button>
      </Box>
    </Card>
  );
}
