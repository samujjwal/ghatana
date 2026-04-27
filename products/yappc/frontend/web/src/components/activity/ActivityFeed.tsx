/**
 * Activity Feed
 *
 * @doc.type component
 * @doc.purpose Workspace activity timeline for visibility of lifecycle and approvals
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import { Box, Card, CardContent, Chip, Typography } from '@ghatana/design-system';

export type ActivitySeverity = 'info' | 'warn' | 'error';

export interface ActivityItem {
  id: string;
  action: string;
  actor: string;
  details?: string;
  occurredAt: string;
  severity: ActivitySeverity;
}

export interface ActivityFeedProps {
  items: ActivityItem[];
  className?: string;
}

const SEVERITY_COLOR: Record<ActivitySeverity, string> = {
  info: 'bg-blue-100 text-blue-700',
  warn: 'bg-amber-100 text-amber-800',
  error: 'bg-red-100 text-red-700',
};

function formatTime(isoTimestamp: string): string {
  const parsed = new Date(isoTimestamp);
  if (Number.isNaN(parsed.getTime())) {
    return isoTimestamp;
  }
  return parsed.toLocaleString();
}

export const ActivityFeed: React.FC<ActivityFeedProps> = ({
  items,
  className = '',
}) => {
  return (
    <Box className={`space-y-3 ${className}`}>
      <Typography className="text-lg font-semibold">Activity Feed</Typography>

      {items.length === 0 && (
        <Card>
          <CardContent className="p-4">
            <Typography className="text-sm text-gray-600">No activity yet.</Typography>
          </CardContent>
        </Card>
      )}

      {items.map((item) => (
        <Card key={item.id}>
          <CardContent className="space-y-2 p-4">
            <Box className="flex items-center justify-between gap-2">
              <Typography className="font-medium">{item.action}</Typography>
              <Chip label={item.severity} size="sm" className={SEVERITY_COLOR[item.severity]} />
            </Box>

            <Typography className="text-xs text-gray-500">Actor: {item.actor}</Typography>
            <Typography className="text-xs text-gray-500">Time: {formatTime(item.occurredAt)}</Typography>
            {item.details && <Typography className="text-sm text-gray-700">{item.details}</Typography>}
          </CardContent>
        </Card>
      ))}
    </Box>
  );
};

export default ActivityFeed;
