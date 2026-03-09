/**
 * PhaseNav Storybook Stories
 *
 * @module DevSecOps/PhaseNav/stories
 */

import type { Meta, StoryObj } from '@storybook/react';

// Small local noop used in stories when interactive callbacks are required
const fn = () => () => {};


import { PhaseNav } from './PhaseNav';
import { devsecopsTheme } from '../../../theme/devsecops-theme';

import type { Phase } from './types';

const allPhases: Phase[] = [
  { id: '1', title: 'Ideation', key: 'ideation', description: 'Brainstorming and idea generation' },
  { id: '2', title: 'Planning', key: 'planning', description: 'Requirements and architecture' },
  { id: '3', title: 'Development', key: 'development', description: 'Code implementation' },
  { id: '4', title: 'Security', key: 'security', description: 'Security reviews and testing' },
  { id: '5', title: 'Testing', key: 'testing', description: 'QA and validation' },
  { id: '6', title: 'Deployment', key: 'deployment', description: 'Release to production' },
  { id: '7', title: 'Operations', key: 'operations', description: 'Monitoring and maintenance' },
];

const meta: Meta<typeof PhaseNav> = {
  title: 'DevSecOps/PhaseNav',
  component: PhaseNav,
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <ThemeProvider theme={devsecopsTheme}>
        <Story />
      </ThemeProvider>
    ),
  ],
  args: {
    onPhaseClick: fn(),
  },
  parameters: {
    docs: {
      description: {
        component:
          'Horizontal phase navigation with visual indicators for active and completed phases. Each phase has a unique color.',
      },
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof PhaseNav>;

/**
 * Default phase navigation at the start
 */
export const Default: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '1',
  },
};

/**
 * Currently in planning phase
 */
export const PlanningPhase: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '2',
    completedPhaseIds: ['1'],
  },
};

/**
 * Mid-development with completed phases
 */
export const DevelopmentPhase: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '3',
    completedPhaseIds: ['1', '2'],
  },
};

/**
 * Security review phase
 */
export const SecurityPhase: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '4',
    completedPhaseIds: ['1', '2', '3'],
  },
};

/**
 * Testing phase
 */
export const TestingPhase: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '5',
    completedPhaseIds: ['1', '2', '3', '4'],
  },
};

/**
 * Ready for deployment
 */
export const DeploymentPhase: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '6',
    completedPhaseIds: ['1', '2', '3', '4', '5'],
  },
};

/**
 * All phases completed, in operations
 */
export const OperationsPhase: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '7',
    completedPhaseIds: ['1', '2', '3', '4', '5', '6'],
  },
};

/**
 * Minimal phases (3 phases only)
 */
export const MinimalPhases: Story = {
  args: {
    phases: [
      { id: '1', title: 'Plan', key: 'planning' },
      { id: '2', title: 'Build', key: 'development' },
      { id: '3', title: 'Deploy', key: 'deployment' },
    ],
    activePhaseId: '2',
    completedPhaseIds: ['1'],
  },
};

/**
 * No completed phases yet
 */
export const NoCompletedPhases: Story = {
  args: {
    phases: allPhases,
    activePhaseId: '1',
    completedPhaseIds: [],
  },
};
