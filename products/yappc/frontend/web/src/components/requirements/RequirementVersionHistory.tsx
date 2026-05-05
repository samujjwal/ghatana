/**
 * Requirement Version History
 *
 * @doc.type component
 * @doc.purpose Show immutable requirement version trail
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import { Box, Card, CardContent, Typography } from '@ghatana/design-system';

import type { RequirementVersion } from './types';

export interface RequirementVersionHistoryProps {
  versions: RequirementVersion[];
}

export const RequirementVersionHistory: React.FC<RequirementVersionHistoryProps> = ({ versions }) => {
  return (
    <Box className="space-y-2" data-testid="requirement-version-history">
      <Typography className="text-sm font-semibold">Version History</Typography>
      {versions.length === 0 && (
        <Typography className="text-sm text-fg-muted">No versions available.</Typography>
      )}

      {versions.map((version) => (
        <Card key={version.id}>
          <CardContent className="space-y-1 p-3">
            <Typography className="text-sm font-medium">v{version.version}</Typography>
            <Typography className="text-sm text-fg">{version.summary}</Typography>
            <Typography className="text-xs text-fg-muted">By {version.createdBy}</Typography>
            <Typography className="text-xs text-fg-muted">
              {new Date(version.createdAt).toLocaleString()}
            </Typography>
          </CardContent>
        </Card>
      ))}
    </Box>
  );
};

export default RequirementVersionHistory;