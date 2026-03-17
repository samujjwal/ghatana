/**
 * Timeline Component Stories
 *
 * @module DevSecOps/Timeline/stories
 */

/* eslint-disable storybook/no-renderer-packages */
import { Box, Surface as Paper, Typography } from '@ghatana/ui';
import { useState } from 'react';

import { Timeline } from './Timeline';

import type { Meta, StoryObj } from '@storybook/react';
import type { Item, Milestone, Phase } from '@ghatana/yappc-types/devsecops';
 

const meta: Meta<typeof Timeline> = {
  title: 'DevSecOps/Timeline',
  component: Timeline,
  tags: ['autodocs'],
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Timeline>;

// Mock data
const mockItems: Item[] = [
  {
    id: '1',
    title: 'Authentication Implementation',
    status: 'in-progress',
    priority: 'high',
    phaseId: 'phase-1',
    startDate: new Date('2025-10-15'),
    endDate: new Date('2025-10-25'),
    createdAt: new Date('2025-10-01'),
    updatedAt: new Date('2025-10-15'),
  },
  {
    id: '2',
    title: 'UI/UX Design',
    status: 'completed',
    priority: 'medium',
    phaseId: 'phase-1',
    startDate: new Date('2025-10-12'),
    endDate: new Date('2025-10-18'),
    createdAt: new Date('2025-10-01'),
    updatedAt: new Date('2025-10-18'),
  },
  {
    id: '3',
    title: 'API Development',
    status: 'in-review',
    priority: 'high',
    phaseId: 'phase-2',
    startDate: new Date('2025-10-20'),
    endDate: new Date('2025-10-30'),
    createdAt: new Date('2025-10-01'),
    updatedAt: new Date('2025-10-20'),
  },
  {
    id: '4',
    title: 'Database Schema',
    status: 'completed',
    priority: 'critical',
    phaseId: 'phase-1',
    startDate: new Date('2025-10-10'),
    endDate: new Date('2025-10-15'),
    createdAt: new Date('2025-10-01'),
    updatedAt: new Date('2025-10-15'),
  },
  {
    id: '5',
    title: 'Security Audit',
    status: 'not-started',
    priority: 'high',
    phaseId: 'phase-3',
    startDate: new Date('2025-11-01'),
    endDate: new Date('2025-11-10'),
    createdAt: new Date('2025-10-01'),
    updatedAt: new Date('2025-10-01'),
  },
  {
    id: '6',
    title: 'Performance Testing',
    status: 'blocked',
    priority: 'medium',
    phaseId: 'phase-3',
    startDate: new Date('2025-10-28'),
    endDate: new Date('2025-11-05'),
    createdAt: new Date('2025-10-01'),
    updatedAt: new Date('2025-10-25'),
  },
];

const mockMilestones: Milestone[] = [
  {
    id: 'm1',
    title: 'Sprint 1 Complete',
    dueDate: '2025-10-18',
    description: 'First sprint deliverables',
    status: 'completed',
  },
  {
    id: 'm2',
    title: 'MVP Release',
    dueDate: '2025-10-30',
    description: 'Minimum viable product',
    status: 'in-progress',
  },
  {
    id: 'm3',
    title: 'Security Review',
    dueDate: '2025-11-08',
    description: 'Security audit checkpoint',
    status: 'pending',
  },
];

const mockPhases: Phase[] = [
  {
    id: 'phase-1',
    name: 'Planning & Design',
    description: 'Initial planning and design phase',
    order: 1,
    startDate: new Date('2025-10-10'),
    endDate: new Date('2025-10-20'),
    color: '#dbeafe',
  },
  {
    id: 'phase-2',
    name: 'Development',
    description: 'Core development phase',
    order: 2,
    startDate: new Date('2025-10-20'),
    endDate: new Date('2025-10-31'),
    color: '#e0e7ff',
  },
  {
    id: 'phase-3',
    name: 'Testing & QA',
    description: 'Testing and quality assurance',
    order: 3,
    startDate: new Date('2025-11-01'),
    endDate: new Date('2025-11-10'),
    color: '#fef3c7',
  },
];

/**
 * Default timeline view
 */
export const Default: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline items={mockItems} />
    </Box>
  ),
};

/**
 * Timeline with all features
 */
export const WithAllFeatures: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline
        items={mockItems}
        milestones={mockMilestones}
        phases={mockPhases}
        showToday
      />
    </Box>
  ),
};

