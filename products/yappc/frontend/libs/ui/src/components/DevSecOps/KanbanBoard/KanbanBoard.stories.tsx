/**
 * KanbanBoard Component Stories
 *
 * @module DevSecOps/KanbanBoard/stories
 */

import { Box, Surface as Paper, Typography } from '@ghatana/ui';
import { useState } from 'react';

import { KanbanBoard } from './KanbanBoard';

import type { KanbanColumn, KanbanItemMoveEvent } from './types';
import type { Meta, StoryObj } from '@storybook/react';
import type { Item } from '@ghatana/yappc-types/devsecops';



// Mock data
const mockItems: Item[] = [
  {
    id: 'item-1',
    title: 'Implement user authentication',
    description: 'Add OAuth2 and JWT token support',
    type: 'feature',
    priority: 'high',
    status: 'in-progress',
    phaseId: 'phase-1',
    owners: [
      { id: 'user-1', name: 'Alice Smith', email: 'alice@example.com', avatar: '' },
    ],
    artifacts: [
      { id: 'art-1', type: 'document', title: 'Tech Spec', url: '#', itemId: 'item-1', createdAt: new Date() },
    ],
    progress: 60,
    tags: ['security', 'backend'],
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-01-10'),
  },
  {
    id: 'item-2',
    title: 'Fix login page styling',
    description: 'Update CSS to match design system',
    type: 'bug',
    priority: 'medium',
    status: 'in-review',
    phaseId: 'phase-1',
    owners: [
      { id: 'user-2', name: 'Bob Johnson', email: 'bob@example.com', avatar: '' },
    ],
    artifacts: [],
    progress: 90,
    tags: ['frontend', 'ui'],
    createdAt: new Date('2024-01-02'),
    updatedAt: new Date('2024-01-11'),
  },
  {
    id: 'item-3',
    title: 'Write unit tests for API',
    description: 'Achieve 80% code coverage',
    type: 'task',
    priority: 'medium',
    status: 'not-started',
    phaseId: 'phase-1',
    owners: [],
    artifacts: [],
    progress: 0,
    tags: ['testing'],
    createdAt: new Date('2024-01-03'),
    updatedAt: new Date('2024-01-03'),
  },
  {
    id: 'item-4',
    title: 'Database migration blocked',
    description: 'Waiting for DBA approval',
    type: 'task',
    priority: 'critical',
    status: 'blocked',
    phaseId: 'phase-1',
    owners: [
      { id: 'user-3', name: 'Carol Davis', email: 'carol@example.com', avatar: '' },
    ],
    artifacts: [],
    progress: 50,
    tags: ['database', 'blocked'],
    createdAt: new Date('2024-01-04'),
    updatedAt: new Date('2024-01-12'),
  },
  {
    id: 'item-5',
    title: 'User documentation',
    description: 'Complete user guide and API docs',
    type: 'task',
    priority: 'low',
    status: 'completed',
    phaseId: 'phase-1',
    owners: [
      { id: 'user-4', name: 'Dave Wilson', email: 'dave@example.com', avatar: '' },
    ],
    artifacts: [
      { id: 'art-2', type: 'document', title: 'User Guide', url: '#', itemId: 'item-5', createdAt: new Date() },
    ],
    progress: 100,
    tags: ['documentation'],
    createdAt: new Date('2024-01-05'),
    updatedAt: new Date('2024-01-13'),
  },
  {
    id: 'item-6',
    title: 'Add search functionality',
    description: 'Implement full-text search with filters',
    type: 'feature',
    priority: 'high',
    status: 'in-progress',
    phaseId: 'phase-2',
    owners: [
      { id: 'user-1', name: 'Alice Smith', email: 'alice@example.com', avatar: '' },
    ],
    artifacts: [],
    progress: 30,
    tags: ['feature', 'backend'],
    createdAt: new Date('2024-01-06'),
    updatedAt: new Date('2024-01-14'),
  },
  {
    id: 'item-7',
    title: 'Performance optimization',
    description: 'Reduce page load time by 50%',
    type: 'task',
    priority: 'high',
    status: 'in-progress',
    phaseId: 'phase-2',
    owners: [],
    artifacts: [],
    progress: 45,
    tags: ['performance'],
    createdAt: new Date('2024-01-07'),
    updatedAt: new Date('2024-01-15'),
  },
];

