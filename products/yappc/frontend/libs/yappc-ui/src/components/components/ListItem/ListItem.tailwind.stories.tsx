import { ListItem } from './ListItem.tailwind';
import { List } from '../List';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof ListItem> = {
  title: 'Components/Layout/ListItem',
  component: ListItem,
  tags: ['autodocs'],
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'ListItem is an individual list item component. Supports icons, secondary text, clickable states, and selection.',
      },
    },
  },
  argTypes: {
    icon: {
      description: 'Optional icon element',
    },
    secondaryText: {
      description: 'Secondary text below main content',
    },
    clickable: {
      control: 'boolean',
      description: 'Enable hover effects',
    },
    selected: {
      control: 'boolean',
      description: 'Selected state',
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
    },
    dense: {
      control: 'boolean',
      description: 'Compact padding',
    },
    align: {
      control: 'select',
      options: ['start', 'center'],
      description: 'Content alignment',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof ListItem>;

/**
 * Default ListItem with simple text
 */
export const Default: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List disablePadding>
        <ListItem>Simple list item</ListItem>
      </List>
    </div>
  ),
};

/**
 * ListItem with icons
 */
export const WithIcon: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
            </svg>
          }
        >
          Home
        </ListItem>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
          }
        >
          Profile
        </ListItem>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          }
        >
          Settings
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * ListItem with secondary text
 */
export const WithSecondaryText: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem secondaryText="View and edit your personal information">My Profile</ListItem>
        <ListItem secondaryText="Manage your account preferences">Account Settings</ListItem>
        <ListItem secondaryText="Control your privacy and security">Privacy & Security</ListItem>
      </List>
    </div>
  ),
};

/**
 * ListItem with both icon and secondary text
 */
export const WithIconAndSecondary: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          }
          secondaryText="Overview and key metrics"
        >
          Dashboard
        </ListItem>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
            </svg>
          }
          secondaryText="Manage your projects"
        >
          Projects
        </ListItem>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          }
          secondaryText="Team members and roles"
        >
          Team
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * Clickable ListItems with hover effects
 */
export const Clickable: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem clickable onClick={() => alert('Item 1 clicked')}>
          Clickable Item 1
        </ListItem>
        <ListItem clickable onClick={() => alert('Item 2 clicked')}>
          Clickable Item 2
        </ListItem>
        <ListItem clickable onClick={() => alert('Item 3 clicked')}>
          Clickable Item 3
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * Selected state
 */
export const Selected: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem clickable>Normal Item</ListItem>
        <ListItem clickable selected>
          Selected Item
        </ListItem>
        <ListItem clickable>Normal Item</ListItem>
      </List>
    </div>
  ),
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem clickable>Available Item</ListItem>
        <ListItem disabled>Disabled Item</ListItem>
        <ListItem clickable>Available Item</ListItem>
      </List>
    </div>
  ),
};

/**
 * Dense (compact) ListItems
 */
export const Dense: Story = {
  render: () => (
    <div className="space-y-6">
      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Normal padding</h3>
        <div className="border border-grey-300 rounded-lg">
          <List disablePadding>
            <ListItem icon={<div className="w-6 h-6 bg-primary-500 rounded" />}>Normal Item 1</ListItem>
            <ListItem icon={<div className="w-6 h-6 bg-primary-500 rounded" />}>Normal Item 2</ListItem>
            <ListItem icon={<div className="w-6 h-6 bg-primary-500 rounded" />}>Normal Item 3</ListItem>
          </List>
        </div>
      </div>

      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Dense padding</h3>
        <div className="border border-grey-300 rounded-lg">
          <List dense disablePadding>
            <ListItem dense icon={<div className="w-5 h-5 bg-primary-500 rounded" />}>
              Dense Item 1
            </ListItem>
            <ListItem dense icon={<div className="w-5 h-5 bg-primary-500 rounded" />}>
              Dense Item 2
            </ListItem>
            <ListItem dense icon={<div className="w-5 h-5 bg-primary-500 rounded" />}>
              Dense Item 3
            </ListItem>
          </List>
        </div>
      </div>
    </div>
  ),
};

/**
 * Content alignment options
 */
