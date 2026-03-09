/**
 * Restructure Page Component
 *
 * Organization restructuring management and proposal system.
 * View active restructures, propose new changes, and track history.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RestructureProposalForm } from '../../components/org/RestructureProposalForm';
import { DepartmentHierarchyViz } from '../../components/org/DepartmentHierarchyViz';

export interface RestructureWorkspaceProps {
  /** Initial organization data */
  initialData?: OrgNode;
}

/**
 * Restructure Workspace Component
 *
 * Provides a sandbox for safely proposing organizational changes.
 * Shows diff preview and validation before submission.
 *
 * @example
 * ```tsx
 * <RestructureWorkspace initialData={orgData} />
 * ```
 */
export function RestructureWorkspace({
  initialData,
}: RestructureWorkspaceProps = {}) {
  const navigate = useNavigate();
  const [proposal, setProposal] = useState<Partial<RestructureProposal>>({
    title: '',
    description: '',
    changes: [],
  });
  const [isSandboxMode, setIsSandboxMode] = useState(true);
  const [hasChanges, setHasChanges] = useState(false);

  /**
   * Handle proposal submission
   */
  const handleSubmit = () => {
    // TODO: Submit proposal to API
    console.log('Submitting proposal:', proposal);
    navigate('/approvals');
  };

  /**
   * Discard all changes
   */
  const handleDiscard = () => {
    setProposal({ title: '', description: '', changes: [] });
    setHasChanges(false);
    setIsSandboxMode(true);
  };

  return (
    <Box className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
            Restructure Workspace
          </h1>
          <p className="text-slate-600 dark:text-neutral-400 mt-1">
            Propose organizational changes in a safe sandbox environment
          </p>
        </div>

        {/* Mode Indicator */}
        <div className="flex items-center gap-3">
          {isSandboxMode && (
            <div className="px-4 py-2 bg-blue-50 border border-blue-200 rounded-lg">
              <p className="text-sm font-medium text-blue-900">
                🔒 Sandbox Mode - Changes not saved
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Proposal Info */}
      <Card>
        <Box className="p-4 space-y-3">
          <TextField
            label="Proposal Title"
            placeholder="e.g., Q2 2024 Engineering Reorganization"
            value={proposal.title || ''}
            onChange={(e) =>
              setProposal({ ...proposal, title: e.target.value })
            }
            fullWidth
            required
          />

          <TextField
            label="Description"
            placeholder="Explain the reason and goals for this reorganization..."
            value={proposal.description || ''}
            onChange={(e) =>
              setProposal({ ...proposal, description: e.target.value })
            }
            multiline
            rows={3}
            fullWidth
            required
          />
        </Box>
      </Card>

      {/* Main Workspace */}
      <Grid columns={3} gap={4}>
        {/* Tree View with Drag-and-Drop */}
        <div className="col-span-2">
          <Card>
            <Box className="p-4 border-b bg-slate-50 dark:bg-neutral-800">
              <div className="flex items-center justify-between">
                <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                  Organization Structure
                </h3>
                <Stack direction="row" spacing={2}>
                  <Button variant="outline" size="sm">
                    Undo
                  </Button>
                  <Button variant="outline" size="sm">
                    Redo
                  </Button>
                </Stack>
              </div>
            </Box>

            <Box className="p-4">
              <OrgTreeView
                data={initialData}
                enableDragDrop={true}
                showMetrics
                showSearch
              />
            </Box>

            {/* Instructions */}
            <Box className="p-4 border-t bg-blue-50">
              <p className="text-sm text-blue-900">
                💡 <strong>How to use:</strong> Drag and drop nodes to
                reorganize. Changes are tracked automatically. Click "Preview
                Changes" to review before submitting.
              </p>
            </Box>
          </Card>
        </div>

        {/* Changes Panel */}
        <div>
          <Card>
            <Box className="p-4 border-b">
              <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                Proposed Changes ({proposal.changes?.length || 0})
              </h3>
            </Box>

            <Box className="p-4">
              {!hasChanges ? (
                <div className="text-center py-8 text-slate-500 dark:text-neutral-400">
                  <div className="text-4xl mb-4">📝</div>
                  <p className="text-sm">No changes yet</p>
                  <p className="text-xs mt-1">
                    Make changes to the org structure to see them here
                  </p>
                </div>
              ) : (
                <Stack spacing={2}>
                  {/* Example change items */}
                  <div className="p-3 bg-green-50 border border-green-200 rounded-lg">
                    <div className="flex items-start gap-2">
                      <span className="text-green-600 text-sm">✓</span>
                      <div className="flex-1">
                        <p className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                          Move Team
                        </p>
                        <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1">
                          Backend Team → Engineering Department
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg">
                    <div className="flex items-start gap-2">
                      <span className="text-blue-600 text-sm">+</span>
                      <div className="flex-1">
                        <p className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                          Create Department
                        </p>
                        <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1">
                          New: Customer Success
                        </p>
                      </div>
                    </div>
                  </div>
                </Stack>
              )}
            </Box>

            {/* Validation Status */}
            <Box className="p-4 border-t">
              <div className="p-3 bg-green-50 rounded-lg mb-3">
                <p className="text-sm font-medium text-green-900">
                  ✓ No validation issues
                </p>
              </div>

              {/* Impact Summary */}
              <div className="space-y-2 mb-4">
                <div className="flex justify-between text-sm">
                  <span className="text-slate-600 dark:text-neutral-400">Affected People:</span>
                  <span className="font-medium text-slate-900 dark:text-neutral-100">24</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-600 dark:text-neutral-400">Affected Teams:</span>
                  <span className="font-medium text-slate-900 dark:text-neutral-100">3</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-600 dark:text-neutral-400">Risk Level:</span>
                  <span className="font-medium text-yellow-600">Medium</span>
                </div>
              </div>
            </Box>
          </Card>
        </div>
      </Grid>

      {/* Action Buttons */}
      <Card>
        <Box className="p-4">
          <div className="flex items-center justify-between">
            <div className="text-sm text-slate-600 dark:text-neutral-400">
              {hasChanges
                ? `${proposal.changes?.length || 0} change(s) pending review`
                : 'No changes to review'}
            </div>

            <Stack direction="row" spacing={2}>
              <Button variant="outline" size="md" onClick={handleDiscard}>
                Discard Changes
              </Button>

              <Button
                variant="outline"
                size="md"
                disabled={!hasChanges}
                onClick={() => {
                  /* TODO: Show diff modal */
                }}
              >
                Preview Changes
              </Button>

              <Button
                variant="primary"
                size="md"
                disabled={
                  !proposal.title ||
                  !proposal.description ||
                  !hasChanges
                }
                onClick={handleSubmit}
              >
                Submit for Approval
              </Button>
            </Stack>
          </div>
        </Box>
      </Card>

      {/* Help Section */}
      <Card>
        <Box className="p-4 bg-blue-50">
          <h4 className="text-sm font-semibold text-blue-900 mb-2">
            📚 Restructure Guidelines
          </h4>
          <ul className="text-sm text-blue-800 space-y-1">
            <li>
              • All changes are tracked and must go through approval workflow
            </li>
            <li>
              • Drag and drop to move teams between departments
            </li>
            <li>• Changes affecting 20+ people require Director approval</li>
            <li>
              • Budget implications must be documented in the description
            </li>
            <li>• Restructures become effective on the specified date</li>
          </ul>
        </Box>
      </Card>
    </Box>
  );
}

// Provide both named and default exports for compatibility with existing imports
export const RestructurePage = RestructureWorkspace;
export default RestructureWorkspace;
