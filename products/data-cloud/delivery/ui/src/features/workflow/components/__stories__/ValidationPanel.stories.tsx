/**
 * ValidationPanel component stories.
 *
 * @doc.type storybook
 * @doc.purpose Interactive component documentation
 * @doc.layer frontend
 * @doc.pattern Storybook Stories
 */

import { validationErrorsAtom } from "@/stores/workflow.store";
import type { ValidationError } from "@/types/workflow.types";
import type { Meta, StoryObj } from "@storybook/react";
import { Provider, createStore } from "jotai";
import { ValidationPanel } from "../ValidationPanel";

const meta = {
  title: "Workflow/ValidationPanel",
  component: ValidationPanel,
  decorators: [
    (Story) => {
      const store = createStore();
      // default: no errors
      store.set(validationErrorsAtom, []);
      return (
        <Provider store={store}>
          <div style={{ height: "600px" }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
  parameters: {
    layout: "fullscreen",
  },
} satisfies Meta<typeof ValidationPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Valid workflow (no issues).
 */
export const Valid: Story = {};

/**
 * Workflow with errors.
 */
export const WithErrors: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      const errors: ValidationError[] = [
        {
          code: "MISSING_NAME",
          message: "Workflow must have a name",
          suggestion: "Provide a descriptive name",
        },
        {
          code: "NO_NODES",
          message: "Workflow must have at least one node",
          suggestion: "Add nodes to the workflow",
        },
      ];
      store.set(
        validationErrorsAtom,
        errors.map((e) => JSON.stringify(e)),
      );
      return (
        <Provider store={store}>
          <div style={{ height: "600px" }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Workflow with warnings.
 */
export const WithWarnings: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      const errors: ValidationError[] = [
        {
          code: "MISSING_NODE_LABEL",
          message: "Node should have a label",
          nodeId: "node-1",
        },
        {
          code: "ORPHANED_NODE",
          message: "Node is not connected to any other node",
          nodeId: "node-5",
        },
      ];
      store.set(
        validationErrorsAtom,
        errors.map((e) => JSON.stringify(e)),
      );
      return (
        <Provider store={store}>
          <div style={{ height: "600px" }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};

/**
 * Read-only validation panel.
 */
export const ReadOnly: Story = {
  decorators: [
    (Story) => {
      const store = createStore();
      const errors: ValidationError[] = [
        {
          code: "MISSING_NAME",
          message: "Workflow must have a name",
          suggestion: "Provide a descriptive name",
        },
      ];
      store.set(
        validationErrorsAtom,
        errors.map((e) => JSON.stringify(e)),
      );
      return (
        <Provider store={store}>
          <div style={{ height: "600px" }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};
