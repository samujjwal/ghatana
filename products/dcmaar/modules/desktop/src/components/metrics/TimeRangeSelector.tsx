import React from 'react';
import { ToggleButton, ToggleButtonGroup, Box, Typography } from '@mui/material';

type TimeRange = '24h' | '7d' | '30d' | '90d';

interface TimeRangeSelectorProps {
  selectedRange: TimeRange;
  onRangeChange: (range: TimeRange) => void;
  disabled?: boolean;
}

const timeRanges: { value: TimeRange; label: string }[] = [
  { value: '24h', label: '24h' },
  { value: '7d', label: '7d' },
  { value: '30d', label: '30d' },
  { value: '90d', label: '90d' },
];

export const TimeRangeSelector: React.FC<TimeRangeSelectorProps> = ({
  selectedRange,
  onRangeChange,
  disabled = false,
}) => {
  const handleChange = (
    event: React.MouseEvent<HTMLElement>,
    newRange: TimeRange | null,
  ) => {
    if (newRange !== null) {
      onRangeChange(newRange);
    }
  };

  return (
    <Box display="flex" alignItems="center" gap={2}>
      <Typography variant="body2" color="text.secondary">
        Time Range:
      </Typography>
      <ToggleButtonGroup
        value={selectedRange}
        exclusive
        onChange={handleChange}
        aria-label="time range"
        size="small"
        disabled={disabled}
      >
        {timeRanges.map((range) => (
          <ToggleButton
            key={range.value}
            value={range.value}
            aria-label={range.label}
            sx={{
              px: 2,
              '&.Mui-selected, &.Mui-selected:hover': {
                backgroundColor: 'primary.main',
                color: 'primary.contrastText',
              },
            }}
          >
            {range.label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
    </Box>
  );
};

export default TimeRangeSelector;
