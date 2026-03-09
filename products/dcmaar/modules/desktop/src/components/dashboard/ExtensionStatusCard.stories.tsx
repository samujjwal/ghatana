import type { Meta, StoryObj } from '@storybook/react';
import ExtensionStatusCard from './ExtensionStatusCard';
import { extensionStatus } from '../../mocks/mockData';

const meta: Meta<typeof ExtensionStatusCard> = {
  title: 'Dashboard/ExtensionStatusCard',
  component: ExtensionStatusCard,
};

export default meta;

type Story = StoryObj<typeof ExtensionStatusCard>;

export const Default: Story = {
  args: {
    connected: extensionStatus.connected,
    version: extensionStatus.version,
    latencyMs: extensionStatus.latencyMs,
    events: extensionStatus.events,
  },
};
