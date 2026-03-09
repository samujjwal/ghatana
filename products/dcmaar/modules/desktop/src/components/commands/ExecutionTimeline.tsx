import React from 'react';
import { Chip, Stack, Typography } from '@mui/material';
import InfoCard from '../common/InfoCard';
import StatusBadge from '../common/StatusBadge';

export interface ExecutionRecord {
  id: string;
  commandId: string;
  operator: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  target: string;
  correlationId: string;
  logs: string[];
}

export interface ExecutionTimelineProps {
  items: ExecutionRecord[];
}

export const ExecutionTimeline: React.FC<ExecutionTimelineProps> = ({ items }) => {
  return (
    <InfoCard
      title="Execution Timeline"
      subtitle="Recent command runs with operator context and results"
    >
      <Stack spacing={2}>
        {items.map((item) => (
          <Stack
            key={item.id}
            spacing={1}
            sx={{
              borderRadius: 2,
              border: '1px solid rgba(148, 163, 184, 0.14)',
              p: 1.5,
            }}
          >
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle2">{item.commandId}</Typography>
              <StatusBadge status={item.status} />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Target <strong>{item.target}</strong>
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Operator {item.operator} · Started {new Date(item.startedAt).toLocaleTimeString()}
            </Typography>
            <Stack direction="row" spacing={1}>
              <Chip
                label={`Correlation ${item.correlationId}`}
                size="small"
                variant="outlined"
              />
              {item.completedAt ? (
                <Chip
                  label={`Elapsed ${Math.round(
                    (new Date(item.completedAt).getTime() -
                      new Date(item.startedAt).getTime()) /
                      1000,
                  )}s`}
                  size="small"
                />
              ) : null}
            </Stack>
            <Stack spacing={0.5} mt={1}>
              {item.logs.map((log, index) => (
                <Typography variant="caption" color="text.secondary" key={index}>
                  {log}
                </Typography>
              ))}
            </Stack>
          </Stack>
        ))}
      </Stack>
    </InfoCard>
  );
};

export default ExecutionTimeline;
