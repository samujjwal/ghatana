/**
 * ExecutionMonitor component stories.
 *
 * @doc.type storybook
 * @doc.purpose Interactive component documentation
 * @doc.layer frontend
 * @doc.pattern Storybook Stories
 */

import type { Meta, StoryObj } from '@storybook/react';
import { Provider } from 'jotai';
import { ExecutionMonitor } from '../ExecutionMonitor';

const meta = {
  title: 'Workflow/ExecutionMonitor',
  component: ExecutionMonitor,
  decorators: [
    (Story) => (
      <Provider>
        <div style={{ height: '600px' }}>
          <Story />
        </div>
      </Provider>
    ),
  ],
  parameters: {
    layout: 'fullscreen',
  },
} satisfies Meta<typeof ExecutionMonitor>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Pending execution.
 */
export const Pending: Story = {
  args: {
    executionId: 'exec-1',
  },
};

/**
 * Running execution.
 */
export const Running: Story = {
  args: {
    executionId: 'exec-2',
  },
};

/**
 * Completed execution.
 */
export const Completed: Story = {
  args: {
    executionId: 'exec-3',
    onComplete: () => console.log('Execution completed'),
  },
};

/**
 * Failed execution.
 */
export const Failed: Story = {
  args: {
    executionId: 'exec-4',
  },
};
