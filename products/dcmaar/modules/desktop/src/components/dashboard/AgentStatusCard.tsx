import React from 'react';
import { Box, Chip, Divider, LinearProgress, Stack, Typography } from '@mui/material';
import InfoCard from '../common/InfoCard';
import StatusBadge from '../common/StatusBadge';
import SensorsIcon from '@mui/icons-material/Sensors';
import AccessTimeIcon from '@mui/icons-material/AccessTime';

export interface AgentStatusCardProps {
  name: string;
  version: string;
  connected: boolean;
  uptimeSeconds: number;
  lastHeartbeat: string;
  queue: {
    depth: number;
    capacity: number;
    highWatermark: number;
    lowWatermark: number;
  };
  exporters: Array<{
    id: string;
    name: string;
    status: string;
    lastSuccess: string | null;
    lastError: string | null;
    latencyMsP95: number;
  }>;
}

const formatDuration = (seconds: number) => {
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3600);
  return `${days}d ${hours}h`;
};

export const AgentStatusCard: React.FC<AgentStatusCardProps> = ({
  name,
  version,
  connected,
  uptimeSeconds,
  lastHeartbeat,
  queue,
  exporters,
}) => {
  const utilisation = Math.round((queue.depth / queue.capacity) * 100);

  return (
    <InfoCard
      title="Agent Bridge"
      subtitle={connected ? 'Connected' : 'Disconnected'}
      icon={<SensorsIcon />}
      action={<StatusBadge status={connected ? 'healthy' : 'disconnected'} />}
    >
      <Stack spacing={3}>
        <Stack direction="row" justifyContent="space-between">
          <Stack spacing={0.5}>
            <Typography variant="subtitle1" fontWeight={600}>
              {name}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Agent version {version}
            </Typography>
          </Stack>
          <Stack alignItems="flex-end">
            <Stack direction="row" alignItems="center" spacing={0.5}>
              <AccessTimeIcon fontSize="small" />
              <Typography variant="body2">Uptime {formatDuration(uptimeSeconds)}</Typography>
            </Stack>
            <Typography variant="caption" color="text.secondary">
              Last heartbeat {new Date(lastHeartbeat).toLocaleTimeString()}
            </Typography>
          </Stack>
        </Stack>

        <Stack spacing={1}>
          <Typography variant="caption" color="text.secondary">
            Queue utilisation
          </Typography>
          <LinearProgress
            variant="determinate"
            value={utilisation}
            sx={{
              height: 10,
              borderRadius: 6,
              backgroundColor: 'rgba(148, 163, 184, 0.18)',
            }}
          />
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="body2">{queue.depth.toLocaleString()} of {queue.capacity.toLocaleString()}</Typography>
            <Typography variant="caption" color="text.secondary">
              High watermark {queue.highWatermark.toLocaleString()}
            </Typography>
          </Stack>
        </Stack>

        <Divider />

        <Stack spacing={1.5}>
          <Typography variant="caption" color="text.secondary">
            Exporters
          </Typography>
          {exporters.map((exporter) => (
            <Stack
              key={exporter.id}
              direction="row"
              justifyContent="space-between"
              alignItems="center"
              sx={{
                borderRadius: 2,
                border: '1px solid rgba(148, 163, 184, 0.14)',
                p: 1.5,
                backgroundColor: 'rgba(15, 23, 42, 0.65)',
              }}
            >
              <Box>
                <Typography variant="subtitle2">{exporter.name}</Typography>
                <Typography variant="caption" color="text.secondary">
                  P95 latency {exporter.latencyMsP95} ms
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                {exporter.lastError ? (
                  <Chip
                    size="small"
                    color="warning"
                    label="Attention"
                    sx={{ fontWeight: 600, letterSpacing: 0.4 }}
                  />
                ) : null}
                <StatusBadge status={exporter.status} />
              </Stack>
            </Stack>
          ))}
        </Stack>
      </Stack>
    </InfoCard>
  );
};

export default AgentStatusCard;
