import type { Meta, StoryObj } from '@storybook/react';
import InfoCard from './InfoCard';
import { Typography } from '../../ui/tw-compat';

const meta: Meta<typeof InfoCard> = {
  title: 'Common/InfoCard',
  component: InfoCard,
};

export default meta;

type Story = StoryObj<typeof InfoCard>;

export const Default: Story = {
  args: {
    title: 'Exporter Health',
    subtitle: 'Recent metrics and state',
    children: (
      <Typography variant="body2" color="text.secondary">
        Cards can compose any layout inside. Use this for summaries, tables, charts, or forms.
      </Typography>
    ),
  },
};

export const WithFooter: Story = {
  args: {
    title: 'Queue Utilisation',
    subtitle: 'Shows queue depth and watermarks',
    footer: <Typography variant="caption">Updated just now</Typography>,
    children: <Typography variant="h4">72%</Typography>,
  },
};
