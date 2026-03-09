import React from 'react';
import { Box, Chip, IconButton, Stack, TextField, Typography } from '@mui/material';
import ClearAllIcon from '@mui/icons-material/ClearAll';

export interface FilterOption {
  label: string;
  value: string;
}

export interface FilterToolbarProps {
  title?: string;
  searchPlaceholder?: string;
  searchValue?: string;
  onSearchChange?: (value: string) => void;
  activeFilters?: FilterOption[];
  onClearFilters?: () => void;
  endAdornment?: React.ReactNode;
}

export const FilterToolbar: React.FC<FilterToolbarProps> = ({
  title,
  searchPlaceholder = 'Search',
  searchValue,
  onSearchChange,
  activeFilters,
  onClearFilters,
  endAdornment,
}) => {
  return (
    <Stack spacing={2}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        alignItems={{ xs: 'stretch', md: 'center' }}
        justifyContent="space-between"
        gap={2}
      >
        <Stack spacing={0.5}>
          {title ? (
            <Typography variant="h6" fontWeight={600}>
              {title}
            </Typography>
          ) : null}
          <Typography variant="body2" color="text.secondary">
            Filter and refine the dataset using the controls below.
          </Typography>
        </Stack>

        <Stack direction="row" spacing={1} alignItems="center">
          <TextField
            size="small"
            label="Search"
            value={searchValue ?? ''}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              onSearchChange?.(event.target.value)
            }
            placeholder={searchPlaceholder}
            sx={{
              minWidth: 220,
            }}
          />
          {endAdornment}
        </Stack>
      </Stack>

      {activeFilters && activeFilters.length > 0 ? (
        <Stack direction="row" flexWrap="wrap" gap={1}>
          {activeFilters.map((filter) => (
            <Chip key={filter.value} label={filter.label} />
          ))}
          {onClearFilters ? (
            <IconButton
              size="small"
              onClick={onClearFilters}
              sx={{ ml: 1 }}
              aria-label="Clear filters"
            >
              <ClearAllIcon fontSize="small" />
            </IconButton>
          ) : null}
        </Stack>
      ) : (
        <Box sx={{ borderTop: '1px solid', borderColor: 'divider' }} />
      )}
    </Stack>
  );
};

export default FilterToolbar;
