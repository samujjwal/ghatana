/**
 * Approvals Page
 *
 * Central page for managing approval requests.
 * Shows pending approvals, completed reviews, and approval history.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Box, Grid, Card, Button, Stack, Chip, Tabs } from '@ghatana/ui';
import type { RestructureProposal, ApprovalStep } from '@/types/org.types';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock approval requests
 */
const mockApprovals: RestructureProposal[] = [
  {
    id: 'proposal-1',
    title: 'Q2 Engineering Reorganization',
    description: 'Consolidate backend teams for better efficiency',
    proposedBy: 'manager-1',
    status: 'pending',
    changes: [],
    approvalChain: [
      {
        id: 'step-1',
        order: 1,
        approverRole: 'manager',
        status: 'approved',
        approvedAt: new Date('2025-11-28'),
      },
      {
        id: 'step-2',
        order: 2,
        approverRole: 'director',
        status: 'pending',
      },
      {
        id: 'step-3',
        order: 3,
        approverRole: 'owner',
        status: 'pending',
      },
    ],
    createdAt: new Date('2025-11-25'),
    updatedAt: new Date('2025-11-28'),
  },
  {
    id: 'proposal-2',
    title: 'Create Customer Success Department',
    description: 'New department to focus on customer retention',
    proposedBy: 'owner-1',
    status: 'pending',
    changes: [],
    approvalChain: [
      {
        id: 'step-1',
        order: 1,
        approverRole: 'director',
        status: 'pending',
      },
    ],
    createdAt: new Date('2025-11-29'),
    updatedAt: new Date('2025-11-29'),
  },
];

