import { StorybookProvider, storybookWorkspacesAtom } from '@ghatana/yappc-ui';
import { useAtom } from 'jotai';
import React from 'react';
import { action } from 'storybook/actions';

import { WorkspaceCard } from '../WorkspaceCard';

import type { Meta, StoryObj } from '@storybook/react-vite';

const meta: Meta<typeof WorkspaceCard> = {
  title: 'UI/Components/WorkspaceCard',
  component: WorkspaceCard,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  decorators: [
    (Story) => (
      <StorybookProvider>
        <div style={{ width: '350px' }}>
          <Story />
        </div>
      </StorybookProvider>
    ),
  ],
  argTypes: {
    favorite: {
      control: 'boolean',
      description: 'Whether the workspace is marked as favorite',
      defaultValue: false,
    },
    onFavoriteToggle: { action: 'favoriteToggled' },
    onOpen: { action: 'opened' },
    onMoreOptions: { action: 'moreOptionsClicked' },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof WorkspaceCard>;

// Basic story with static props
export const Basic: Story = {
  args: {
    id: 'workspace-1',
    name: 'Demo Workspace',
    description: 'A demo workspace for Storybook',
    lastModified: new Date().toISOString(),
    favorite: false,
    onFavoriteToggle: action('favoriteToggled'),
    onOpen: action('opened'),
    onMoreOptions: action('moreOptionsClicked'),
  },
};

export const Favorite: Story = {
  args: {
    ...Basic.args,
    favorite: true,
  },
};

export const NoDescription: Story = {
  args: {
    ...Basic.args,
    description: undefined,
  },
};

export const LongName: Story = {
  args: {
    ...Basic.args,
    name: 'This is a very long workspace name that should wrap to multiple lines',
  },
};

export const LongDescription: Story = {
  args: {
    ...Basic.args,
    description: 'This is a very long description that should wrap to multiple lines. It contains a lot of text to demonstrate how the card handles overflow.',
  },
};

export const OldWorkspace: Story = {
  args: {
    ...Basic.args,
    lastModified: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(), // 30 days ago
  },
};

// Story that uses the Storybook store
const WorkspaceCardWithStore = () => {
  const [workspaces] = useAtom<unknown[]>(storybookWorkspacesAtom as unknown);
  const workspace = workspaces[0]; // Use the first workspace from the store

  return (
    <WorkspaceCard
      id={workspace.id}
      name={workspace.name}
      description={workspace.description}
      lastModified={workspace.lastModified}
      favorite={workspace.favorite}
      onFavoriteToggle={action('favoriteToggled')}
      onOpen={action('opened')}
      onMoreOptions={action('moreOptionsClicked')}
    />
  );
};

export const FromStore: Story = {
  render: () => <WorkspaceCardWithStore />,
};

// Story that uses the Storybook store with dark theme
export const DarkTheme: Story = {
  render: () => <WorkspaceCardWithStore />,
  decorators: [
    (Story) => (
      <StorybookProvider theme="dark">
        <div style={{ width: '350px' }}>
          <Story />
        </div>
      </StorybookProvider>
    ),
  ],
};

// Story that shows multiple workspace cards
const MultipleWorkspaceCards = () => {
  const [workspaces] = useAtom<unknown[]>(storybookWorkspacesAtom as unknown);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', width: '350px' }}>
      {workspaces.map((workspace: unknown) => (
        <WorkspaceCard
          key={workspace.id}
          id={workspace.id}
          name={workspace.name}
          description={workspace.description}
          lastModified={workspace.lastModified}
          favorite={workspace.favorite}
          onFavoriteToggle={action('favoriteToggled')}
          onOpen={action('opened')}
          onMoreOptions={action('moreOptionsClicked')}
        />
      ))}
    </div>
  );
};

export const MultipleCards: Story = {
  render: () => <MultipleWorkspaceCards />,
  parameters: {
    layout: 'padded',
  },
};
