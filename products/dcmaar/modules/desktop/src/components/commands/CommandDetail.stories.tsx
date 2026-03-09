import type { Meta, StoryObj } from '@storybook/react';
import CommandDetail from './CommandDetail';
import { commandCatalogue } from '../../mocks/mockData';

const command = commandCatalogue[0];

const meta: Meta<typeof CommandDetail> = {
  title: 'Commands/CommandDetail',
  component: CommandDetail,
};

export default meta;

type Story = StoryObj<typeof CommandDetail>;

export const Default: Story = {
  args: {
    name: command.name,
    description: command.description,
    riskLevel: command.riskLevel,
    requiresApproval: command.requiresApproval,
    estimatedDurationMinutes: command.estimatedDurationMinutes,
    parameters: command.parameters,
  },
};