const meta: Meta<typeof KanbanBoard> = {
  title: 'DevSecOps/KanbanBoard',
  component: KanbanBoard,
  tags: ['autodocs'],
  parameters: {
    layout: 'fullscreen',
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof KanbanBoard>;

/**
 * Default Kanban board with all columns
 */
export const Default: Story = {
  render: () => {
    const [items, setItems] = useState(mockItems);
    const [lastMove, setLastMove] = useState<KanbanItemMoveEvent | null>(null);

    const handleItemMove = (event: KanbanItemMoveEvent) => {
      setLastMove(event);
      // Update item status in local state
      setItems((prev) =>
        prev.map((item) =>
          item.id === event.item.id ? { ...item, status: event.targetStatus } : item
        )
      );
    };

    return (
      <Box className="p-6">
        <Typography as="h4" gutterBottom>
          DevSecOps Kanban Board
        </Typography>
        <KanbanBoard
          items={items}
          onItemMove={handleItemMove}
          onItemClick={(item) => console.log('Item clicked:', item)}
        />
        {lastMove && (
          <Paper className="p-4 mt-4 bg-gray-100 dark:bg-gray-800">
            <Typography as="p" className="text-sm">
              <strong>Last Move:</strong> {lastMove.item.title} from {lastMove.sourceStatus} to{' '}
              {lastMove.targetStatus}
            </Typography>
          </Paper>
        )}
      </Box>
    );
  },
};

/**
 * Kanban board without WIP limits
 */
export const WithoutWipLimits: Story = {
  render: () => (
    <Box className="p-6">
      <KanbanBoard items={mockItems} showWipLimits={false} />
    </Box>
  ),
};

/**
 * Kanban board with custom columns
 */
export const CustomColumns: Story = {
  render: () => {
    const customColumns: KanbanColumn[] = [
      {
        id: 'backlog',
        title: 'Backlog',
        status: 'not-started',
        color: '#6B7280',
        order: 1,
      },
      {
        id: 'active',
        title: 'Active Work',
        status: 'in-progress',
        color: '#3B82F6',
        wipLimit: 3,
        order: 2,
      },
      {
        id: 'done',
        title: 'Done',
        status: 'completed',
        color: '#10B981',
        order: 3,
      },
    ];

    return (
      <Box className="p-6">
        <Typography as="h5" gutterBottom>
          Simplified Workflow
        </Typography>
        <KanbanBoard items={mockItems} columns={customColumns} />
      </Box>
    );
  },
};

/**
 * Loading state
 */
export const Loading: Story = {
  args: {
    items: [],
    loading: true,
  },
  render: (args) => (
    <Box className="p-6">
      <KanbanBoard {...args} />
    </Box>
  ),
};

/**
 * Empty board with no items
 */
export const Empty: Story = {
  args: {
    items: [],
  },
  render: (args) => (
    <Box className="p-6">
      <Typography as="h5" gutterBottom>
        Empty Kanban Board
      </Typography>
      <KanbanBoard {...args} />
    </Box>
  ),
};

/**
 * Board with WIP limit exceeded
 */
export const WipLimitExceeded: Story = {
  render: () => {
    const manyInProgressItems: Item[] = [
      ...Array.from({ length: 8 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i + 1}`,
        type: 'task' as const,
        priority: 'medium' as const,
        status: 'in-progress' as const,
        phaseId: 'phase-1',
        owners: [],
        artifacts: [],
        progress: Math.floor(Math.random() * 100),
        createdAt: new Date(),
        updatedAt: new Date(),
      })),
    ];

    return (
      <Box className="p-6">
        <Typography as="h5" gutterBottom tone="danger">
          WIP Limit Exceeded (8 items, limit is 5)
        </Typography>
        <KanbanBoard items={manyInProgressItems} showWipLimits />
      </Box>
    );
  },
};

/**
 * Drag disabled mode
 */
export const DragDisabled: Story = {
  args: {
    items: mockItems,
    dragEnabled: false,
  },
  render: (args) => (
    <Box className="p-6">
      <Typography as="h5" gutterBottom>
        Read-Only View (Drag Disabled)
      </Typography>
      <KanbanBoard {...args} />
    </Box>
  ),
};

/**
 * Interactive demo with state management
 */
export const InteractiveDemo: Story = {
  render: () => {
    const [items, setItems] = useState(mockItems);
    const [moveHistory, setMoveHistory] = useState<KanbanItemMoveEvent[]>([]);

    const handleItemMove = (event: KanbanItemMoveEvent) => {
      setMoveHistory((prev) => [...prev, event]);
      setItems((prev) =>
        prev.map((item) =>
          item.id === event.item.id ? { ...item, status: event.targetStatus } : item
        )
      );
    };

    const handleItemClick = (item: Item) => {
      console.log('Item clicked:', item);
      alert(`Clicked: ${item.title}\nStatus: ${item.status}\nProgress: ${item.progress}%`);
    };

    return (
      <Box className="p-6">
        <Typography as="h4" gutterBottom>
          Interactive Kanban Board
        </Typography>
        <Typography as="p" color="text.secondary" className="mb-4">
          Drag items between columns to update their status. Click on items for details.
        </Typography>

        <KanbanBoard
          items={items}
          onItemMove={handleItemMove}
          onItemClick={handleItemClick}
          showWipLimits
        />

        {moveHistory.length > 0 && (
          <Paper className="p-4 mt-6">
            <Typography as="h6" gutterBottom>
              Move History ({moveHistory.length})
            </Typography>
            <Box className="overflow-y-auto max-h-[200px]">
              {moveHistory.reverse().map((move, idx) => (
                <Typography key={idx} as="p" className="text-sm" className="py-1">
                  • {move.item.title}: {move.sourceStatus} → {move.targetStatus}
                </Typography>
              ))}
            </Box>
          </Paper>
        )}
      </Box>
    );
  },
};
