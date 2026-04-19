/**
 * Requirement List
 *
 * Display all requirements with filtering and search.
 *
 * @packageDocumentation
 */

import { Search, Filter } from 'lucide-react';
import {
  Box,
  Stack,
  Typography,
  TextField,
  Select,
  MenuItem,
  Paper,
  Chip,
} from '@ghatana/design-system';
import React, { useState, useMemo, useCallback } from 'react';

import type { RequirementConfig } from '@yappc/config-schema';

/**
 * @doc.type component
 * @doc.purpose Display requirements with filtering and search
 * @doc.layer product
 * @doc.pattern List Component
 */
interface RequirementListProps {
  requirements: RequirementConfig[];
  onRequirementSelect?: (requirement: RequirementConfig) => void;
  selectedId?: string;
}

export const RequirementList: React.FC<RequirementListProps> = ({
  requirements,
  onRequirementSelect,
  selectedId,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState<string>('all');
  const [filterPriority, setFilterPriority] = useState<string>('all');
  const [filterStatus, setFilterStatus] = useState<string>('all');

  const filteredRequirements = useMemo(() => {
    return requirements.filter((req) => {
      // Search filter
      if (searchTerm) {
        const searchLower = searchTerm.toLowerCase();
        const matchesSearch =
          req.title.toLowerCase().includes(searchLower) ||
          req.description.toLowerCase().includes(searchLower);
        if (!matchesSearch) return false;
      }

      // Type filter
      if (filterType !== 'all' && req.type !== filterType) return false;

      // Priority filter
      if (filterPriority !== 'all' && req.priority !== filterPriority) return false;

      // Status filter
      if (filterStatus !== 'all' && req.status !== filterStatus) return false;

      return true;
    });
  }, [requirements, searchTerm, filterType, filterPriority, filterStatus]);

  const handleSelect = useCallback(
    (req: RequirementConfig) => {
      onRequirementSelect?.(req);
    },
    [onRequirementSelect]
  );

  const uniqueTypes = useMemo(() => {
    return Array.from(new Set(requirements.map((r) => r.type)));
  }, [requirements]);

  const uniquePriorities = useMemo(() => {
    return Array.from(new Set(requirements.map((r) => r.priority)));
  }, [requirements]);

  const uniqueStatuses = useMemo(() => {
    return Array.from(new Set(requirements.map((r) => r.status)));
  }, [requirements]);

  return (
    <Box data-testid="requirement-list">
      <Typography variant="h6" gutterBottom>
        Requirements ({filteredRequirements.length}/{requirements.length})
      </Typography>

      {/* Search */}
      <TextField
        placeholder="Search requirements..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        fullWidth
        size="small"
        InputProps={{
          startAdornment: <Search size={16} className="mr-2 text-gray-400" />,
        }}
        sx={{ mb: 2 }}
      />

      {/* Filters */}
      <Stack direction="row" spacing={2} mb={2}>
        <TextField
          select
          label="Type"
          value={filterType}
          onChange={(e) => setFilterType(e.target.value)}
          size="small"
          sx={{ minWidth: 120 }}
          InputProps={{
            startAdornment: <Filter size={16} className="mr-1 text-gray-400" />,
          }}
        >
          <MenuItem value="all">All Types</MenuItem>
          {uniqueTypes.map((type) => (
            <MenuItem key={type} value={type}>
              {type}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          label="Priority"
          value={filterPriority}
          onChange={(e) => setFilterPriority(e.target.value)}
          size="small"
          sx={{ minWidth: 120 }}
        >
          <MenuItem value="all">All Priorities</MenuItem>
          {uniquePriorities.map((priority) => (
            <MenuItem key={priority} value={priority}>
              {priority}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          label="Status"
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          size="small"
          sx={{ minWidth: 120 }}
        >
          <MenuItem value="all">All Statuses</MenuItem>
          {uniqueStatuses.map((status) => (
            <MenuItem key={status} value={status}>
              {status}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {/* List */}
      <Stack spacing={2}>
        {filteredRequirements.length === 0 ? (
          <Typography color="text.secondary" variant="body2" textAlign="center" py={4}>
            No requirements match your filters
          </Typography>
        ) : (
          filteredRequirements.map((req) => (
            <Paper
              key={req.id}
              variant="outlined"
              className={`p-3 cursor-pointer transition-colors ${
                selectedId === req.id ? 'border-primary-500 bg-primary-50' : 'hover:bg-gray-50'
              }`}
              onClick={() => handleSelect(req)}
              data-testid={`requirement-item-${req.id}`}
            >
              <Stack spacing={2}>
                <Typography variant="subtitle2" className="font-medium">
                  {req.title}
                </Typography>

                <Typography variant="body2" color="text.secondary" className="line-clamp-2">
                  {req.description}
                </Typography>

                <Stack direction="row" spacing={1} flexWrap="wrap">
                  <Chip
                    label={req.type}
                    size="small"
                    variant="outlined"
                    className="text-xs"
                  />
                  <Chip
                    label={req.priority}
                    size="small"
                    variant="outlined"
                    color={
                      req.priority === 'critical' || req.priority === 'high'
                        ? 'error'
                        : req.priority === 'medium'
                        ? 'warning'
                        : 'default'
                    }
                    className="text-xs"
                  />
                  <Chip
                    label={req.status}
                    size="small"
                    variant="outlined"
                    color={req.status === 'done' ? 'success' : 'default'}
                    className="text-xs"
                  />
                </Stack>

                {req.tags && req.tags.length > 0 && (
                  <Stack direction="row" spacing={0.5} flexWrap="wrap">
                    {req.tags.map((tag) => (
                      <Chip key={tag} label={tag} size="small" variant="outlined" className="text-xs" />
                    ))}
                  </Stack>
                )}
              </Stack>
            </Paper>
          ))
        )}
      </Stack>
    </Box>
  );
};
