import React, { useMemo } from 'react';
import { Box, Chip, Stack, Typography } from '@mui/material';
import InfoCard from '../common/InfoCard';
import StatusBadge from '../common/StatusBadge';
import LanguageIcon from '@mui/icons-material/Language';

export interface ExtensionStatusCardProps {
  connected: boolean;
  version: string;
  latencyMs: number;
  events: Array<{
    id: string;
    type: string;
    title: string;
    url: string;
    timestamp: string;
    element?: string;
  }>;
}

export const ExtensionStatusCard: React.FC<ExtensionStatusCardProps> = ({
  connected,
  version,
  latencyMs,
  events,
}) => {
  // Only process a bounded window of recent events for grouping to avoid
  // expensive reductions over very large arrays on each render.
  const recentForGrouping = events.slice(0, 200);

  const grouped = useMemo(() => {
    return recentForGrouping.reduce<Record<string, number>>((acc, event) => {
      acc[event.type] = (acc[event.type] ?? 0) + 1;
      return acc;
    }, {});
  }, [recentForGrouping]);

  return (
    <InfoCard
      title="Browser Extension"
      subtitle={`v${version}`}
      icon={<LanguageIcon />}
      action={<StatusBadge status={connected ? 'active' : 'disconnected'} />}
    >
      <Stack spacing={2.5}>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">
            Round-trip latency
          </Typography>
          <Typography variant="subtitle1">{latencyMs} ms</Typography>
        </Stack>

        <Stack direction="row" spacing={1} flexWrap="wrap">
          {Object.entries(grouped).map(([type, count]) => (
            <Chip
              key={type}
              label={`${type.replace('_', ' ')} · ${count}`}
              size="small"
              sx={{ fontWeight: 600, textTransform: 'capitalize' }}
            />
          ))}
        </Stack>

        <Typography variant="caption" color="text.secondary">
          Recent events
        </Typography>

        <Stack spacing={1.5} sx={{ maxHeight: 200, overflow: 'auto', pr: 1 }}>
          {events.slice(0, 6).map((event) => (
            <Box
              key={event.id}
              sx={{
                borderRadius: 2,
                border: '1px solid rgba(148, 163, 184, 0.14)',
                p: 1.5,
              }}
            >
              <Typography variant="subtitle2">{event.title}</Typography>
              <Typography variant="caption" color="text.secondary" display="block">
                {new Date(event.timestamp).toLocaleTimeString()} · {event.type.replace('_', ' ')}
              </Typography>
              <Typography variant="body2" color="text.secondary" noWrap>
                {event.url}
              </Typography>
            </Box>
          ))}
        </Stack>
      </Stack>
    </InfoCard>
  );
};

// Memoize the component to avoid re-rendering the whole card when the parent
// passes a new `events` array reference but the important derived values
// (connected, version, latency, and the first few events) did not materially change.
const areEqual = (prev: ExtensionStatusCardProps, next: ExtensionStatusCardProps) => {
  if (prev.connected !== next.connected) return false;
  if (prev.version !== next.version) return false;
  if (prev.latencyMs !== next.latencyMs) return false;

  // Quick length check for the recent slice we display
  if (prev.events.length === 0 && next.events.length === 0) return true;
  if (prev.events.length === next.events.length) {
    // Compare first 6 event ids (the displayed preview)
    for (let i = 0; i < Math.min(6, prev.events.length, next.events.length); i++) {
      if (prev.events[i].id !== next.events[i].id) return false;
    }
    return true;
  }
  return false;
};

export default React.memo(ExtensionStatusCard, areEqual);