export function ApprovalsPage() {
  const { persona, canApprove } = usePersona();
  const [activeTab, setActiveTab] = useState<'pending' | 'completed' | 'all'>(
    'pending'
  );
  const [selectedProposal, setSelectedProposal] =
    useState<RestructureProposal | null>(null);

  const filteredApprovals = mockApprovals.filter((approval) => {
    if (activeTab === 'pending') return approval.status === 'pending';
    if (activeTab === 'completed')
      return ['approved', 'rejected'].includes(approval.status);
    return true;
  });

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'approved':
        return 'success';
      case 'rejected':
        return 'error';
      case 'pending':
        return 'warning';
      default:
        return 'default';
    }
  };

  const getStepStatus = (step: ApprovalStep) => {
    switch (step.status) {
      case 'approved':
        return '✓';
      case 'rejected':
        return '✗';
      default:
        return '○';
    }
  };

  return (
    <Box className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Approvals</h1>
        <p className="text-slate-600 dark:text-neutral-400 mt-1">
          Review and approve organizational change requests
        </p>
      </div>

      {/* Stats */}
      <Grid columns={4} gap={4}>
        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Pending Approval</p>
            <p className="text-2xl font-bold text-orange-600">3</p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Requiring My Action</p>
            <p className="text-2xl font-bold text-red-600">1</p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Approved This Month</p>
            <p className="text-2xl font-bold text-green-600">12</p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Avg Approval Time</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">2.5 days</p>
          </Box>
        </Card>
      </Grid>

      {/* Tabs */}
      <Card>
        <Box className="border-b">
          <Tabs
            value={activeTab}
            onChange={(value) => setActiveTab(value as any)}
            tabs={[
              { label: 'Pending', value: 'pending', badge: 3 },
              { label: 'Completed', value: 'completed' },
              { label: 'All', value: 'all' },
            ]}
          />
        </Box>
      </Card>

      {/* Approvals List */}
      <Grid columns={3} gap={4}>
        {/* List */}
        <div className="col-span-2 space-y-4">
          {filteredApprovals.length === 0 ? (
            <Card>
              <Box className="p-12 text-center text-slate-500 dark:text-neutral-400">
                <div className="text-4xl mb-4">📋</div>
                <p>No {activeTab} approvals</p>
              </Box>
            </Card>
          ) : (
            filteredApprovals.map((approval) => (
              <Card
                key={approval.id}
                className={`cursor-pointer transition-all ${
                  selectedProposal?.id === approval.id
                    ? 'ring-2 ring-blue-500'
                    : 'hover:shadow-md'
                }`}
                onClick={() => setSelectedProposal(approval)}
              >
                <Box className="p-4">
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                          {approval.title}
                        </h3>
                        <Chip
                          label={approval.status}
                          color={getStatusColor(approval.status)}
                          size="small"
                        />
                      </div>
                      <p className="text-sm text-slate-600 dark:text-neutral-400">
                        {approval.description}
                      </p>
                    </div>
                  </div>

                  {/* Approval Chain Progress */}
                  <div className="flex items-center gap-2 mb-3">
                    {approval.approvalChain.map((step) => (
                      <div key={step.id} className="flex items-center gap-1">
                        <div
                          className={`
                          w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium
                          ${
                            step.status === 'approved'
                              ? 'bg-green-100 text-green-700'
                              : step.status === 'rejected'
                              ? 'bg-red-100 text-red-700'
                              : 'bg-slate-100 text-slate-600'
                          }
                        `}
                        >
                          {getStepStatus(step)}
                        </div>
                        {step.order < approval.approvalChain.length && (
                          <div className="w-8 h-0.5 bg-slate-300"></div>
                        )}
                      </div>
                    ))}
                  </div>

                  {/* Meta Info */}
                  <div className="flex items-center justify-between text-sm text-slate-500 dark:text-neutral-400">
                    <span>
                      Created {approval.createdAt.toLocaleDateString()}
                    </span>
                    <span>
                      {approval.changes.length} change
                      {approval.changes.length !== 1 ? 's' : ''}
                    </span>
                  </div>
                </Box>
              </Card>
            ))
          )}
        </div>

        {/* Detail Panel */}
        <div>
          {selectedProposal ? (
            <Card>
              <Box className="p-4 space-y-4">
                <div>
                  <div className="flex items-center gap-2 mb-2">
                    <h3 className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                      {selectedProposal.title}
                    </h3>
                    <Chip
                      label={selectedProposal.status}
                      color={getStatusColor(selectedProposal.status)}
                      size="small"
                    />
                  </div>
                  <p className="text-sm text-slate-600 dark:text-neutral-400">
                    {selectedProposal.description}
                  </p>
                </div>

                {/* Approval Chain */}
                <div>
                  <h4 className="text-sm font-semibold text-slate-700 mb-2">
                    Approval Workflow
                  </h4>
                  <Stack spacing={2}>
                    {selectedProposal.approvalChain.map((step) => (
                      <div
                        key={step.id}
                        className={`
                        p-3 rounded-lg border
                        ${
                          step.status === 'approved'
                            ? 'bg-green-50 border-green-200'
                            : step.status === 'rejected'
                            ? 'bg-red-50 border-red-200'
                            : 'bg-slate-50 border-slate-200'
                        }
                      `}
                      >
                        <div className="flex items-center justify-between mb-1">
                          <span className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                            {step.approverRole.charAt(0).toUpperCase() +
                              step.approverRole.slice(1)}
                          </span>
                          <span className="text-lg">
                            {getStepStatus(step)}
                          </span>
                        </div>
                        {step.approvedAt && (
                          <p className="text-xs text-slate-600 dark:text-neutral-400">
                            {step.approvedAt.toLocaleDateString()}
                          </p>
                        )}
                      </div>
                    ))}
                  </Stack>
                </div>

                {/* Changes Summary */}
                <div>
                  <h4 className="text-sm font-semibold text-slate-700 mb-2">
                    Changes ({selectedProposal.changes.length})
                  </h4>
                  <div className="p-3 bg-slate-50 rounded-lg text-sm text-slate-600 dark:text-neutral-400">
                    View detailed changes →
                  </div>
                </div>

                {/* Actions */}
                {canApprove() && selectedProposal.status === 'pending' && (
                  <Stack spacing={2} className="pt-4 border-t">
                    <Button variant="primary" size="sm" fullWidth>
                      Approve
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      fullWidth
                      className="text-red-600 border-red-600"
                    >
                      Reject
                    </Button>
                    <Button variant="outline" size="sm" fullWidth>
                      Request Changes
                    </Button>
                  </Stack>
                )}
              </Box>
            </Card>
          ) : (
            <Card>
              <Box className="p-8 text-center text-slate-500 dark:text-neutral-400">
                <div className="text-4xl mb-4">📋</div>
                <p>Select an approval to view details</p>
              </Box>
            </Card>
          )}
        </div>
      </Grid>
    </Box>
  );
}

