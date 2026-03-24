import React from 'react';

import { TransferList, type TransferListItem } from './TransferList';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof TransferList> = {
  title: 'Components/TransferList',
  component: TransferList,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof TransferList>;

const sampleItems: TransferListItem[] = [
  { id: 1, label: 'Item 1', description: 'Description for item 1' },
  { id: 2, label: 'Item 2', description: 'Description for item 2' },
  { id: 3, label: 'Item 3', description: 'Description for item 3' },
  { id: 4, label: 'Item 4', description: 'Description for item 4' },
  { id: 5, label: 'Item 5', description: 'Description for item 5' },
];

/**
 * Default transfer list
 */
export const Default: Story = {
  render: () => {
    const [left, setLeft] = React.useState<TransferListItem[]>(sampleItems);
    const [right, setRight] = React.useState<TransferListItem[]>([]);

    return (
      <TransferList
        leftItems={left}
        rightItems={right}
        onChange={(newLeft, newRight) => {
          setLeft(newLeft);
          setRight(newRight);
        }}
      />
    );
  },
};

/**
 * With search functionality
 */
export const WithSearch: Story = {
  render: () => {
    const [left, setLeft] = React.useState<TransferListItem[]>(sampleItems);
    const [right, setRight] = React.useState<TransferListItem[]>([]);

    return (
      <TransferList
        leftItems={left}
        rightItems={right}
        onChange={(newLeft, newRight) => {
          setLeft(newLeft);
          setRight(newRight);
        }}
        searchable
      />
    );
  },
};

/**
 * With custom titles
 */
export const CustomTitles: Story = {
  render: () => {
    const [left, setLeft] = React.useState<TransferListItem[]>(sampleItems);
    const [right, setRight] = React.useState<TransferListItem[]>([]);

    return (
      <TransferList
        leftItems={left}
        rightItems={right}
        leftTitle="Source"
        rightTitle="Destination"
        onChange={(newLeft, newRight) => {
          setLeft(newLeft);
          setRight(newRight);
        }}
      />
    );
  },
};

/**
 * With disabled items
 */
export const WithDisabledItems: Story = {
  render: () => {
    const items = [
      ...sampleItems,
      { id: 6, label: 'Disabled Item', description: 'This item cannot be selected', disabled: true },
    ];

    const [left, setLeft] = React.useState<TransferListItem[]>(items);
    const [right, setRight] = React.useState<TransferListItem[]>([]);

    return (
      <TransferList
        leftItems={left}
        rightItems={right}
        onChange={(newLeft, newRight) => {
          setLeft(newLeft);
          setRight(newRight);
        }}
        searchable
      />
    );
  },
};

/**
 * User permissions example
 */
export const UserPermissions: Story = {
  render: () => {
    const permissions: TransferListItem[] = [
      { id: 'read', label: 'Read', description: 'View content' },
      { id: 'write', label: 'Write', description: 'Create and edit content' },
      { id: 'delete', label: 'Delete', description: 'Remove content' },
      { id: 'admin', label: 'Admin', description: 'Full access' },
      { id: 'share', label: 'Share', description: 'Share with others' },
    ];

    const [available, setAvailable] = React.useState<TransferListItem[]>(permissions);
    const [assigned, setAssigned] = React.useState<TransferListItem[]>([]);

    return (
      <div className="max-w-4xl">
        <h3 className="text-lg font-semibold mb-4">Manage User Permissions</h3>
        <TransferList
          leftItems={available}
          rightItems={assigned}
          leftTitle="Available Permissions"
          rightTitle="Assigned Permissions"
          onChange={(newAvailable, newAssigned) => {
            setAvailable(newAvailable);
            setAssigned(newAssigned);
          }}
          searchable
          height="300px"
        />
      </div>
    );
  },
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  render: () => {
    const [left, setLeft] = React.useState<TransferListItem[]>(sampleItems);
    const [right, setRight] = React.useState<TransferListItem[]>([]);

    return (
      <TransferList
        leftItems={left}
        rightItems={right}
        onChange={(newLeft, newRight) => {
          setLeft(newLeft);
          setRight(newRight);
        }}
        searchable={true}
        disabled={false}
        height="400px"
      />
    );
  },
};
