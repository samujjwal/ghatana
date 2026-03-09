/**
 * Org Overview Page
 *
 * Main page for viewing and managing organization structure.
 * Combines tree view and detail panel in a split-pane layout.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Box, Grid, Button, Stack } from '@ghatana/ui';
import { OrgTreeView } from '@/components/org/OrgTreeView';
import { OrgNodeDetail } from '@/components/org/OrgNodeDetail';
import type { OrgNode } from '@/types/org.types';
import { useNavigate } from 'react-router';
import { usePersona } from '@/hooks/usePersona';

/**
 * Org Overview Page Component
 *
 * Displays the complete organization structure with tree view
 * and detail panel. Provides navigation to restructure and management tools.
 *
 * @example
 * ```tsx
 * <OwnerLayout>
 *   <OrgOverviewPage />
 * </OwnerLayout>
 * ```
 */
export function OrgOverviewPage() {
  const navigate = useNavigate();
  const { canRestructure } = usePersona();
  const [selectedNode, setSelectedNode] = useState<OrgNode | null>(null);

  /**
   * Handle node edit action
   */
  const handleEdit = (node: OrgNode) => {
    // TODO: Navigate to edit page or open modal
    console.log('Edit node:', node);
  };

  /**
   * Handle node delete action
   */
  const handleDelete = (node: OrgNode) => {
    // TODO: Open confirmation dialog and delete
    console.log('Delete node:', node);
  };

  /**
   * Handle restructure action
   */
  const handleRestructure = (node: OrgNode) => {
    // Navigate to restructure workspace with this node selected
    navigate('/org/restructure', { state: { selectedNode: node } });
  };

  return (
    <Box className="p-6 space-y-4">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Organization Structure</h1>
          <p className="text-slate-600 dark:text-neutral-400 mt-1">View and manage your organization hierarchy</p>
        </div>

        {/* Quick Actions */}
        <Stack direction="row" spacing={2}>
          <Button
            variant="outline"
            size="md"
            onClick={() => navigate('/org/personas')}
          >
            Manage Personas
          </Button>

          {canRestructure() && (
            <Button
              variant="primary"
              size="md"
              onClick={() => navigate('/org/restructure')}
            >
              Propose Restructure
            </Button>
          )}
        </Stack>
      </div>

      {/* Stats Overview */}
      <Grid columns={4} gap={4}>
        <div className="p-4 bg-white rounded-lg border border-slate-200 dark:border-neutral-600">
          <p className="text-sm text-slate-600 dark:text-neutral-400">Departments</p>
          <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">10</p>
          <p className="text-xs text-green-600 mt-1">+2 this quarter</p>
        </div>

        <div className="p-4 bg-white rounded-lg border border-slate-200 dark:border-neutral-600">
          <p className="text-sm text-slate-600 dark:text-neutral-400">Teams</p>
          <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">45</p>
          <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">Across all departments</p>
        </div>

        <div className="p-4 bg-white rounded-lg border border-slate-200 dark:border-neutral-600">
          <p className="text-sm text-slate-600 dark:text-neutral-400">Total Employees</p>
          <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">320</p>
          <p className="text-xs text-green-600 mt-1">+12 this month</p>
        </div>

        <div className="p-4 bg-white rounded-lg border border-slate-200 dark:border-neutral-600">
          <p className="text-sm text-slate-600 dark:text-neutral-400">Open Positions</p>
          <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">8</p>
          <p className="text-xs text-orange-600 mt-1">3 critical</p>
        </div>
      </Grid>

      {/* Main Content: Tree View + Detail Panel */}
      <Grid columns={3} gap={4}>
        {/* Tree View - 2 columns */}
        <div className="col-span-2">
          <OrgTreeView
            onNodeSelect={setSelectedNode}
            showSearch
            showMetrics
          />
        </div>

        {/* Detail Panel - 1 column */}
        <div>
          <OrgNodeDetail
            node={selectedNode}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onRestructure={handleRestructure}
          />
        </div>
      </Grid>

      {/* Help Text */}
      <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
        <h3 className="text-sm font-semibold text-blue-900 mb-1">
          💡 Quick Tips
        </h3>
        <ul className="text-sm text-blue-800 space-y-1">
          <li>• Click on any node in the tree to view details</li>
          <li>• Use the search box to quickly find departments or teams</li>
          <li>• Select "Propose Restructure" to make organizational changes</li>
          <li>• Color indicators show team capacity: 🟢 Optimal, 🟡 High Load, 🔴 Overloaded</li>
        </ul>
      </div>
    </Box>
  );
}

