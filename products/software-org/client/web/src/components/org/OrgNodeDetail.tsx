/**
 * Org Node Detail Component
 *
 * Displays detailed information about a selected organization node.
 * Shows metrics, members, and available actions.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { Card, Stack, Button, Chip, Box } from '@ghatana/design-system';
import type { OrgNode } from '@/types/org.types';
import { usePersona } from '@/hooks/usePersona';

export interface OrgNodeDetailProps {
  /** Node to display details for */
  node: OrgNode | null;

  /** Callback for edit action */
  onEdit?: (node: OrgNode) => void;

  /** Callback for delete action */
  onDelete?: (node: OrgNode) => void;

  /** Callback for restructure action */
  onRestructure?: (node: OrgNode) => void;
}

/**
 * Org Node Detail Component
 *
 * Shows comprehensive information about a selected org node.
 *
 * @example
 * ```tsx
 * <OrgNodeDetail
 *   node={selectedNode}
 *   onEdit={(node) => console.log('Edit', node)}
 * />
 * ```
 */
export function OrgNodeDetail({
  node,
  onEdit,
  onDelete,
  onRestructure,
}: OrgNodeDetailProps) {
  const { canRestructure } = usePersona();

  if (!node) {
    return (
      <Card>
        <Box className="p-8 text-center text-slate-500 dark:text-neutral-400">
          <div className="text-4xl mb-4">🔍</div>
          <p>Select a node to view details</p>
        </Box>
      </Card>
    );
  }

  const getNodeIcon = (type: string) => {
    switch (type) {
      case 'organization':
        return '🏢';
      case 'department':
        return '📁';
      case 'team':
        return '👥';
      case 'role':
        return '👤';
      default:
        return '📄';
    }
  };

  const getStatusColor = (status?: string) => {
    switch (status) {
      case 'optimal':
        return 'success';
      case 'high-load':
        return 'warning';
      case 'overloaded':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Card>
      {/* Header */}
      <Box className="p-4 border-b">
        <Stack direction="row" spacing={2} alignItems="center">
          <span className="text-3xl">{getNodeIcon(node.type)}</span>
          <div className="flex-1">
            <h2 className="text-xl font-bold text-slate-900 dark:text-neutral-100">{node.name}</h2>
            <p className="text-sm text-slate-500 capitalize">{node.type}</p>
          </div>
          {node.metadata?.status && (
            <Chip
              label={node.metadata.status}
              color={getStatusColor(node.metadata.status as string)}
              size="small"
            />
          )}
        </Stack>
      </Box>

      {/* Details */}
      <Box className="p-4 space-y-4">
        {/* Description */}
        {node.description && (
          <div>
            <h3 className="text-sm font-semibold text-slate-700 mb-1">Description</h3>
            <p className="text-sm text-slate-600 dark:text-neutral-400">{node.description}</p>
          </div>
        )}

        {/* Metrics */}
        <div>
          <h3 className="text-sm font-semibold text-slate-700 mb-2">Metrics</h3>
          <div className="grid grid-cols-2 gap-3">
            {node.metadata?.headcount !== undefined && (
              <div className="p-3 bg-slate-50 rounded-lg">
                <p className="text-xs text-slate-500 dark:text-neutral-400">Headcount</p>
                <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                  {node.metadata.headcount}
                </p>
              </div>
            )}

            {node.children && (
              <div className="p-3 bg-slate-50 rounded-lg">
                <p className="text-xs text-slate-500 dark:text-neutral-400">
                  {node.type === 'organization' ? 'Departments' :
                   node.type === 'department' ? 'Teams' : 'Members'}
                </p>
                <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                  {node.children.length}
                </p>
              </div>
            )}

            {node.metadata?.capacity !== undefined && (
              <div className="p-3 bg-slate-50 rounded-lg">
                <p className="text-xs text-slate-500 dark:text-neutral-400">Capacity</p>
                <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                  {node.metadata.capacity}%
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Children Summary */}
        {node.children && node.children.length > 0 && (
          <div>
            <h3 className="text-sm font-semibold text-slate-700 mb-2">
              {node.type === 'organization' ? 'Departments' :
               node.type === 'department' ? 'Teams' : 'Members'} ({node.children.length})
            </h3>
            <Stack spacing={1}>
              {node.children.map((child) => (
                <div
                  key={child.id}
                  className="flex items-center justify-between p-2 bg-slate-50 rounded"
                >
                  <div className="flex items-center gap-2">
                    <span>{getNodeIcon(child.type)}</span>
                    <span className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                      {child.name}
                    </span>
                  </div>
                  {child.metadata?.headcount && (
                    <span className="text-xs text-slate-500 dark:text-neutral-400">
                      {child.metadata.headcount} people
                    </span>
                  )}
                </div>
              ))}
            </Stack>
          </div>
        )}

        {/* Metadata */}
        {node.metadata && Object.keys(node.metadata).length > 0 && (
          <div>
            <h3 className="text-sm font-semibold text-slate-700 mb-2">Additional Info</h3>
            <div className="space-y-1">
              {Object.entries(node.metadata)
                .filter(([key]) => !['headcount', 'capacity', 'status'].includes(key))
                .map(([key, value]) => (
                  <div key={key} className="flex justify-between text-sm">
                    <span className="text-slate-500 capitalize">
                      {key.replace(/([A-Z])/g, ' $1').trim()}:
                    </span>
                    <span className="text-slate-900 font-medium">
                      {String(value)}
                    </span>
                  </div>
                ))}
            </div>
          </div>
        )}
      </Box>

      {/* Actions */}
      <Box className="p-4 border-t">
        <Stack spacing={2}>
          {onEdit && (
            <Button variant="primary" size="sm" fullWidth onClick={() => onEdit(node)}>
              Edit {node.type}
            </Button>
          )}

          {canRestructure() && onRestructure && (
            <Button
              variant="outline"
              size="sm"
              fullWidth
              onClick={() => onRestructure(node)}
            >
              Propose Restructure
            </Button>
          )}

          {onDelete && (
            <Button
              variant="outline"
              size="sm"
              fullWidth
              onClick={() => onDelete(node)}
              className="text-red-600 border-red-600 hover:bg-red-50"
            >
              Delete {node.type}
            </Button>
          )}
        </Stack>
      </Box>
    </Card>
  );
}
