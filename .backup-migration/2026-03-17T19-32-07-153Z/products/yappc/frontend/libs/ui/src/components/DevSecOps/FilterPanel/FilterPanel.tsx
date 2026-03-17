/**
 * FilterPanel Component
 *
 * Advanced filtering panel with multi-select filters for phases, status,
 * priority, owners, tags, and date ranges.
 *
 * @module DevSecOps/FilterPanel
 */

import { XCircle as ClearIcon } from 'lucide-react';
import { X as CloseIcon } from 'lucide-react';
import { ChevronDown as ExpandMoreIcon } from 'lucide-react';
import { Filter as FilterListIcon } from 'lucide-react';
import { Box, Button, Checkbox, Chip, Drawer, FormControlLabel, IconButton, Surface as Paper, Stack, Typography } from '@ghatana/ui';
import { Accordion, AccordionDetails, AccordionSummary, FormGroup } from '@mui/material';
import { useState } from 'react';

import type { FilterPanelProps } from './types';
import type { ItemStatus, Priority } from '@ghatana/yappc-types/devsecops';


const STATUS_OPTIONS: ItemStatus[] = [
  'not-started',
  'in-progress',
  'in-review',
  'completed',
  'blocked',
];

const PRIORITY_OPTIONS: Priority[] = ['low', 'medium', 'high', 'critical'];

/**
 * FilterPanel - Advanced multi-filter panel
 *
 * @param props - FilterPanel component props
 * @returns Rendered FilterPanel component
 *
 * @example
 * ```tsx
 * <FilterPanel
 *   filters={filterConfig}
 *   onChange={setFilterConfig}
 *   phaseIds={phases.map(p => p.id)}
 *   phaseLabels={phaseLabelMap}
 *   availableTags={['backend', 'frontend', 'api']}
 *   open={filterPanelOpen}
 *   onClose={() => setFilterPanelOpen(false)}
 * />
 * ```
 */
