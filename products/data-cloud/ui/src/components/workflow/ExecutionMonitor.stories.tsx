import type { Meta, StoryObj } from '@storybook/react';
import { Provider, createStore } from 'jotai';
import { ExecutionMonitor } from './ExecutionMonitor';
import { executionAtom, executionStatusAtom } from '@/stores/workflow.store';
import type { WorkflowExecution } from '@/types/workflow.types';

/**
 * Storybook stories for ExecutionMonitor component.
 *
 * <p><b>Purpose</b><br>
 * Demonstrates ExecutionMonitor usage with various execution states.
 * Shows execution status, node progress, error highlighting, and real-time updates.
 *
 * <p><b>Stories</b><br>
 * - Idle State: No execution running
 * - Running: Execution in progress
 * - Completed: Successful execution
 * - Failed: Execution with errors
 * - Partial Progress: Execution with some nodes completed
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and examples
 * @doc.layer frontend
 */

const meta = {
  title: 'Workflow/ExecutionMonitor',
  component: ExecutionMonitor,
  parameters: {
    layout: 'fullscreen',
  },
  decorators: [
    (Story) => {
      const store = createStore();
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '400px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
} satisfies Meta<typeof ExecutionMonitor>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Idle state with no execution.
 */
export const IdleState: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      store.set(executionAtom, null);
      store.set(executionStatusAtom, 'idle');
      
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '400px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
  args: { executionId: 'exec-id-idle' },
};

/**
 * Execution in progress.
 */
export const Running: Story = {
  args: { executionId: 'exec-1' },
  decorators: [
    (Story) => {
      const store = createStore();
      const execution: WorkflowExecution = {
        id: 'exec-1',
        workflowId: 'workflow-1',
        tenantId: 'tenant-1',
        status: 'RUNNING',
        startedAt: new Date(Date.now() - 5000).toISOString(),
        nodeExecutions: [
          {
            nodeId: 'node-1',
            nodeName: 'Start',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 5000).toISOString(),
            completedAt: new Date(Date.now() - 4000).toISOString(),
            duration: 1000,
          },
          {
            nodeId: 'node-2',
            nodeName: 'API Call',
            state: 'RUNNING',
            startedAt: new Date(Date.now() - 3000).toISOString(),
          },
          {
            nodeId: 'node-3',
            nodeName: 'Decision',
            state: 'PENDING',
          },
        ],
        progress: 45,
        nodeStatuses: [
          {
            nodeId: 'node-1',
            nodeName: 'Start',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 5000).toISOString(),
            completedAt: new Date(Date.now() - 4000).toISOString(),
            duration: 1000,
          },
          {
            nodeId: 'node-2',
            nodeName: 'API Call',
            state: 'RUNNING',
            startedAt: new Date(Date.now() - 3000).toISOString(),
          },
          {
            nodeId: 'node-3',
            nodeName: 'Decision',
            state: 'PENDING',
          },
        ],
      };

      store.set(executionAtom, execution);
      store.set(executionStatusAtom, 'running');

      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '500px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Successful execution completed.
 */
export const Completed: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      const execution: WorkflowExecution = {
        id: 'exec-2',
        workflowId: 'workflow-1',
        tenantId: 'tenant-1',
        status: 'COMPLETED',
        startedAt: new Date(Date.now() - 15000).toISOString(),
        completedAt: new Date(Date.now() - 5000).toISOString(),
        nodeExecutions: [
          {
            nodeId: 'node-1',
            nodeName: 'Fetch Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 15000).toISOString(),
            completedAt: new Date(Date.now() - 14000).toISOString(),
            output: { data: 'fetched' },
          },
          {
            nodeId: 'node-2',
            nodeName: 'Process Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 14000).toISOString(),
            completedAt: new Date(Date.now() - 10000).toISOString(),
            output: { processed: true },
          },
          {
            nodeId: 'node-3',
            nodeName: 'Generate Report',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 10000).toISOString(),
            completedAt: new Date(Date.now() - 5000).toISOString(),
            output: { result: 'success' },
          },
        ],
        progress: 100,
        nodeStatuses: [
          {
            nodeId: 'node-1',
            nodeName: 'Fetch Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 15000).toISOString(),
            completedAt: new Date(Date.now() - 14000).toISOString(),
            output: { data: 'fetched' },
          },
          {
            nodeId: 'node-2',
            nodeName: 'Process Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 14000).toISOString(),
            completedAt: new Date(Date.now() - 10000).toISOString(),
            output: { processed: true },
          },
          {
            nodeId: 'node-3',
            nodeName: 'Generate Report',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 10000).toISOString(),
            completedAt: new Date(Date.now() - 5000).toISOString(),
            output: { result: 'success' },
          },
        ],
      };

      store.set(executionAtom, execution);
      store.set(executionStatusAtom, 'completed');

      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '400px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
  args: { executionId: 'exec-2' },
};

