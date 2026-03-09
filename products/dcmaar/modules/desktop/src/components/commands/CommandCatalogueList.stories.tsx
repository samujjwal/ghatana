import type { Meta, StoryObj } from '@storybook/react';
import CommandCatalogueList from './CommandCatalogueList';
import { commandCatalogue } from '../../mocks/mockData';

const meta: Meta<typeof CommandCatalogueList> = {
  title: 'Commands/CommandCatalogueList',
  component: CommandCatalogueList,
};

export default meta;

type Story = StoryObj<typeof CommandCatalogueList>;

export const Default: Story = {
  args: {
    commands: commandCatalogue,
    selected: commandCatalogue[0].id,
  },
};
