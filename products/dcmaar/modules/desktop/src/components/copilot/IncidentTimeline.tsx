import React from 'react';
import { Box, Stack, Typography } from '../../ui/tw-compat';
import InfoCard from '../common/InfoCard';

export interface IncidentTimelineItem {
  timestamp: string;
  title: string;
  description: string;
  action?: { id: string; name: string } | null;
}

export interface IncidentTimelineProps {
  items: IncidentTimelineItem[];
}

export const IncidentTimeline: React.FC<IncidentTimelineProps> = ({ items }) => {
  return (
    <InfoCard title="Incident Timeline" subtitle="Recent signals that shaped these recommendations">
      <Stack spacing={2}>
        {items.map((item) => (
          <Stack
            key={item.timestamp}
            spacing={0}
            className="rounded-lg border border-slate-200/15 p-3"
          >
            <Typography variant="caption" color="text.secondary">
              {new Date(item.timestamp).toLocaleString()}
            </Typography>
            <Typography variant="subtitle1">{item.title}</Typography>
            <Typography variant="body2" color="text.secondary">
              {item.description}
            </Typography>
            {item.action ? (
              <Box>
                <Typography variant="caption" color="text.secondary">
                  Suggested action
                </Typography>
                <Typography variant="body2">{item.action.name}</Typography>
              </Box>
            ) : null}
          </Stack>
        ))}
      </Stack>
    </InfoCard>
  );
};

export default IncidentTimeline;
