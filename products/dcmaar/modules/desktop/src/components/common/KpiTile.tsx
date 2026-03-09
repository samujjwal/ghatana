import React from 'react';
import { Box, Stack, Typography } from '../../ui/tw-compat';
import StatusBadge, { StatusVariant } from './StatusBadge';

export interface KpiTileProps {
  label: string;
  value: string;
  trend?: string;
  status?: StatusVariant;
  icon?: React.ReactNode;
  caption?: string;
}

export const KpiTile: React.FC<KpiTileProps> = ({ label, value, trend, status, icon, caption }) => {
  return (
    <Stack
      spacing={1}
      className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 h-full"
    >
      <Stack direction="row" className="justify-between items-start">
        <Stack spacing={0}>
          <Typography variant="body2" color="text.secondary">
            {label}
          </Typography>
          <Typography variant="h5" className="font-semibold">
            {value}
          </Typography>
        </Stack>
        {icon ? <Box className="text-primary-600 dark:text-primary-400">{icon}</Box> : null}
      </Stack>

      {trend ? (
        <Typography variant="body2" className={trend.startsWith('-') ? 'text-red-600' : 'text-green-600'}>
          {trend}
        </Typography>
      ) : null}

      {status ? <StatusBadge status={status} /> : null}

      {caption ? (
        <Typography variant="caption" color="text.disabled">
          {caption}
        </Typography>
      ) : null}
    </Stack>
  );
};

export default KpiTile;