export function FilterPanel({
  filters = {},
  onChange,
  phaseIds = [],
  phaseLabels = {},
  availableTags = [],
  availableOwners: _availableOwners = [],
  open = true,
  onClose,
  variant = 'inline',
}: FilterPanelProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(['status', 'priority'])
  );

  const toggleSection = (section: string) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(section)) {
        next.delete(section);
      } else {
        next.add(section);
      }
      return next;
    });
  };

  const handleStatusToggle = (status: ItemStatus) => {
    if (!onChange) return; // Prevent crash when onChange is null/undefined
    const current = filters?.status || [];
    const next = current.includes(status)
      ? current.filter((s) => s !== status)
      : [...current, status];
    onChange({ ...filters, status: next.length > 0 ? next : undefined });
  };

  const handlePriorityToggle = (priority: Priority) => {
    if (!onChange) return; // Prevent crash when onChange is null/undefined
    const current = filters?.priority || [];
    const next = current.includes(priority)
      ? current.filter((p) => p !== priority)
      : [...current, priority];
    onChange({ ...filters, priority: next.length > 0 ? next : undefined });
  };

  const handlePhaseToggle = (phaseId: string) => {
    if (!onChange) return; // Prevent crash when onChange is null/undefined
    const current = filters?.phaseIds || [];
    const next = current.includes(phaseId)
      ? current.filter((p) => p !== phaseId)
      : [...current, phaseId];
    onChange({ ...filters, phaseIds: next.length > 0 ? next : undefined });
  };

  const handleTagToggle = (tag: string) => {
    if (!onChange) return; // Prevent crash when onChange is null/undefined
    const current = filters?.tags || [];
    const next = current.includes(tag)
      ? current.filter((t) => t !== tag)
      : [...current, tag];
    onChange({ ...filters, tags: next.length > 0 ? next : undefined });
  };

  const handleClearAll = () => {
    if (!onChange) return; // Prevent crash when onChange is null/undefined
    onChange({});
  };

  const activeFilterCount =
    (filters?.status?.length || 0) +
    (filters?.priority?.length || 0) +
    (filters?.phaseIds?.length || 0) +
    (filters?.tags?.length || 0);

  const content = (
    <Box className="p-4">
      {/* Header */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box display="flex" alignItems="center" gap={1}>
          <FilterListIcon />
          <Typography as="h6" fontWeight={600}>
            Filters
          </Typography>
          {activeFilterCount > 0 && (
            <Chip label={activeFilterCount} size="sm" tone="primary" />
          )}
        </Box>
        <Box>
          {activeFilterCount > 0 && (
            <Button
              size="sm"
              startIcon={<ClearIcon />}
              onClick={handleClearAll}
              className="mr-2"
            >
              Clear
            </Button>
          )}
          {variant === 'drawer' && onClose && (
            <IconButton size="sm" onClick={onClose} aria-label="Close filters">
              <CloseIcon />
            </IconButton>
          )}
        </Box>
      </Box>

      {/* Status Filter */}
      <Accordion
        expanded={expandedSections.has('status')}
        onChange={() => toggleSection('status')}
        disableGutters
        variant="flat"
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography as="p" className="text-sm font-medium" fontWeight={600}>
            Status {filters.status?.length ? `(${filters.status.length})` : ''}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <FormGroup>
            {STATUS_OPTIONS.map((status) => (
              <FormControlLabel
                key={status}
                control={
                  <Checkbox
                    checked={filters.status?.includes(status) || false}
                    onChange={() => handleStatusToggle(status)}
                    size="sm"
                  />
                }
                label={status.replace('-', ' ').replace(/\b\w/g, (l) => l.toUpperCase())}
              />
            ))}
          </FormGroup>
        </AccordionDetails>
      </Accordion>

      {/* Priority Filter */}
      <Accordion
        expanded={expandedSections.has('priority')}
        onChange={() => toggleSection('priority')}
        disableGutters
        variant="flat"
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography as="p" className="text-sm font-medium" fontWeight={600}>
            Priority {filters.priority?.length ? `(${filters.priority.length})` : ''}
          </Typography>
        </AccordionSummary>
        <AccordionDetails>
          <FormGroup>
            {PRIORITY_OPTIONS.map((priority) => (
              <FormControlLabel
                key={priority}
                control={
                  <Checkbox
                    checked={filters.priority?.includes(priority) || false}
                    onChange={() => handlePriorityToggle(priority)}
                    size="sm"
                  />
                }
                label={priority.charAt(0).toUpperCase() + priority.slice(1)}
              />
            ))}
          </FormGroup>
        </AccordionDetails>
      </Accordion>

      {/* Phase Filter */}
      {phaseIds.length > 0 && (
        <Accordion
          expanded={expandedSections.has('phases')}
          onChange={() => toggleSection('phases')}
          disableGutters
          variant="flat"
        >
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography as="p" className="text-sm font-medium" fontWeight={600}>
              Phases {filters.phaseIds?.length ? `(${filters.phaseIds.length})` : ''}
            </Typography>
          </AccordionSummary>
          <AccordionDetails>
            <FormGroup>
              {phaseIds.map((phaseId) => (
                <FormControlLabel
                  key={phaseId}
                  control={
                    <Checkbox
                      checked={filters.phaseIds?.includes(phaseId) || false}
                      onChange={() => handlePhaseToggle(phaseId)}
                      size="sm"
                    />
                  }
                  label={phaseLabels[phaseId] || phaseId}
                />
              ))}
            </FormGroup>
          </AccordionDetails>
        </Accordion>
      )}

      {/* Tags Filter */}
      {availableTags.length > 0 && (
        <Accordion
          expanded={expandedSections.has('tags')}
          onChange={() => toggleSection('tags')}
          disableGutters
          variant="flat"
        >
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography as="p" className="text-sm font-medium" fontWeight={600}>
              Tags {filters.tags?.length ? `(${filters.tags.length})` : ''}
            </Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Stack direction="row" flexWrap="wrap" gap={1}>
              {availableTags.map((tag) => (
                <Chip
                  key={tag}
                  label={tag}
                  size="sm"
                  onClick={() => handleTagToggle(tag)}
                  color={filters.tags?.includes(tag) ? 'primary' : 'default'}
                  variant={filters.tags?.includes(tag) ? 'filled' : 'outlined'}
                />
              ))}
            </Stack>
          </AccordionDetails>
        </Accordion>
      )}
    </Box>
  );

  if (variant === 'drawer') {
    return (
      <Drawer anchor="right" open={open} onClose={onClose} className="w-80">
        {content}
        {onClose && (
          <Box className="p-4 border-gray-200 dark:border-gray-700 border-t" >
            <Button variant="solid" fullWidth onClick={onClose}>
              Apply
            </Button>
          </Box>
        )}
      </Drawer>
    );
  }

  return (
    <Paper variant="raised" className="w-[280px]">
      {content}
    </Paper>
  );
}
