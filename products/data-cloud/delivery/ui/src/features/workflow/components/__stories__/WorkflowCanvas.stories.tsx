/**
 * WorkflowCanvas component stories.
 *
 * @doc.type storybook
 * @doc.purpose Interactive component documentation
 * @doc.layer frontend
 * @doc.pattern Storybook Stories
 */

import type { Meta, StoryObj } from '@storybook/react';
import { Provider } from 'jotai';
import { WorkflowCanvas } from '../WorkflowCanvas';
import type { WorkflowNode } from '../../types/workflow.types';

const meta = {
  title: 'Workflow/Canvas',
  component: WorkflowCanvas,
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
} satisfies Meta<typeof WorkflowCanvas>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Empty canvas.
 */
export const Empty: Story = {
  args: {
    readOnly: false,
  },
};

/**
 * Canvas with sample workflow.
 */
export const WithWorkflow: Story = {
  args: {
    readOnly: false,
  },
};

/**
 * Read-only canvas.
 */
export const ReadOnly: Story = {
  args: {
    readOnly: true,
  },
};

/**
 * Canvas with node selection.
 */
export const WithSelection: Story = {
  args: {
    readOnly: false,
    onNodeSelect: (nodeId) => console.log('Selected node:', nodeId),
  },
};
