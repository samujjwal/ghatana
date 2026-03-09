import React from 'react';
import { Box, Button, Chip, Stack, Typography } from '@mui/material';
import InfoCard from '../common/InfoCard';
import SmartToyIcon from '@mui/icons-material/SmartToy';

export interface RecommendationsCardProps {
  items: Array<{
    id: string;
    title: string;
    summary: string;
    confidence: number;
    risk: string;
    updatedAt: string;
    actions: Array<{
      id: string;
      name: string;
    }>;
  }>;
}

export const RecommendationsCard: React.FC<RecommendationsCardProps> = ({ items }) => {
  return (
    <InfoCard
      title="Copilot Recommendations"
      subtitle="AI generated runbooks prioritised for impact"
      icon={<SmartToyIcon />}
      action={
        <Button color="secondary" variant="outlined" size="small" href="/copilot">
          View Copilot
        </Button>
      }
    >
      <Stack spacing={2}>
        {items.map((item) => (
          <Box
            key={item.id}
            sx={{
              borderRadius: 2,
              border: '1px solid rgba(148, 163, 184, 0.14)',
              p: 1.5,
              background: 'linear-gradient(135deg, rgba(45,106,158,0.18), rgba(15,23,42,0.6))',
            }}
          >
            <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
              <Typography variant="subtitle1">{item.title}</Typography>
              <Chip
                label={`Confidence ${Math.round(item.confidence * 100)}%`}
                size="small"
                color="info"
              />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {item.summary}
            </Typography>
            <Stack direction="row" spacing={1} mt={1.5}>
              <Chip
                label={`Risk ${item.risk}`}
                size="small"
                color={item.risk === 'medium' ? 'warning' : 'default'}
              />
              <Typography variant="caption" color="text.secondary">
                Updated {new Date(item.updatedAt).toLocaleTimeString()}
              </Typography>
            </Stack>
            {item.actions.length ? (
              <Stack direction="row" spacing={1} mt={1.5} flexWrap="wrap">
                {item.actions.map((action) => (
                  <Chip key={action.id} label={action.name} size="small" />
                ))}
              </Stack>
            ) : null}
          </Box>
        ))}
      </Stack>
    </InfoCard>
  );
};

export default RecommendationsCard;
