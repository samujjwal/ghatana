import React, { useMemo } from 'react';
import { Chip, Stack, Typography } from '../../ui/tw-compat';
import InfoCard from '../common/InfoCard';

export interface EventTimelineProps {
  events: Array<{
    id: string;
    timestamp: string;
    severity: string;
    title: string;
    message: string;
    source?: string;
    correlationId?: string;
  }>;
}

const severityChipColor: Record<string, 'default' | 'warning' | 'error' | 'success' | 'info'> = {
  info: 'info',
  warning: 'warning',
  critical: 'error',
  error: 'error',
};

export const EventTimeline: React.FC<EventTimelineProps> = ({ events }) => {
  // Show only a bounded recent window to avoid rendering very large lists on each update
  const recent = useMemo(() => events.slice(0, 12), [events]);

  return (
    <InfoCard title="Recent Signals" subtitle="Top alerts across telemetry, policies and commands">
      <Stack spacing={2}>
        {recent.map((event) => (
          <Stack
            key={event.id}
            spacing={1}
            className="rounded-lg border border-slate-200/15 p-3"
          >
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip
                  label={event.severity.toUpperCase()}
                  size="small"
                  color={severityChipColor[event.severity] ?? 'default'}
                  className="font-semibold tracking-wide"
                />
                <Typography variant="subtitle1">{event.title}</Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                {new Date(event.timestamp).toLocaleTimeString()}
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {event.message}
            </Typography>
            <Stack direction="row" spacing={2} mt={0.5}>
              {event.source ? (
                <Typography variant="caption" color="text.secondary">
                  Source: {event.source}
                </Typography>
              ) : null}
              {event.correlationId ? (
                <Typography variant="caption" color="text.secondary">
                  Corr ID: {event.correlationId}
                </Typography>
              ) : null}
            </Stack>
          </Stack>
        ))}
      </Stack>
    </InfoCard>
  );
};

export default React.memo(EventTimeline);
