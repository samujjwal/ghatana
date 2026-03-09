import { seedScenarios } from '@ghatana/yappc-testing/mocks/seed-canvas';
import { Provider, useSetAtom } from 'jotai';
import React, { useEffect } from 'react';

import { canvasStateAtom } from '@ghatana/canvas';
import CanvasScene from '../../routes/app/project/canvas/CanvasScene';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof CanvasScene> = {
  title: 'Canvas/CanvasScene',
  component: CanvasScene,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'The main canvas scene with React Flow integration, sketch tools, and component palette.',
      },
    },
  },
  decorators: [
    (Story: unknown, context: unknown) => {
      // Map story name to seed scenario
      const scenarioMap: Record<string, string | null> = {
        Empty: null,
        SmallDiagram: 'small',
        MediumDiagram: 'medium',
        LargeDiagram: 'large',
        MicroservicesArchitecture: 'microservices',
      };

      const scenario = scenarioMap[context.name];
      const setCanvasState = useSetAtom(canvasStateAtom);
      const hasSetInitialState = React.useRef(false);

      useEffect(() => {
        // Skip initial state setup - let ReactFlow initialize with empty state
        // to avoid triggering infinite update loops. State can be set later if needed.
        if (hasSetInitialState.current) return;
        hasSetInitialState.current = true;

        // Only set state if we have a specific scenario seed; otherwise skip
        // to let ReactFlow manage its own initial state
        if (scenario && seedScenarios[scenario]) {
          try {
            setCanvasState(seedScenarios[scenario]());
          } catch (err) {
            // Silently catch errors to prevent breaking the story
            console.warn('[Story] Failed to seed scenario:', scenario, err);
          }
        }
        // Only depend on scenario - not setCanvasState which changes every render
      }, [scenario]);

      return (
        <Provider>
          <div style={{ width: '100vw', height: '100vh' }}>
            <Story />
          </div>
        </Provider>
      );
    },
  ],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof CanvasScene>;

export const Empty: Story = {
  name: 'Empty Canvas',
  parameters: {
    docs: {
      description: {
        story: 'An empty canvas ready for creating diagrams and sketches.',
      },
    },
  },
  args: {},
};

export const SmallDiagram: Story = {
  name: 'Small Diagram',
  parameters: {
    docs: {
      description: {
        story: 'A small diagram with 5 nodes and connections.',
      },
    },
  },
  args: {},
};

export const MediumDiagram: Story = {
  name: 'Medium Diagram',
  parameters: {
    docs: {
      description: {
        story: 'A medium-sized diagram with 15 nodes, shapes, and strokes.',
      },
    },
  },
  args: {},
};

export const LargeDiagram: Story = {
  name: 'Large Diagram',
  parameters: {
    docs: {
      description: {
        story: 'A large diagram with 50 nodes for testing performance.',
      },
    },
  },
  args: {},
};

export const MicroservicesArchitecture: Story = {
  name: 'Microservices Architecture',
  parameters: {
    docs: {
      description: {
        story:
          'A sample microservices architecture diagram with realistic service names.',
      },
    },
  },
  args: {},
};