export const Alignment: Story = {
  render: () => (
    <div className="space-y-6">
      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Start alignment (default)</h3>
        <div className="border border-grey-300 rounded-lg">
          <List disablePadding>
            <ListItem
              align="start"
              icon={
                <svg className="w-full h-full text-primary-500" fill="currentColor" viewBox="0 0 20 20">
                  <circle cx="10" cy="10" r="8" />
                </svg>
              }
              secondaryText="This is a multi-line secondary text that demonstrates start alignment"
            >
              Multi-line Item
            </ListItem>
          </List>
        </div>
      </div>

      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Center alignment</h3>
        <div className="border border-grey-300 rounded-lg">
          <List disablePadding>
            <ListItem
              align="center"
              icon={
                <svg className="w-full h-full text-primary-500" fill="currentColor" viewBox="0 0 20 20">
                  <circle cx="10" cy="10" r="8" />
                </svg>
              }
            >
              Centered Item
            </ListItem>
          </List>
        </div>
      </div>
    </div>
  ),
};

/**
 * ListItem with avatars
 */
export const WithAvatars: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem
          clickable
          icon={
            <div className="w-10 h-10 rounded-full bg-primary-500 flex items-center justify-center text-white font-medium">
              JD
            </div>
          }
          secondaryText="john.doe@example.com"
        >
          John Doe
        </ListItem>
        <ListItem
          clickable
          icon={
            <div className="w-10 h-10 rounded-full bg-secondary-500 flex items-center justify-center text-white font-medium">
              JS
            </div>
          }
          secondaryText="jane.smith@example.com"
        >
          Jane Smith
        </ListItem>
        <ListItem
          clickable
          icon={
            <div className="w-10 h-10 rounded-full bg-success-500 flex items-center justify-center text-white font-medium">
              BJ
            </div>
          }
          secondaryText="bob.johnson@example.com"
        >
          Bob Johnson
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * ListItem with actions (buttons)
 */
export const WithActions: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem secondaryText="john.doe@example.com">
          <div className="flex items-center justify-between w-full">
            <div className="flex-1">
              <div className="font-medium">John Doe</div>
              <div className="text-sm text-grey-600">john.doe@example.com</div>
            </div>
            <div className="flex gap-2">
              <button className="px-3 py-1 text-sm text-primary-500 hover:bg-primary-50 rounded">Edit</button>
              <button className="px-3 py-1 text-sm text-error-500 hover:bg-error-50 rounded">Delete</button>
            </div>
          </div>
        </ListItem>
        <ListItem secondaryText="jane.smith@example.com">
          <div className="flex items-center justify-between w-full">
            <div className="flex-1">
              <div className="font-medium">Jane Smith</div>
              <div className="text-sm text-grey-600">jane.smith@example.com</div>
            </div>
            <div className="flex gap-2">
              <button className="px-3 py-1 text-sm text-primary-500 hover:bg-primary-50 rounded">Edit</button>
              <button className="px-3 py-1 text-sm text-error-500 hover:bg-error-50 rounded">Delete</button>
            </div>
          </div>
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * ListItem with badges/status
 */
export const WithBadges: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem clickable>
          <div className="flex items-center justify-between w-full">
            <span>Messages</span>
            <span className="px-2 py-1 text-xs font-medium bg-primary-500 text-white rounded-full">5</span>
          </div>
        </ListItem>
        <ListItem clickable>
          <div className="flex items-center justify-between w-full">
            <span>Notifications</span>
            <span className="px-2 py-1 text-xs font-medium bg-error-500 text-white rounded-full">12</span>
          </div>
        </ListItem>
        <ListItem clickable>
          <div className="flex items-center justify-between w-full">
            <span>Updates</span>
            <span className="px-2 py-1 text-xs font-medium bg-success-500 text-white rounded-full">3</span>
          </div>
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * Complex custom content
 */
export const ComplexContent: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem clickable>
          <div className="flex gap-4 w-full">
            <img
              src="https://via.placeholder.com/80"
              alt="Product"
              className="w-20 h-20 object-cover rounded"
            />
            <div className="flex-1">
              <h3 className="font-medium">Product Title</h3>
              <p className="text-sm text-grey-600 mt-1">
                Short description of the product goes here
              </p>
              <div className="flex items-center gap-4 mt-2">
                <span className="text-lg font-bold text-primary-500">$49.99</span>
                <span className="text-sm text-grey-500 line-through">$69.99</span>
                <span className="px-2 py-1 text-xs font-medium bg-success-100 text-success-700 rounded">
                  30% OFF
                </span>
              </div>
            </div>
          </div>
        </ListItem>
      </List>
    </div>
  ),
};
