/**
 * Requirement Detail
 *
 * @doc.type component
 * @doc.purpose Render requirement metadata and version history
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import { Box, Card, CardContent, Chip, Typography } from '@ghatana/design-system';

import type { RequirementRecord } from './types';
import { RequirementVersionHistory } from './RequirementVersionHistory';

export interface RequirementDetailProps {
  requirement: RequirementRecord;
}

const PRIORITY_STYLE: Record<RequirementRecord['priority'], string> = {
  LOW: 'bg-surface-muted text-fg',
  MEDIUM: 'bg-info-bg text-info-color',
  HIGH: 'bg-warning-bg text-warning-color',
  CRITICAL: 'bg-destructive-bg text-destructive',
};

const STATUS_STYLE: Record<RequirementRecord['status'], string> = {
  DRAFT: 'bg-surface-muted text-fg',
  SUBMITTED: 'bg-info-bg text-info-color',
  IN_REVIEW: 'bg-warning-bg text-warning-color',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  REJECTED: 'bg-destructive-bg text-destructive',
  IMPLEMENTED: 'bg-surface-muted text-fg',
};

export const RequirementDetail: React.FC<RequirementDetailProps> = ({ requirement }) => {
  return (
    <Card data-testid="requirement-detail">
      <CardContent className="space-y-3 p-4">
        <Box className="flex items-start justify-between gap-2">
          <Typography className="text-lg font-semibold">{requirement.title}</Typography>
          <Box className="flex gap-2">
            <Chip label={requirement.priority} size="sm" className={PRIORITY_STYLE[requirement.priority]} />
            <Chip label={requirement.status} size="sm" className={STATUS_STYLE[requirement.status]} />
          </Box>
        </Box>

        <Typography className="text-sm text-fg">{requirement.description}</Typography>

        {requirement.tags && requirement.tags.length > 0 && (
          <Box className="flex flex-wrap gap-1">
            {requirement.tags.map((tag) => (
              <Chip key={tag} size="sm" variant="outlined" label={tag} />
            ))}
          </Box>
        )}

        <Box className="grid gap-3 text-xs text-fg-muted md:grid-cols-2">
          <Typography>Created: {new Date(requirement.createdAt).toLocaleString()}</Typography>
          <Typography>Updated: {new Date(requirement.updatedAt).toLocaleString()}</Typography>
        </Box>

        <RequirementVersionHistory versions={requirement.versions} />
      </CardContent>
    </Card>
  );
};

export default RequirementDetail;