import type { Meta, StoryObj } from '@storybook/react';
import AgentStatusCard from './AgentStatusCard';
import { agentStatus } from '../../mocks/mockData';

const meta: Meta<typeof AgentStatusCard> = {
  title: 'Dashboard/AgentStatusCard',
  component: AgentStatusCard,
};

export default meta;

type Story = StoryObj<typeof AgentStatusCard>;

export const Default: Story = {
  args: {
    name: agentStatus.name,
    version: agentStatus.version,
    connected: agentStatus.connected,
    uptimeSeconds: agentStatus.uptimeSeconds,
    lastHeartbeat: agentStatus.lastHeartbeat,
    queue: agentStatus.queue,
    exporters: agentStatus.exporters,
  },
};
