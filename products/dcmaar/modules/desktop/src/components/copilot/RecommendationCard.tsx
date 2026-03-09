import React from 'react';
import { Box, Button, Chip, Stack, Typography } from '@mui/material';
import InfoCard from '../common/InfoCard';
import SmartToyIcon from '@mui/icons-material/SmartToy';

export interface CopilotRecommendation {
  id: string;
  title: string;
  summary: string;
  confidence: number;
  risk: string;
  updatedAt: string;
  actions: Array<{ id: string; name: string }>;
}

export interface RecommendationCardProps {
  recommendation: CopilotRecommendation;
  onExecute?: (id: string) => void;
}

export const RecommendationCard: React.FC<RecommendationCardProps> = ({
  recommendation,
  onExecute,
}) => {
  return (
    <InfoCard
      title={recommendation.title}
      subtitle={`Confidence ${Math.round(recommendation.confidence * 100)}%`}
      icon={<SmartToyIcon />}
      action={
        <Button variant="contained" size="small" onClick={() => onExecute?.(recommendation.id)}>
          Apply
        </Button>
      }
      footer={
        <Typography variant="caption" color="text.secondary">
          Updated {new Date(recommendation.updatedAt).toLocaleTimeString()}
        </Typography>
      }
    >
      <Stack spacing={2}>
        <Typography variant="body2" color="text.secondary">
          {recommendation.summary}
        </Typography>
        <Chip label={`Risk ${recommendation.risk}`} color="warning" size="small" />
        {recommendation.actions.length ? (
          <Box>
            <Typography variant="caption" color="text.secondary">
              Suggested actions
            </Typography>
            <Stack direction="row" spacing={1} mt={1} flexWrap="wrap">
              {recommendation.actions.map((action) => (
                <Chip key={action.id} label={action.name} size="small" />
              ))}
            </Stack>
          </Box>
        ) : null}
      </Stack>
    </InfoCard>
  );
};

export default RecommendationCard;
