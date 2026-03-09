import type { Meta, StoryObj } from '@storybook/react';
import { Provider, createStore } from 'jotai';
import { ValidationPanel } from './ValidationPanel';
import { validationErrorsAtom } from '@/stores/workflow.store';
import type { ValidationError } from '@/types/workflow.types';

/**
 * Storybook stories for ValidationPanel component.
 *
 * <p><b>Purpose</b><br>
 * Demonstrates ValidationPanel usage with various validation states.
 * Shows error display, warnings, suggestions, and auto-fix functionality.
 *
 * <p><b>Stories</b><br>
 * - No Errors: Valid workflow with no issues
 * - With Errors: Workflow with structural errors
 * - With Warnings: Workflow with warnings only
 * - Mixed Issues: Workflow with both errors and warnings
 * - With Suggestions: Errors with auto-fix suggestions
 *
 * @doc.type storybook
 * @doc.purpose Component documentation and examples
 * @doc.layer frontend
 */

const meta = {
  title: 'Workflow/ValidationPanel',
  component: ValidationPanel,
  parameters: {
    layout: 'fullscreen',
  },
  decorators: [
    (Story) => (
      <Provider>
        <div style={{ width: '100%', height: '500px', display: 'flex' }}>
          <div style={{ flex: 1, borderRight: '1px solid #ccc' }}>Canvas Area</div>
          <div style={{ width: '300px' }}>
            <Story />
          </div>
        </div>
      </Provider>
    ),
  ],
} satisfies Meta<typeof ValidationPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Valid workflow with no errors.
 */
export const NoErrors: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      store.set(validationErrorsAtom, []);
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '500px', display: 'flex' }}>
            <div style={{ flex: 1, borderRight: '1px solid #ccc' }}>Canvas Area</div>
            <div style={{ width: '300px' }}>
              <Story />
            </div>
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Workflow with structural errors.
 */
export const WithErrors: Story = {
  decorators: [
    (Story) => {
      const errors: ValidationError[] = [
        {
          code: 'MISSING_NAME',
          message: 'Workflow must have a name',
          suggestion: 'Provide a descriptive name for the workflow',
        },
        {
          code: 'NO_NODES',
          message: 'Workflow must have at least one node',
          suggestion: 'Add nodes to the workflow canvas',
        },
        {
          code: 'MISSING_API_URL',
          message: 'API Call node must have a URL',
          nodeId: 'node-2',
          suggestion: 'Provide the API endpoint URL',
        },
      ];

      const store = createStore();
      store.set(validationErrorsAtom, errors.map((e) => JSON.stringify(e)));
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '500px', display: 'flex' }}>
            <div style={{ flex: 1, borderRight: '1px solid #ccc' }}>Canvas Area</div>
            <div style={{ width: '300px' }}>
              <Story />
            </div>
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Workflow with warnings only.
 */
export const WithWarnings: Story = {
  decorators: [
    (Story) => {
      const warnings = [
        {
          code: 'MISSING_NODE_LABEL',
          message: 'Node should have a label',
          nodeId: 'node-1',
          suggestion: 'Add a descriptive label to the node',
        },
        {
          code: 'ORPHANED_NODE',
          message: 'Node is not connected to any other node',
          nodeId: 'node-5',
          suggestion: 'Connect this node to the workflow or remove it',
        },
        {
          code: 'NO_TRIGGERS',
          message: 'Workflow has no triggers',
          suggestion: 'Add at least one trigger to enable workflow execution',
        },
      ];

      const store = createStore();
      store.set(validationErrorsAtom, warnings.map((w) => JSON.stringify(w)));
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '500px', display: 'flex' }}>
            <div style={{ flex: 1, borderRight: '1px solid #ccc' }}>Canvas Area</div>
            <div style={{ width: '300px' }}>
              <Story />
            </div>
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Workflow with both errors and warnings.
 */
export const MixedIssues: Story = {
  decorators: [
    (Story) => {
      const issues = [
        {
          code: 'MISSING_NAME',
          message: 'Workflow must have a name',
          suggestion: 'Provide a descriptive name for the workflow',
        },
        {
          code: 'MISSING_NODE_LABEL',
          message: 'Node should have a label',
          nodeId: 'node-1',
          suggestion: 'Add a descriptive label to the node',
        },
        {
          code: 'MISSING_API_URL',
          message: 'API Call node must have a URL',
          nodeId: 'node-2',
          suggestion: 'Provide the API endpoint URL',
        },
        {
          code: 'ORPHANED_NODE',
          message: 'Node is not connected to any other node',
          nodeId: 'node-5',
          suggestion: 'Connect this node to the workflow or remove it',
        },
        {
          code: 'CYCLE_DETECTED',
          message: 'Workflow contains a cycle',
          suggestion: 'Remove edges to break the cycle',
        },
      ];

      const store = createStore();
      store.set(validationErrorsAtom, issues.map((i) => JSON.stringify(i)));
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '500px', display: 'flex' }}>
            <div style={{ flex: 1, borderRight: '1px solid #ccc' }}>Canvas Area</div>
            <div style={{ width: '300px' }}>
              <Story />
            </div>
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Errors with auto-fix suggestions.
 */
export const WithSuggestions: Story = {
  decorators: [
    (Story) => {
      const errors: ValidationError[] = [
        {
          code: 'MISSING_API_METHOD',
          message: 'API Call node must have a method',
          nodeId: 'node-2',
          suggestion: 'Select an HTTP method (GET, POST, etc.)',
        },
        {
          code: 'MISSING_DECISION_CONDITION',
          message: 'Decision node must have a condition',
          nodeId: 'node-3',
          suggestion: 'Define the decision condition',
        },
        {
          code: 'MISSING_APPROVAL_ASSIGNEE',
          message: 'Approval node must have an assignee',
          nodeId: 'node-4',
          suggestion: 'Specify who should approve this step',
        },
      ];

      const store = createStore();
      store.set(validationErrorsAtom, errors.map((e) => JSON.stringify(e)));
      return (
        <Provider store={store}>
          <div style={{ width: '100%', height: '500px', display: 'flex' }}>
            <div style={{ flex: 1, borderRight: '1px solid #ccc' }}>Canvas Area</div>
            <div style={{ width: '300px' }}>
              <Story />
            </div>
          </div>
        </Provider>
      );
    },
  ],
};
