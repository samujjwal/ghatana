import React from 'react';
import Chip, { type ChipProps } from '@mui/material/Chip';

const palette = {
  healthy: { color: 'success', label: 'Healthy' },
  active: { color: 'success', label: 'Active' },
  degraded: { color: 'warning', label: 'Degraded' },
  warning: { color: 'warning', label: 'Warning' },
  failed: { color: 'error', label: 'Failed' },
  error: { color: 'error', label: 'Error' },
  pending: { color: 'info', label: 'Pending' },
  disconnected: { color: 'default', label: 'Disconnected' },
  idle: { color: 'default', label: 'Idle' },
} as const;

export type StatusVariant = keyof typeof palette | string;

export interface StatusBadgeProps extends Omit<ChipProps, 'label' | 'color'> {
  status: StatusVariant;
  label?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, label, ...props }) => {
  const entry = palette[status as keyof typeof palette] ?? {
    color: 'default',
    label: status.replace(/_/g, ' '),
  };

  const labelText = String(label ?? entry.label ?? '').toUpperCase();

  return (
    <Chip
      size="small"
      variant="outlined"
      {...props}
      color={entry.color as ChipProps['color']}
      label={labelText}
      sx={{ fontWeight: 600, letterSpacing: 0.5, ...props.sx }}
    />
  );
};

export default StatusBadge;
