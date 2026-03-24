import { Breadcrumb } from './Breadcrumb.tailwind';

import type { BreadcrumbItemType } from './Breadcrumb.tailwind';
import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Breadcrumb> = {
  title: 'Components/Breadcrumb',
  component: Breadcrumb,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
    },
    showHomeIcon: {
      control: 'boolean',
    },
    maxItems: {
      control: 'number',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Breadcrumb>;

const basicItems: BreadcrumbItemType[] = [
  { label: 'Home', href: '/' },
  { label: 'Products', href: '/products' },
  { label: 'Electronics' },
];

const longItems: BreadcrumbItemType[] = [
  { label: 'Home', href: '/' },
  { label: 'Category', href: '/category' },
  { label: 'Subcategory', href: '/category/sub' },
  { label: 'Products', href: '/category/sub/products' },
  { label: 'Details', href: '/category/sub/products/details' },
  { label: 'Specifications' },
];

export const Default: Story = {
  args: {
    items: basicItems,
  },
};

export const WithHomeIcon: Story = {
  args: {
    items: basicItems,
    showHomeIcon: true,
  },
};

export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col gap-4 w-full">
      <Breadcrumb
        items={basicItems}
        size="small"
      />
      <Breadcrumb
        items={basicItems}
        size="medium"
      />
      <Breadcrumb
        items={basicItems}
        size="large"
      />
    </div>
  ),
};

export const CustomSeparator: Story = {
  args: {
    items: basicItems,
    separator: '›',
  },
};

export const DashSeparator: Story = {
  args: {
    items: basicItems,
    separator: '-',
  },
};

export const SlashSeparator: Story = {
  args: {
    items: basicItems,
    separator: '/',
  },
};

export const WithMaxItems: Story = {
  args: {
    items: longItems,
    maxItems: 3,
  },
};

export const CollapsedLong: Story = {
  args: {
    items: longItems,
    maxItems: 4,
    showHomeIcon: true,
  },
};

export const WithCustomIcons: Story = {
  args: {
    items: [
      {
        label: 'Dashboard',
        href: '/',
        icon: (
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="2" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.5" />
            <rect x="9" y="2" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.5" />
            <rect x="2" y="9" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.5" />
            <rect x="9" y="9" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.5" />
          </svg>
        ),
      },
      {
        label: 'Settings',
        href: '/settings',
        icon: (
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M8 10C9.10457 10 10 9.10457 10 8C10 6.89543 9.10457 6 8 6C6.89543 6 6 6.89543 6 8C6 9.10457 6.89543 10 8 10Z"
              stroke="currentColor"
              strokeWidth="1.5"
            />
            <path
              d="M13 8C13 8 11.5 5 8 5C4.5 5 3 8 3 8C3 8 4.5 11 8 11C11.5 11 13 8 13 8Z"
              stroke="currentColor"
              strokeWidth="1.5"
            />
          </svg>
        ),
      },
      {
        label: 'Profile',
        icon: (
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="8" cy="5" r="2.5" stroke="currentColor" strokeWidth="1.5" />
            <path
              d="M3 13C3 10.7909 5.23858 9 8 9C10.7614 9 13 10.7909 13 13"
              stroke="currentColor"
              strokeWidth="1.5"
            />
          </svg>
        ),
      },
    ],
  },
};

export const WithClickHandlers: Story = {
  render: () => {
    const handleClick = (label: string) => {
      alert(`Clicked: ${label}`);
    };

    return (
      <Breadcrumb
        items={[
          { label: 'Home', onClick: () => handleClick('Home') },
          { label: 'Products', onClick: () => handleClick('Products') },
          { label: 'Details', onClick: () => handleClick('Details') },
          { label: 'Current Page' },
        ]}
      />
    );
  },
};

export const MixedNavigation: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'Category', onClick: () => alert('Category clicked') },
      { label: 'Product', href: '/product' },
      { label: 'Current' },
    ],
  },
};

export const WithDisabledItems: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'Disabled Step', disabled: true },
      { label: 'Current' },
    ],
  },
};

export const FileBrowserExample: Story = {
  args: {
    items: [
      { label: 'Documents', href: '/documents' },
      { label: 'Projects', href: '/documents/projects' },
      { label: 'React App', href: '/documents/projects/react-app' },
      { label: 'src', href: '/documents/projects/react-app/src' },
      { label: 'components' },
    ],
    separator: '/',
  },
};

export const EcommerceExample: Story = {
  args: {
    items: [
      { label: 'Home', href: '/' },
      { label: 'Electronics', href: '/electronics' },
      { label: 'Computers', href: '/electronics/computers' },
      { label: 'Laptops', href: '/electronics/computers/laptops' },
      { label: 'Gaming Laptops' },
    ],
    showHomeIcon: true,
    maxItems: 4,
  },
};

export const DarkMode: Story = {
  render: () => (
    <div className="dark bg-grey-900 p-8 rounded-lg">
      <Breadcrumb
        items={[
          { label: 'Home', href: '/' },
          { label: 'Products', href: '/products' },
          { label: 'Details', href: '/products/details' },
          { label: 'Current Page' },
        ]}
        showHomeIcon
      />
    </div>
  ),
  parameters: {
    backgrounds: { default: 'dark' },
  },
};

export const Playground: Story = {
  args: {
    items: basicItems,
    size: 'medium',
    showHomeIcon: false,
    maxItems: undefined,
  },
};
