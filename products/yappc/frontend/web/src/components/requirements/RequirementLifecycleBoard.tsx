/**
 * Requirement Lifecycle Board
 *
 * @doc.type component
 * @doc.purpose Unified list-detail board for requirement lifecycle operations
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useMemo, useState } from 'react';
import { Box, Card, CardContent, Chip, Typography } from '@ghatana/design-system';

import type { RequirementRecord } from './types';
import { RequirementDetail } from './RequirementDetail';

export interface RequirementLifecycleBoardProps {
  requirements: RequirementRecord[];
}

const ORDERED_STATUS = ['DRAFT', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'IMPLEMENTED'] as const;

export const RequirementLifecycleBoard: React.FC<RequirementLifecycleBoardProps> = ({ requirements }) => {
  const [selectedRequirementId, setSelectedRequirementId] = useState<string | null>(requirements[0]?.id ?? null);

  const selectedRequirement = useMemo(
    () => requirements.find((item) => item.id === selectedRequirementId) ?? null,
    [requirements, selectedRequirementId]
  );

  const counts = useMemo(() => {
    return ORDERED_STATUS.reduce<Record<string, number>>((acc, status) => {
      acc[status] = requirements.filter((item) => item.status === status).length;
      return acc;
    }, {});
  }, [requirements]);

  return (
    <Box className="grid gap-4 lg:grid-cols-[340px_1fr]" data-testid="requirement-lifecycle-board">
      <Card>
        <CardContent className="space-y-3 p-4">
          <Typography className="text-lg font-semibold">Requirements</Typography>

          <Box className="flex flex-wrap gap-1">
            {ORDERED_STATUS.map((status) => (
              <Chip key={status} size="sm" variant="outlined" label={`${status}: ${counts[status]}`} />
            ))}
          </Box>

          {requirements.length === 0 && (
            <Typography className="text-sm text-fg-muted">No requirements submitted yet.</Typography>
          )}

          <Box className="space-y-2">
            {requirements.map((requirement) => (
              <button
                key={requirement.id}
                type="button"
                onClick={() => setSelectedRequirementId(requirement.id)}
                className={`w-full rounded border px-3 py-2 text-left transition-colors ${
                  requirement.id === selectedRequirementId
                    ? 'border-info-border bg-info-bg'
                    : 'border-border hover:bg-surface-muted'
                }`}
              >
                <Typography className="text-sm font-medium">{requirement.title}</Typography>
                <Typography className="text-xs text-fg-muted">{requirement.status}</Typography>
              </button>
            ))}
          </Box>
        </CardContent>
      </Card>

      {selectedRequirement ? (
        <RequirementDetail requirement={selectedRequirement} />
      ) : (
        <Card>
          <CardContent className="p-4">
            <Typography className="text-sm text-fg-muted">Select a requirement to view details.</Typography>
          </CardContent>
        </Card>
      )}
    </Box>
  );
};

export default RequirementLifecycleBoard;