import type { Meta, StoryObj } from '@storybook/react';
import { Provider, createStore } from 'jotai';
import { WorkflowCanvas } from './WorkflowCanvas';
import { workflowAtom } from '@/stores/workflow.store';
import type { WorkflowDefinition } from '@/types/workflow.types';

/**
 * Storybook stories for WorkflowCanvas component.
 *
 * <p><b>Purpose</b><br>
 * Demonstrates WorkflowCanvas usage with various workflow configurations.
 * Shows canvas controls, node rendering, and interaction patterns.
 *
 * <p><b>Stories</b><br>
 * - Empty Canvas: Canvas with no nodes
 * - Simple Workflow: Basic workflow with 3 nodes
 * - Complex Workflow: Multi-node workflow with various types
 * - Large Workflow: Performance test with many nodes
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and examples
 * @doc.layer frontend
 */

const meta = {
  title: 'Workflow/Canvas',
  component: WorkflowCanvas,
  parameters: {
    layout: 'fullscreen',
  },
  decorators: [
    (Story) => {
      const store = createStore();
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '600px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
} satisfies Meta<typeof WorkflowCanvas>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Empty canvas with no nodes.
 */
export const EmptyCanvas: Story = {
  args: {
    workflowId: 'workflow-empty',
  },
  decorators: [
    (Story) => {
      const store = createStore();
      store.set(workflowAtom, null);
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '600px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Simple workflow with 3 nodes.
 */
export const SimpleWorkflow: Story = {
  args: {
    workflowId: 'workflow-simple',
  },
  decorators: [
    (Story) => {
      const workflow: WorkflowDefinition = {
        id: 'workflow-simple',
        tenantId: 'tenant-1',
        name: 'Simple Workflow',
        collectionId: 'collection-1',
        nodes: [
          {
            id: 'node-1',
            type: 'START',
            label: 'Start',
            data: {},
            config: {},
            metadata: {},
            position: { x: 100, y: 100 },
          },
          {
            id: 'node-2',
            type: 'API_CALL',
            label: 'Fetch Data',
            data: {},
            config: { url: 'https://api.example.com/data', method: 'GET' },
            metadata: {},
            position: { x: 300, y: 100 },
          },
          {
            id: 'node-3',
            type: 'END',
            label: 'End',
            data: {},
            config: {},
            metadata: {},
            position: { x: 500, y: 100 },
          },
        ],
        edges: [
          {
            id: 'edge-1',
            source: 'node-1',
            target: 'node-2',
            type: 'DEFAULT',
          },
          {
            id: 'edge-2',
            source: 'node-2',
            target: 'node-3',
            type: 'DEFAULT',
          },
        ],
        triggers: [],
        variables: {},
        status: 'DRAFT',
        version: 1,
        active: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        updatedBy: 'user-1',
      };

      const store = createStore();
      store.set(workflowAtom, workflow);
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '600px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Complex workflow with multiple node types.
 */
export const ComplexWorkflow: Story = {
  args: {
    workflowId: 'workflow-complex',
  },
  decorators: [
    (Story) => {
      const workflow: WorkflowDefinition = {
        id: 'workflow-complex',
        tenantId: 'tenant-1',
        name: 'Complex Workflow',
        collectionId: 'collection-1',
        nodes: [
          {
            id: 'node-1',
            type: 'START',
            label: 'Start',
            data: {},
            config: {},
            metadata: {},
            position: { x: 100, y: 150 },
          },
          {
            id: 'node-2',
            type: 'API_CALL',
            label: 'Fetch User',
            data: {},
            config: { url: 'https://api.example.com/users', method: 'GET' },
            metadata: {},
            position: { x: 300, y: 100 },
          },
          {
            id: 'node-3',
            type: 'DECISION',
            label: 'Check Status',
            data: {},
            config: { condition: 'status === active' },
            metadata: {},
            position: { x: 500, y: 150 },
          },
          {
            id: 'node-4',
            type: 'APPROVAL',
            label: 'Manager Approval',
            data: {},
            config: { assignee: 'manager@example.com' },
            metadata: {},
            position: { x: 700, y: 50 },
          },
          {
            id: 'node-5',
            type: 'TRANSFORM',
            label: 'Transform Data',
            data: {},
            config: { mapping: { name: 'fullName', email: 'contactEmail' } },
            metadata: {},
            position: { x: 700, y: 250 },
          },
          {
            id: 'node-6',
            type: 'END',
            label: 'End',
            data: {},
            config: {},
            metadata: {},
            position: { x: 900, y: 150 },
          },
        ],
        edges: [
          {
            id: 'edge-1',
            source: 'node-1',
            target: 'node-2',
            type: 'DEFAULT',
          },
          {
            id: 'edge-2',
            source: 'node-2',
            target: 'node-3',
            type: 'DEFAULT',
          },
          {
            id: 'edge-3',
            source: 'node-3',
            target: 'node-4',
            type: 'CONDITIONAL',
            condition: 'true',
          },
          {
            id: 'edge-4',
            source: 'node-3',
            target: 'node-5',
            type: 'CONDITIONAL',
            condition: 'false',
          },
          {
            id: 'edge-5',
            source: 'node-4',
            target: 'node-6',
            type: 'DEFAULT',
          },
          {
            id: 'edge-6',
            source: 'node-5',
            target: 'node-6',
            type: 'DEFAULT',
          },
        ],
        triggers: [],
        variables: {},
        status: 'DRAFT',
        version: 1,
        active: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        updatedBy: 'user-1',
      };

      const store = createStore();
      store.set(workflowAtom, workflow);
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '600px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Large workflow for performance testing.
 */
export const LargeWorkflow: Story = {
  args: {
    workflowId: 'workflow-large',
  },
  decorators: [
    (Story) => {
      // Generate a large workflow with many nodes
      const nodes = [];
      const edges = [];

      for (let i = 0; i < 20; i++) {
        const nodeTypes = ['API_CALL', 'DECISION', 'TRANSFORM', 'APPROVAL'];
        const nodeType = nodeTypes[i % nodeTypes.length];

        nodes.push({
          id: `node-${i}`,
          type: i === 0 ? 'START' : i === 19 ? 'END' : nodeType,
          label: `Step ${i}`,
          data: {},
          config: {},
          metadata: {},
          position: {
            x: (i % 5) * 200,
            y: Math.floor(i / 5) * 150,
          },
        });

        if (i > 0) {
          edges.push({
            id: `edge-${i}`,
            source: `node-${i - 1}`,
            target: `node-${i}`,
            type: 'DEFAULT',
          });
         }
       }

      const workflow: WorkflowDefinition = {
        id: 'workflow-large',
        tenantId: 'tenant-1',
        name: 'Large Workflow',
        collectionId: 'collection-1',
        nodes: nodes as any,
        edges: edges as any,
        triggers: [],
        variables: {},
        status: 'DRAFT',
        version: 1,
        active: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        createdBy: 'user-1',
        updatedBy: 'user-1',
      };

      const store = createStore();
      store.set(workflowAtom, workflow);
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '600px' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};
