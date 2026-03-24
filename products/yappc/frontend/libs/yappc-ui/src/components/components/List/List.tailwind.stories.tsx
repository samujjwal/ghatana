import { List } from './List.tailwind';
import { ListItem } from '../ListItem';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof List> = {
  title: 'Components/Layout/List',
  component: List,
  tags: ['autodocs'],
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'List is a semantic list component (ul/ol) with styling options. Works with ListItem for consistent item styling.',
      },
    },
  },
  argTypes: {
    variant: {
      control: 'select',
      options: ['bullet', 'numbered', 'none'],
      description: 'List style variant',
    },
    dividers: {
      control: 'boolean',
      description: 'Show dividers between items',
    },
    dense: {
      control: 'boolean',
      description: 'Compact spacing',
    },
    padding: {
      control: 'select',
      options: ['none', 'sm', 'md', 'lg'],
      description: 'Padding around list',
    },
    disablePadding: {
      control: 'boolean',
      description: 'Remove padding (for bordered containers)',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof List>;

/**
 * Default List with no markers
 */
export const Default: Story = {
  args: {
    children: (
      <>
        <ListItem>First item</ListItem>
        <ListItem>Second item</ListItem>
        <ListItem>Third item</ListItem>
      </>
    ),
  },
};

/**
 * Different list style variants
 */
export const Variants: Story = {
  render: () => (
    <div className="space-y-6">
      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">None (default) - No markers</h3>
        <List variant="none">
          <ListItem>Home</ListItem>
          <ListItem>Profile</ListItem>
          <ListItem>Settings</ListItem>
        </List>
      </div>

      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Bullet - Disc markers</h3>
        <List variant="bullet">
          <ListItem>Apples</ListItem>
          <ListItem>Bananas</ListItem>
          <ListItem>Oranges</ListItem>
        </List>
      </div>

      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Numbered - Decimal markers</h3>
        <List variant="numbered">
          <ListItem>First step</ListItem>
          <ListItem>Second step</ListItem>
          <ListItem>Third step</ListItem>
        </List>
      </div>
    </div>
  ),
};

/**
 * List with dividers between items
 */
export const WithDividers: Story = {
  render: () => (
    <div className="space-y-6">
      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Without dividers</h3>
        <div className="border border-grey-300 rounded-lg">
          <List disablePadding>
            <ListItem>Item 1</ListItem>
            <ListItem>Item 2</ListItem>
            <ListItem>Item 3</ListItem>
          </List>
        </div>
      </div>

      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">With dividers</h3>
        <div className="border border-grey-300 rounded-lg">
          <List dividers disablePadding>
            <ListItem>Item 1</ListItem>
            <ListItem>Item 2</ListItem>
            <ListItem>Item 3</ListItem>
          </List>
        </div>
      </div>
    </div>
  ),
};

/**
 * Dense (compact) spacing
 */
export const DenseSpacing: Story = {
  render: () => (
    <div className="space-y-6">
      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Normal spacing</h3>
        <div className="border border-grey-300 rounded-lg">
          <List disablePadding>
            <ListItem>Item 1</ListItem>
            <ListItem>Item 2</ListItem>
            <ListItem>Item 3</ListItem>
            <ListItem>Item 4</ListItem>
          </List>
        </div>
      </div>

      <div>
        <h3 className="text-sm font-medium text-grey-700 mb-2">Dense spacing</h3>
        <div className="border border-grey-300 rounded-lg">
          <List dense disablePadding>
            <ListItem dense>Item 1</ListItem>
            <ListItem dense>Item 2</ListItem>
            <ListItem dense>Item 3</ListItem>
            <ListItem dense>Item 4</ListItem>
          </List>
        </div>
      </div>
    </div>
  ),
};

/**
 * Different padding options
 */
export const PaddingOptions: Story = {
  render: () => (
    <div className="space-y-6">
      {(['none', 'sm', 'md', 'lg'] as const).map((padding) => (
        <div key={padding}>
          <h3 className="text-sm font-medium text-grey-700 mb-2">Padding: {padding}</h3>
          <div className="border border-grey-300 rounded-lg">
            <List padding={padding}>
              <ListItem>Item 1</ListItem>
              <ListItem>Item 2</ListItem>
              <ListItem>Item 3</ListItem>
            </List>
          </div>
        </div>
      ))}
    </div>
  ),
};

/**
 * List with icons
 */
export const WithIcons: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
              />
            </svg>
          }
        >
          Home
        </ListItem>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
              />
            </svg>
          }
        >
          Profile
        </ListItem>
        <ListItem
          icon={
            <svg className="w-full h-full text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
              />
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
 * List with secondary text
 */
export const WithSecondaryText: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem secondaryText="View and edit your personal information">My Profile</ListItem>
        <ListItem secondaryText="Manage your account preferences">Account Settings</ListItem>
        <ListItem secondaryText="Control your privacy and security">Privacy & Security</ListItem>
        <ListItem secondaryText="Configure notification preferences">Notifications</ListItem>
      </List>
    </div>
  ),
};

