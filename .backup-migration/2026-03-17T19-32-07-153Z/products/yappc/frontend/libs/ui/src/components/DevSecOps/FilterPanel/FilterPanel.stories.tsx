/**
 * FilterPanel Component Stories
 *
 * @module DevSecOps/FilterPanel/stories
 */

import { Filter as FilterListIcon } from 'lucide-react';
import { Box, Button, Surface as Paper, Typography, InteractiveList as List, ListItem, ListItemText } from '@ghatana/ui';
import { useState } from 'react';

import { FilterPanel } from './FilterPanel';

import type { Meta, StoryObj } from '@storybook/react';
import type { ItemFilter } from '@ghatana/yappc-types/devsecops';


const meta: Meta<typeof FilterPanel> = {
  title: 'DevSecOps/FilterPanel',
  component: FilterPanel,
  tags: ['autodocs'],
  parameters: {
    layout: 'padded',
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof FilterPanel>;

const mockPhaseIds = ['phase-1', 'phase-2', 'phase-3', 'phase-4', 'phase-5'];
const mockPhaseLabels = {
  'phase-1': 'Ideation',
  'phase-2': 'Planning',
  'phase-3': 'Development',
  'phase-4': 'Testing',
  'phase-5': 'Deployment',
};

const mockTags = [
  'backend',
  'frontend',
  'api',
  'security',
  'performance',
  'ui/ux',
  'database',
  'testing',
];

/**
 * Interactive wrapper for stories
 */
function InteractiveWrapper(props: Omit<React.ComponentProps<typeof FilterPanel>, 'filters' | 'onChange'>) {
  const [filters, setFilters] = useState<ItemFilter>({});

  return (
    <Box className="p-6">
      <FilterPanel {...props} filters={filters} onChange={setFilters} />

      <Paper className="mt-6 p-4 bg-gray-50 dark:bg-gray-800">
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Active Filters:
        </Typography>
        <pre style={{ fontSize: '12px', overflow: 'auto' }}>
          {JSON.stringify(filters, null, 2)}
        </pre>
      </Paper>
    </Box>
  );
}

/**
 * Default inline filter panel
 */
export const Default: Story = {
  render: () => <InteractiveWrapper />,
};

/**
 * With all filter options
 */
export const WithAllFilters: Story = {
  render: () => (
    <InteractiveWrapper
      phaseIds={mockPhaseIds}
      phaseLabels={mockPhaseLabels}
      availableTags={mockTags}
    />
  ),
};

/**
 * With active filters
 */
export const WithActiveFilters: Story = {
  render: () => {
    const [filters, setFilters] = useState<ItemFilter>({
      status: ['in-progress', 'in-review'],
      priority: ['high', 'critical'],
      phaseIds: ['phase-3', 'phase-4'],
      tags: ['backend', 'security'],
    });

    return (
      <Box className="p-6">
        <FilterPanel
          filters={filters}
          onChange={setFilters}
          phaseIds={mockPhaseIds}
          phaseLabels={mockPhaseLabels}
          availableTags={mockTags}
        />

        <Paper className="mt-6 p-4">
          <Typography as="h6" gutterBottom>
            Filter Summary
          </Typography>
          <Typography as="p" className="text-sm">
            • Status: {filters.status?.join(', ')}
          </Typography>
          <Typography as="p" className="text-sm">
            • Priority: {filters.priority?.join(', ')}
          </Typography>
          <Typography as="p" className="text-sm">
            • Phases: {filters.phaseIds?.map(id => mockPhaseLabels[id]).join(', ')}
          </Typography>
          <Typography as="p" className="text-sm">
            • Tags: {filters.tags?.join(', ')}
          </Typography>
        </Paper>
      </Box>
    );
  },
};

/**
 * Drawer variant
 */
export const DrawerVariant: Story = {
  render: () => {
    const [filters, setFilters] = useState<ItemFilter>({});
    const [open, setOpen] = useState(false);

    return (
      <Box className="p-6">
        <Button
          variant="solid"
          startIcon={<FilterListIcon />}
          onClick={() => setOpen(true)}
        >
          Open Filters
        </Button>

        <FilterPanel
          filters={filters}
          onChange={setFilters}
          variant="drawer"
          open={open}
          onClose={() => setOpen(false)}
          phaseIds={mockPhaseIds}
          phaseLabels={mockPhaseLabels}
          availableTags={mockTags}
        />

        <Paper className="mt-6 p-4 bg-gray-50 dark:bg-gray-800">
          <Typography as="p" className="text-sm font-medium" gutterBottom>
            Active Filters:
          </Typography>
          <pre style={{ fontSize: '12px' }}>{JSON.stringify(filters, null, 2)}</pre>
        </Paper>
      </Box>
    );
  },
};

/**
 * Status and Priority only
 */
export const StatusAndPriorityOnly: Story = {
  render: () => <InteractiveWrapper />,
};

/**
 * With phases only
 */
export const WithPhasesOnly: Story = {
  render: () => (
    <InteractiveWrapper
      phaseIds={mockPhaseIds}
      phaseLabels={mockPhaseLabels}
    />
  ),
};

/**
 * With tags only
 */
export const WithTagsOnly: Story = {
  render: () => <InteractiveWrapper availableTags={mockTags} />,
};

/**
 * Full featured example with filtering
 */
export const FullFeaturedExample: Story = {
  render: () => {
    const [filters, setFilters] = useState<ItemFilter>({});

    // Mock data
    const allItems = [
      { id: '1', title: 'Implement auth', status: 'in-progress', priority: 'high', phase: 'phase-3', tags: ['backend', 'security'] },
      { id: '2', title: 'Design UI', status: 'not-started', priority: 'medium', phase: 'phase-2', tags: ['frontend', 'ui/ux'] },
      { id: '3', title: 'Write tests', status: 'in-progress', priority: 'low', phase: 'phase-3', tags: ['testing'] },
      { id: '4', title: 'API integration', status: 'in-review', priority: 'high', phase: 'phase-3', tags: ['backend', 'api'] },
      { id: '5', title: 'Deploy to prod', status: 'blocked', priority: 'critical', phase: 'phase-5', tags: ['backend'] },
      { id: '6', title: 'Performance tune', status: 'completed', priority: 'medium', phase: 'phase-4', tags: ['performance', 'backend'] },
    ];

    // Filter items
    const filteredItems = allItems.filter((item) => {
      if (filters.status?.length && !filters.status.includes(item.status as unknown)) return false;
      if (filters.priority?.length && !filters.priority.includes(item.priority as unknown)) return false;
      if (filters.phaseIds?.length && !filters.phaseIds.includes(item.phase)) return false;
      if (filters.tags?.length && !filters.tags.some(tag => item.tags.includes(tag))) return false;
      return true;
    });

    return (
      <Box className="p-6 grid gap-6" style={{ gridTemplateColumns: '280px 1fr' }} >
        <Box>
          <FilterPanel
            filters={filters}
            onChange={setFilters}
            phaseIds={mockPhaseIds}
            phaseLabels={mockPhaseLabels}
            availableTags={mockTags}
          />
        </Box>

        <Box>
          <Paper className="p-4">
            <Typography as="h6" gutterBottom>
              Filtered Results ({filteredItems.length}/{allItems.length})
            </Typography>
            <List>
              {filteredItems.length > 0 ? (
                filteredItems.map((item) => (
                  <ListItem key={item.id}>
                    <ListItemText
                      primary={item.title}
                      secondary={`${item.status} • ${item.priority} • ${mockPhaseLabels[item.phase as keyof typeof mockPhaseLabels]} • ${item.tags.join(', ')}`}
                    />
                  </ListItem>
                ))
              ) : (
                <Typography as="p" className="text-sm" color="text.secondary">
                  No items match the current filters
                </Typography>
              )}
            </List>
          </Paper>
        </Box>
      </Box>
    );
  },
};

/**
 * Mobile responsive example
 */
export const MobileResponsive: Story = {
  render: () => {
    const [filters, setFilters] = useState<ItemFilter>({});
    const [open, setOpen] = useState(false);

    return (
      <Box className="p-4 max-w-[375px] mx-auto bg-gray-100 dark:bg-gray-800 min-h-screen">
        <Paper className="p-4 mb-4">
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Typography as="h6">Items</Typography>
            <Button
              variant="outlined"
              size="sm"
              startIcon={<FilterListIcon />}
              onClick={() => setOpen(true)}
            >
              Filters
              {(filters.status?.length || 0) + (filters.priority?.length || 0) > 0 && (
                <Box
                  component="span"
                  className="ml-2 rounded-full inline-flex items-center justify-center text-xs bg-blue-600 text-white w-[20px] h-[20px]"
                >
                  {(filters.status?.length || 0) + (filters.priority?.length || 0)}
                </Box>
              )}
            </Button>
          </Box>
        </Paper>

        <FilterPanel
          filters={filters}
          onChange={setFilters}
          variant="drawer"
          open={open}
          onClose={() => setOpen(false)}
          phaseIds={mockPhaseIds}
          phaseLabels={mockPhaseLabels}
          availableTags={mockTags}
        />

        <Typography as="p" className="text-sm" color="text.secondary" className="mt-4">
          This demonstrates the drawer variant optimized for mobile devices
        </Typography>
      </Box>
    );
  },
};

/**
 * Empty state (no optional filters)
 */
export const EmptyState: Story = {
  render: () => {
    const [filters, setFilters] = useState<ItemFilter>({});

    return (
      <Box className="p-6">
        <FilterPanel
          filters={filters}
          onChange={setFilters}
          phaseIds={[]}
          availableTags={[]}
        />

        <Typography as="p" className="text-sm" color="text.secondary" className="mt-4">
          Only status and priority filters are available when no phases or tags are provided
        </Typography>
      </Box>
    );
  },
};

/**
 * All filters comparison
 */
export const AllFiltersComparison: Story = {
  render: () => {
    const [filters1, setFilters1] = useState<ItemFilter>({});
    const [filters2, setFilters2] = useState<ItemFilter>({});

    return (
      <Box className="p-6 grid gap-6 grid-cols-2">
        <Box>
          <Typography as="h6" gutterBottom>
            Basic Filters
          </Typography>
          <FilterPanel filters={filters1} onChange={setFilters1} />
        </Box>

        <Box>
          <Typography as="h6" gutterBottom>
            All Filters
          </Typography>
          <FilterPanel
            filters={filters2}
            onChange={setFilters2}
            phaseIds={mockPhaseIds}
            phaseLabels={mockPhaseLabels}
            availableTags={mockTags}
          />
        </Box>
      </Box>
    );
  },
};
