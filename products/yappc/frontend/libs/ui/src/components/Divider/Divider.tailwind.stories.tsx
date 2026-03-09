import { Divider } from './Divider.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Divider (Tailwind)',
  component: Divider,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Divider>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {},
};

export const Horizontal: Story = {
  args: {},
  render: () => (
    <div className="space-y-4">
      <p>Content above divider</p>
      <Divider />
      <p>Content below divider</p>
    </div>
  ),
};

export const Vertical: Story = {
  args: {},
  render: () => (
    <div className="flex items-center h-12 gap-0">
      <span>Left content</span>
      <Divider orientation="vertical" />
      <span>Right content</span>
    </div>
  ),
};

export const Variants: Story = {
  args: {},
  render: () => (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-semibold mb-2">Full Width (default):</p>
        <div className="border p-4">
          <p>Content above</p>
          <Divider variant="fullWidth" />
          <p>Content below</p>
        </div>
      </div>
      
      <div>
        <p className="text-sm font-semibold mb-2">Inset:</p>
        <div className="border p-4">
          <p>Content above</p>
          <Divider variant="inset" />
          <p>Content below</p>
        </div>
      </div>
      
      <div>
        <p className="text-sm font-semibold mb-2">Middle:</p>
        <div className="border p-4">
          <p>Content above</p>
          <Divider variant="middle" />
          <p>Content below</p>
        </div>
      </div>
    </div>
  ),
};

export const WithText: Story = {
  args: {},
  render: () => (
    <div className="space-y-6">
      <div>
        <p>Content above</p>
        <Divider>OR</Divider>
        <p>Content below</p>
      </div>
      
      <div>
        <p>Section 1</p>
        <Divider>SECTION 2</Divider>
        <p>Section 2 content</p>
      </div>
    </div>
  ),
};

export const TextAlignment: Story = {
  args: {},
  render: () => (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-semibold mb-2">Left aligned:</p>
        <Divider textAlign="left">Left</Divider>
      </div>
      
      <div>
        <p className="text-sm font-semibold mb-2">Center aligned (default):</p>
        <Divider textAlign="center">Center</Divider>
      </div>
      
      <div>
        <p className="text-sm font-semibold mb-2">Right aligned:</p>
        <Divider textAlign="right">Right</Divider>
      </div>
    </div>
  ),
};

export const VerticalWithText: Story = {
  args: {},
  render: () => (
    <div className="flex items-center h-20 gap-0">
      <div className="px-4">Left content</div>
      <Divider orientation="vertical">OR</Divider>
      <div className="px-4">Right content</div>
    </div>
  ),
};

export const ListSeparator: Story = {
  args: {},
  render: () => (
    <div className="border rounded-lg overflow-hidden">
      <div className="p-4">Item 1</div>
      <Divider variant="fullWidth" />
      <div className="p-4">Item 2</div>
      <Divider variant="fullWidth" />
      <div className="p-4">Item 3</div>
      <Divider variant="fullWidth" />
      <div className="p-4">Item 4</div>
    </div>
  ),
};

export const InsetList: Story = {
  args: {},
  render: () => (
    <div className="border rounded-lg overflow-hidden">
      <div className="p-4 flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-primary-100" />
        <div>
          <p className="font-semibold">User Name</p>
          <p className="text-sm text-grey-600">user@example.com</p>
        </div>
      </div>
      <Divider variant="inset" />
      <div className="p-4 flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-success-100" />
        <div>
          <p className="font-semibold">Another User</p>
          <p className="text-sm text-grey-600">another@example.com</p>
        </div>
      </div>
    </div>
  ),
};

export const FormSections: Story = {
  args: {},
  render: () => (
    <div className="border rounded-lg p-6 space-y-6 max-w-md">
      <div>
        <h3 className="font-semibold mb-2">Personal Information</h3>
        <input className="border rounded px-3 py-2 w-full mb-2" placeholder="Name" />
        <input className="border rounded px-3 py-2 w-full" placeholder="Email" />
      </div>
      
      <Divider>Account Details</Divider>
      
      <div>
        <input className="border rounded px-3 py-2 w-full mb-2" placeholder="Username" />
        <input className="border rounded px-3 py-2 w-full" type="password" placeholder="Password" />
      </div>
    </div>
  ),
};

export const ToolbarSeparator: Story = {
  args: {},
  render: () => (
    <div className="border rounded-lg p-2 flex items-center gap-2">
      <button className="px-3 py-1 hover:bg-grey-100 rounded">Bold</button>
      <button className="px-3 py-1 hover:bg-grey-100 rounded">Italic</button>
      <Divider orientation="vertical" className="h-6" />
      <button className="px-3 py-1 hover:bg-grey-100 rounded">Link</button>
      <button className="px-3 py-1 hover:bg-grey-100 rounded">Image</button>
      <Divider orientation="vertical" className="h-6" />
      <button className="px-3 py-1 hover:bg-grey-100 rounded">Undo</button>
      <button className="px-3 py-1 hover:bg-grey-100 rounded">Redo</button>
    </div>
  ),
};

export const Playground: Story = {
  args: {
    orientation: 'horizontal',
    variant: 'fullWidth',
    textAlign: 'center',
    children: undefined,
  },
};