/**
 * Clickable list items with hover effects
 */
export const Clickable: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem clickable onClick={() => alert('Home clicked')}>
          Home
        </ListItem>
        <ListItem clickable onClick={() => alert('Profile clicked')}>
          Profile
        </ListItem>
        <ListItem clickable onClick={() => alert('Settings clicked')}>
          Settings
        </ListItem>
        <ListItem clickable onClick={() => alert('Logout clicked')}>
          Logout
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * List with selected state
 */
export const WithSelection: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem
          clickable
          icon={
            <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
            </svg>
          }
        >
          Home
        </ListItem>
        <ListItem
          clickable
          selected
          icon={
            <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
          }
        >
          Profile (Selected)
        </ListItem>
        <ListItem
          clickable
          icon={
            <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
 * List with disabled items
 */
export const WithDisabled: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem clickable>Available Item</ListItem>
        <ListItem disabled>Disabled Item</ListItem>
        <ListItem clickable>Available Item</ListItem>
        <ListItem disabled secondaryText="This feature is not available">
          Premium Feature
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * Navigation list example
 */
export const NavigationList: Story = {
  render: () => (
    <div className="w-64 border border-grey-300 rounded-lg">
      <List disablePadding>
        <ListItem
          clickable
          selected
          icon={
            <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          }
          secondaryText="Overview and stats"
        >
          Dashboard
        </ListItem>
        <ListItem
          clickable
          icon={
            <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
            </svg>
          }
          secondaryText="Manage your projects"
        >
          Projects
        </ListItem>
        <ListItem
          clickable
          icon={
            <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          }
          secondaryText="Team members"
        >
          Team
        </ListItem>
        <ListItem
          clickable
          icon={
            <svg className="w-full h-full" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          }
          secondaryText="Account preferences"
        >
          Settings
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * Contact list with avatars
 */
export const ContactList: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List dividers disablePadding>
        <ListItem
          clickable
          icon={<div className="w-10 h-10 rounded-full bg-primary-500 flex items-center justify-center text-white font-medium">JD</div>}
          secondaryText="john.doe@example.com"
        >
          John Doe
        </ListItem>
        <ListItem
          clickable
          icon={<div className="w-10 h-10 rounded-full bg-secondary-500 flex items-center justify-center text-white font-medium">JS</div>}
          secondaryText="jane.smith@example.com"
        >
          Jane Smith
        </ListItem>
        <ListItem
          clickable
          icon={<div className="w-10 h-10 rounded-full bg-success-500 flex items-center justify-center text-white font-medium">BJ</div>}
          secondaryText="bob.johnson@example.com"
        >
          Bob Johnson
        </ListItem>
      </List>
    </div>
  ),
};

/**
 * Nested lists
 */
export const NestedList: Story = {
  render: () => (
    <div className="border border-grey-300 rounded-lg">
      <List variant="none" disablePadding>
        <ListItem>
          <div className="w-full">
            <div className="font-medium">Fruits</div>
            <List variant="bullet" className="mt-2 ml-4">
              <li>Apples</li>
              <li>Bananas</li>
              <li>Oranges</li>
            </List>
          </div>
        </ListItem>
        <ListItem>
          <div className="w-full">
            <div className="font-medium">Vegetables</div>
            <List variant="bullet" className="mt-2 ml-4">
              <li>Carrots</li>
              <li>Broccoli</li>
              <li>Spinach</li>
            </List>
          </div>
        </ListItem>
      </List>
    </div>
  ),
};
