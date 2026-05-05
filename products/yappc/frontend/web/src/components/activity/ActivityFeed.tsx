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
  info: 'bg-info-bg text-info-color',
  warn: 'bg-warning-bg text-warning-color',
  error: 'bg-destructive-bg text-destructive',
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
            <Typography className="text-sm text-fg-muted">No activity yet.</Typography>
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

            <Typography className="text-xs text-fg-muted">Actor: {item.actor}</Typography>
            <Typography className="text-xs text-fg-muted">Time: {formatTime(item.occurredAt)}</Typography>
            {item.details && <Typography className="text-sm text-fg">{item.details}</Typography>}
          </CardContent>
        </Card>
      ))}
    </Box>
  );
};

export default ActivityFeed;
