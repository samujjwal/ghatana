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
  LOW: 'bg-gray-100 text-gray-700',
  MEDIUM: 'bg-blue-100 text-blue-700',
  HIGH: 'bg-amber-100 text-amber-800',
  CRITICAL: 'bg-red-100 text-red-800',
};

const STATUS_STYLE: Record<RequirementRecord['status'], string> = {
  DRAFT: 'bg-gray-100 text-gray-700',
  SUBMITTED: 'bg-indigo-100 text-indigo-700',
  IN_REVIEW: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-emerald-100 text-emerald-700',
  REJECTED: 'bg-red-100 text-red-700',
  IMPLEMENTED: 'bg-slate-100 text-slate-700',
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

        <Typography className="text-sm text-gray-700">{requirement.description}</Typography>

        {requirement.tags && requirement.tags.length > 0 && (
          <Box className="flex flex-wrap gap-1">
            {requirement.tags.map((tag) => (
              <Chip key={tag} size="sm" variant="outlined" label={tag} />
            ))}
          </Box>
        )}

        <Box className="grid gap-3 text-xs text-gray-500 md:grid-cols-2">
          <Typography>Created: {new Date(requirement.createdAt).toLocaleString()}</Typography>
          <Typography>Updated: {new Date(requirement.updatedAt).toLocaleString()}</Typography>
        </Box>

        <RequirementVersionHistory versions={requirement.versions} />
      </CardContent>
    </Card>
  );
};

export default RequirementDetail;