/**
 * Failed execution with errors.
 */
export const Failed: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      const execution: WorkflowExecution = {
        id: 'exec-3',
        workflowId: 'workflow-1',
        tenantId: 'tenant-1',
        status: 'FAILED',
        startedAt: new Date(Date.now() - 10000).toISOString(),
        completedAt: new Date(Date.now() - 2000).toISOString(),
        nodeExecutions: [
          {
            nodeId: 'node-1',
            nodeName: 'Start',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 10000).toISOString(),
            completedAt: new Date(Date.now() - 9000).toISOString(),
            duration: 1000,
          },
          {
            nodeId: 'node-2',
            nodeName: 'API Call',
            state: 'FAILED',
            startedAt: new Date(Date.now() - 9000).toISOString(),
            completedAt: new Date(Date.now() - 2000).toISOString(),
            duration: 7000,
            error: 'API request failed: Connection timeout',
          },
          {
            nodeId: 'node-3',
            nodeName: 'Decision',
            state: 'SKIPPED',
          },
        ],
        error: 'Workflow execution failed at node-2',
        progress: 60,
        nodeStatuses: [
          {
            nodeId: 'node-1',
            nodeName: 'Start',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 10000).toISOString(),
            completedAt: new Date(Date.now() - 9000).toISOString(),
            duration: 1000,
          },
          {
            nodeId: 'node-2',
            nodeName: 'API Call',
            state: 'FAILED',
            startedAt: new Date(Date.now() - 9000).toISOString(),
            completedAt: new Date(Date.now() - 2000).toISOString(),
            duration: 7000,
            error: 'API request failed: Connection timeout',
          },
          {
            nodeId: 'node-3',
            nodeName: 'Decision',
            state: 'SKIPPED',
          },
        ],
      };

      store.set(executionAtom, execution);
      store.set(executionStatusAtom, 'failed');
      
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '400px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
  args: { executionId: 'exec-3' },
};

/**
 * Partial progress with some nodes completed.
 */
export const PartialProgress: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      const execution: WorkflowExecution = {
        id: 'exec-4',
        workflowId: 'workflow-1',
        tenantId: 'tenant-1',
        status: 'RUNNING',
        startedAt: new Date(Date.now() - 20000).toISOString(),
        nodeExecutions: [
          {
            nodeId: 'node-1',
            nodeName: 'Fetch Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 20000).toISOString(),
            completedAt: new Date(Date.now() - 19000).toISOString(),
            output: { data: 'fetched' },
          },
          {
            nodeId: 'node-2',
            nodeName: 'Process Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 19000).toISOString(),
            completedAt: new Date(Date.now() - 15000).toISOString(),
            output: { processed: true },
          },
          {
            nodeId: 'node-3',
            nodeName: 'Analyze',
            state: 'RUNNING',
            startedAt: new Date(Date.now() - 15000).toISOString(),
          },
          {
            nodeId: 'node-4',
            nodeName: 'Generate Report',
            state: 'PENDING',
          },
          {
            nodeId: 'node-5',
            nodeName: 'Send Notification',
            state: 'PENDING',
          },
        ],
        progress: 55,
        nodeStatuses: [
          {
            nodeId: 'node-1',
            nodeName: 'Fetch Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 20000).toISOString(),
            completedAt: new Date(Date.now() - 19000).toISOString(),
            output: { data: 'fetched' },
          },
          {
            nodeId: 'node-2',
            nodeName: 'Process Data',
            state: 'COMPLETED',
            startedAt: new Date(Date.now() - 19000).toISOString(),
            completedAt: new Date(Date.now() - 15000).toISOString(),
            output: { processed: true },
          },
          {
            nodeId: 'node-3',
            nodeName: 'Analyze',
            state: 'RUNNING',
            startedAt: new Date(Date.now() - 15000).toISOString(),
          },
          {
            nodeId: 'node-4',
            nodeName: 'Generate Report',
            state: 'PENDING',
          },
          {
            nodeId: 'node-5',
            nodeName: 'Send Notification',
            state: 'PENDING',
          },
        ],
      };

      store.set(executionAtom, execution);
      store.set(executionStatusAtom, 'running');
      
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '400px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
  args: { executionId: 'exec-4' },
};
