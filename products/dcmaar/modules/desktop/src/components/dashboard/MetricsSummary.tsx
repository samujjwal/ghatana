import React from 'react';
import Grid from '@mui/material/Grid';
import InfoCard from '../common/InfoCard';
import TrendSparkline from '../common/TrendSparkline';
import TimelineIcon from '@mui/icons-material/Timeline';

export interface MetricsSummaryProps {
  items: Array<{
    id: string;
    title: string;
    value: string;
    delta?: string;
    deltaColor?: 'success' | 'warning' | 'error' | 'info';
    data: Array<{ timestamp: string; value: number }>;
    unit?: string;
  }>;
}

export const MetricsSummary: React.FC<MetricsSummaryProps> = ({ items }) => {
  return (
    <Grid container spacing={2}>
      {items.map((item) => (
        <Grid key={item.id} size={{ xs: 12, md: 4 }}>
          <InfoCard title={item.title} icon={<TimelineIcon />}>
            <TrendSparkline
              title={item.title}
              value={item.value}
              delta={item.delta}
              deltaColor={item.deltaColor}
              data={item.data}
              unit={item.unit}
            />
          </InfoCard>
        </Grid>
      ))}
    </Grid>
  );
};

export default MetricsSummary;
