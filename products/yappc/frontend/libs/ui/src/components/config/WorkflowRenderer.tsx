/**
 * WorkflowRenderer Component
 *
 * Renders workflow selection and visualization with phases and stages.
 *
 * @module ui/components/config
 * @doc.type component
 * @doc.purpose Workflow visualization and selection
 * @doc.layer ui
 */

import React from 'react';

import { Box, Card, CardContent, Typography, Stack, Chip, FormControl, InputLabel, Select, MenuItem } from '@ghatana/ui';
import { useAtom } from 'jotai';

import { useWorkflows } from '../../hooks/useConfig';
import { selectedWorkflowIdAtom } from '../../state/configAtoms';

// ============================================================================
// Types
// ============================================================================

interface WorkflowRendererProps {
  /** Additional CSS class name */
  className?: string;
}

interface PhaseCardProps {
  /** Phase data */
  phase: {
    id: string;
    name: string;
    description?: string;
    estimatedDuration?: string;
    stages?: string[];
    tasks?: string[];
  };
}

// ============================================================================
// Phase Card Component
// ============================================================================

const PhaseCard: React.FC<PhaseCardProps> = ({ phase }) => (
  <Card variant="outlined">
    <CardContent>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={1}>
        <Box>
          <Typography as="p" className="text-lg font-medium" fontWeight={500}>
            {phase.name}
          </Typography>
          {phase.description && (
            <Typography as="p" className="text-sm" color="text.secondary">
              {phase.description}
            </Typography>
          )}
        </Box>
        {phase.estimatedDuration && (
          <Chip label={phase.estimatedDuration} size="sm" variant="outlined" />
        )}
      </Stack>

      {/* Stages */}
      {phase.stages && phase.stages.length > 0 && (
        <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5} mt={2}>
          {phase.stages.map((stage) => (
            <Chip
              key={stage}
              label={stage}
              size="sm"
              className="bg-indigo-100 dark:bg-indigo-900/30 text-white"
            />
          ))}
        </Stack>
      )}

      {/* Tasks */}
      {phase.tasks && phase.tasks.length > 0 && (
        <Box className="mt-4">
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary" gutterBottom>
            Tasks ({phase.tasks.length})
          </Typography>
          <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5}>
            {phase.tasks.slice(0, 5).map((taskId) => (
              <Chip
                key={taskId}
                label={taskId}
                size="sm"
                className="bg-green-100 dark:bg-green-900/30 text-white"
              />
            ))}
            {phase.tasks.length > 5 && (
              <Chip
                label={`+${phase.tasks.length - 5} more`}
                size="sm"
                variant="outlined"
              />
            )}
          </Stack>
        </Box>
      )}
    </CardContent>
  </Card>
);

// ============================================================================
// Main Component
// ============================================================================

/**
 * Workflow renderer component for selecting and visualizing workflows
 *
 * @example
 * ```tsx
 * <WorkflowRenderer className="my-4" />
 * ```
 */
export const WorkflowRenderer: React.FC<WorkflowRendererProps> = ({ className = '' }) => {
  const workflows = useWorkflows();
  const [selectedId, setSelectedId] = useAtom(selectedWorkflowIdAtom);
  // Cast to any to access extended workflow properties from API
  const selectedWorkflow = workflows.find((w) => w.id === selectedId) as unknown;

  return (
    <Box className={className}>
      {/* Workflow Selector */}
      <FormControl fullWidth size="sm" className="mb-6">
        <InputLabel id="workflow-select-label">Select Workflow</InputLabel>
        <Select
          labelId="workflow-select-label"
          id="workflow-select"
          value={selectedId || ''}
          label="Select Workflow"
          onChange={(e) => setSelectedId(e.target.value || null)}
        >
          <MenuItem value="">
            <em>Choose a workflow...</em>
          </MenuItem>
          {workflows.map((wf) => (
            <MenuItem key={wf.id} value={wf.id}>
              {wf.name}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      {/* Workflow Visualization */}
      {selectedWorkflow && (
        <Card className="bg-gray-50 dark:bg-gray-800">
          <CardContent>
            {/* Header */}
            <Box className="mb-6">
              <Typography as="h6" fontWeight={600}>
                {selectedWorkflow.name}
              </Typography>
              {selectedWorkflow.description && (
                <Typography as="p" className="text-sm" color="text.secondary" className="mt-1">
                  {selectedWorkflow.description}
                </Typography>
              )}
            </Box>

            {/* Lifecycle Stages */}
            {selectedWorkflow.lifecycleStages && selectedWorkflow.lifecycleStages.length > 0 && (
              <Box className="mb-6">
                <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                  Lifecycle Stages
                </Typography>
                <Stack
                  direction="row"
                  spacing={1}
                  alignItems="center"
                  className="overflow-x-auto pb-2"
                >
                  {selectedWorkflow.lifecycleStages.map((stage: string, idx: number) => (
                    <React.Fragment key={stage}>
                      <Chip
                        label={stage}
                        tone="primary"
                        variant="outlined"
                        className="min-w-max"
                      />
                      {idx < selectedWorkflow.lifecycleStages.length - 1 && (
                        <Typography color="text.secondary" className="px-1">
                          →
                        </Typography>
                      )}
                    </React.Fragment>
                  ))}
                </Stack>
              </Box>
            )}

            {/* Phases */}
            {selectedWorkflow.phases && selectedWorkflow.phases.length > 0 && (
              <Box>
                <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                  Workflow Phases
                </Typography>
                <Stack spacing={2}>
                  {selectedWorkflow.phases.map((phase: PhaseCardProps['phase']) => (
                    <PhaseCard key={phase.id} phase={phase} />
                  ))}
                </Stack>
              </Box>
            )}
          </CardContent>
        </Card>
      )}
    </Box>
  );
};

export default WorkflowRenderer;