/**
 * Day view mode
 */
export const DayView: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline
        items={mockItems}
        milestones={mockMilestones}
        viewMode="day"
        startDate={new Date('2025-10-15')}
        endDate={new Date('2025-10-25')}
      />
    </Box>
  ),
};

/**
 * Week view mode (default)
 */
export const WeekView: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline
        items={mockItems}
        milestones={mockMilestones}
        viewMode="week"
      />
    </Box>
  ),
};

/**
 * Month view mode
 */
export const MonthView: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline
        items={mockItems}
        milestones={mockMilestones}
        phases={mockPhases}
        viewMode="month"
      />
    </Box>
  ),
};

/**
 * Quarter view mode
 */
export const QuarterView: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline
        items={mockItems}
        milestones={mockMilestones}
        phases={mockPhases}
        viewMode="quarter"
        startDate={new Date('2025-10-01')}
        endDate={new Date('2025-12-31')}
      />
    </Box>
  ),
};

/**
 * With milestones only
 */
export const WithMilestones: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline items={mockItems} milestones={mockMilestones} />
    </Box>
  ),
};

/**
 * With phase backgrounds
 */
export const WithPhases: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline items={mockItems} phases={mockPhases} />
    </Box>
  ),
};

/**
 * Interactive example with click handlers
 */
export const Interactive: Story = {
  render: () => {
    const [selectedItem, setSelectedItem] = useState<Item | null>(null);
    const [selectedMilestone, setSelectedMilestone] = useState<Milestone | null>(null);

    return (
      <Box className="p-6">
        <Timeline
          items={mockItems}
          milestones={mockMilestones}
          phases={mockPhases}
          onItemClick={setSelectedItem}
          onMilestoneClick={setSelectedMilestone}
          showToday
        />

        {selectedItem && (
          <Paper className="mt-4 p-4 bg-blue-50" >
            <Typography as="h6">Selected Item</Typography>
            <Typography as="p" className="text-sm">
              <strong>Title:</strong> {selectedItem.title}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>Status:</strong> {selectedItem.status}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>Priority:</strong> {selectedItem.priority}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>Dates:</strong>{' '}
              {selectedItem.startDate?.toLocaleDateString()} -{' '}
              {selectedItem.endDate?.toLocaleDateString()}
            </Typography>
          </Paper>
        )}

        {selectedMilestone && (
          <Paper className="mt-4 p-4" style={{ backgroundColor: 'warning.50' }} >
            <Typography as="h6">Selected Milestone</Typography>
            <Typography as="p" className="text-sm">
              <strong>Title:</strong> {selectedMilestone.title}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>Date:</strong> {new Date(selectedMilestone.dueDate).toLocaleDateString()}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>Description:</strong> {selectedMilestone.description}
            </Typography>
          </Paper>
        )}
      </Box>
    );
  },
};

/**
 * Loading state
 */
export const Loading: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline items={[]} loading />
    </Box>
  ),
};

/**
 * Empty state
 */
export const Empty: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline items={[]} />
      <Typography as="p" className="text-sm" color="text.secondary" className="mt-4 text-center">
        No items to display on timeline
      </Typography>
    </Box>
  ),
};

/**
 * Custom height
 */
export const CustomHeight: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline
        items={mockItems}
        milestones={mockMilestones}
        height={400}
      />
    </Box>
  ),
};

/**
 * Custom date range
 */
export const CustomDateRange: Story = {
  render: () => (
    <Box className="p-6">
      <Timeline
        items={mockItems}
        startDate={new Date('2025-10-01')}
        endDate={new Date('2025-11-30')}
      />
    </Box>
  ),
};

/**
 * Full screen example
 */
export const FullScreen: Story = {
  render: () => (
    <Box className="h-screen flex flex-col">
      <Paper className="p-4 rounded-none">
        <Typography as="h5">Project Timeline</Typography>
        <Typography as="p" className="text-sm" color="text.secondary">
          Gantt-style visualization of project items and milestones
        </Typography>
      </Paper>
      <Box className="flex-1 overflow-hidden">
        <Timeline
          items={mockItems}
          milestones={mockMilestones}
          phases={mockPhases}
          height={window.innerHeight - 100}
          showToday
        />
      </Box>
    </Box>
  ),
};
