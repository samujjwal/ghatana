/**
 * Audit Timeline
 *
 * @doc.type component
 * @doc.purpose Compact timeline for lifecycle and approval events
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import { Box, Card, CardContent, Chip, Typography } from '@ghatana/design-system';

export type AuditTimelineLevel = 'INFO' | 'WARN' | 'ERROR';

export interface AuditTimelineEntry {
  id: string;
  title: string;
  description?: string;
  actor: string;
  level: AuditTimelineLevel;
  createdAt: string;
}

export interface AuditTimelineProps {
  entries: AuditTimelineEntry[];
  className?: string;
}

const LEVEL_STYLE: Record<AuditTimelineLevel, string> = {
  INFO: 'bg-blue-100 text-blue-800',
  WARN: 'bg-amber-100 text-amber-800',
  ERROR: 'bg-red-100 text-red-800',
};

export const AuditTimeline: React.FC<AuditTimelineProps> = ({ entries, className = '' }) => {
  return (
    <Box className={`space-y-3 ${className}`} data-testid="audit-timeline">
      <Typography className="text-lg font-semibold">Audit Timeline</Typography>

      {entries.length === 0 && (
        <Card>
          <CardContent className="p-4">
            <Typography className="text-sm text-gray-600">No audit events yet.</Typography>
          </CardContent>
        </Card>
      )}

      {entries.map((entry) => (
        <Card key={entry.id}>
          <CardContent className="space-y-2 p-4">
            <Box className="flex items-center justify-between gap-2">
              <Typography className="font-medium">{entry.title}</Typography>
              <Chip label={entry.level} size="sm" className={LEVEL_STYLE[entry.level]} />
            </Box>

            {entry.description && (
              <Typography className="text-sm text-gray-700">{entry.description}</Typography>
            )}

            <Typography className="text-xs text-gray-500">Actor: {entry.actor}</Typography>
            <Typography className="text-xs text-gray-500">
              Time: {new Date(entry.createdAt).toLocaleString()}
            </Typography>
          </CardContent>
        </Card>
      ))}
    </Box>
  );
};

export default AuditTimeline;