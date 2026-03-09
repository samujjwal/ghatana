import { useState } from 'react';

import { Menu, MenuItem, MenuDivider } from './Menu.baseui';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Menu> = {
  title: 'Components/Menu',
  component: Menu,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Menu>;

/**
 * Basic menu with simple text items
 */
export const Default: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <Menu
        {...args}
        open={open}
        onOpenChange={setOpen}
        trigger={<Button onClick={() => setOpen(!open)}>Open Menu</Button>}
      >
        <MenuItem text="Profile" />
        <MenuItem text="Settings" />
        <MenuDivider />
        <MenuItem text="Logout" />
      </Menu>
    );
  },
};

/**
 * Menu items with icons
 */
export const WithIcons: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    // Simple SVG icons
    const HomeIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
      </svg>
    );

    const SettingsIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
    );

    const LogoutIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
      </svg>
    );

    return (
      <Menu
        {...args}
        open={open}
        onOpenChange={setOpen}
        trigger={<Button onClick={() => setOpen(!open)}>Menu with Icons</Button>}
      >
        <MenuItem icon={HomeIcon} text="Home" />
        <MenuItem icon={SettingsIcon} text="Settings" />
        <MenuDivider />
        <MenuItem icon={LogoutIcon} text="Logout" />
      </Menu>
    );
  },
};

/**
 * Menu items with secondary text
 */
export const WithSecondaryText: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    const UserIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
      </svg>
    );

    const KeyIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
      </svg>
    );

    return (
      <Menu
        {...args}
        open={open}
        onOpenChange={setOpen}
        trigger={<Button onClick={() => setOpen(!open)}>Account Menu</Button>}
      >
        <MenuItem 
          icon={UserIcon} 
          text="Profile" 
          secondaryText="View and edit your profile"
        />
        <MenuItem 
          icon={KeyIcon} 
          text="Security" 
          secondaryText="Password and 2FA settings"
        />
      </Menu>
    );
  },
};

/**
 * Menu shapes: rounded, soft, square
 */
export const Shapes: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [shape, setShape] = useState<'rounded' | 'square' | 'soft' | null>(null);

    return (
      <div className="flex gap-2">
        <Menu
          {...args}
          open={shape === 'rounded'}
          onOpenChange={(open) => setShape(open ? 'rounded' : null)}
          trigger={<Button onClick={() => setShape('rounded')}>Rounded (6px)</Button>}
          shape="rounded"
        >
          <MenuItem text="Option 1" />
          <MenuItem text="Option 2" />
          <MenuItem text="Option 3" />
        </Menu>

        <Menu
          {...args}
          open={shape === 'soft'}
          onOpenChange={(open) => setShape(open ? 'soft' : null)}
          trigger={<Button onClick={() => setShape('soft')}>Soft (8px)</Button>}
          shape="soft"
        >
          <MenuItem text="Option 1" />
          <MenuItem text="Option 2" />
          <MenuItem text="Option 3" />
        </Menu>

        <Menu
          {...args}
          open={shape === 'square'}
          onOpenChange={(open) => setShape(open ? 'square' : null)}
          trigger={<Button onClick={() => setShape('square')}>Square (2px)</Button>}
          shape="square"
        >
          <MenuItem text="Option 1" />
          <MenuItem text="Option 2" />
          <MenuItem text="Option 3" />
        </Menu>
      </div>
    );
  },
};

/**
 * Menu elevation levels (shadow depth)
 */
export const Elevations: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [elevation, setElevation] = useState<0 | 1 | 2 | 4 | 8 | null>(null);

    return (
      <div className="flex flex-wrap gap-2">
        <Menu
          {...args}
          open={elevation === 0}
          onOpenChange={(open) => setElevation(open ? 0 : null)}
          trigger={<Button onClick={() => setElevation(0)}>Elevation 0</Button>}
          elevation={0}
        >
          <MenuItem text="No shadow" />
        </Menu>

        <Menu
          {...args}
          open={elevation === 1}
          onOpenChange={(open) => setElevation(open ? 1 : null)}
          trigger={<Button onClick={() => setElevation(1)}>Elevation 1</Button>}
          elevation={1}
        >
          <MenuItem text="Small shadow" />
        </Menu>

        <Menu
          {...args}
          open={elevation === 2}
          onOpenChange={(open) => setElevation(open ? 2 : null)}
          trigger={<Button onClick={() => setElevation(2)}>Elevation 2 (default)</Button>}
          elevation={2}
        >
          <MenuItem text="Medium shadow" />
        </Menu>

        <Menu
          {...args}
          open={elevation === 4}
          onOpenChange={(open) => setElevation(open ? 4 : null)}
          trigger={<Button onClick={() => setElevation(4)}>Elevation 4</Button>}
          elevation={4}
        >
          <MenuItem text="Large shadow" />
        </Menu>

        <Menu
          {...args}
          open={elevation === 8}
          onOpenChange={(open) => setElevation(open ? 8 : null)}
          trigger={<Button onClick={() => setElevation(8)}>Elevation 8</Button>}
          elevation={8}
        >
          <MenuItem text="Extra large shadow" />
        </Menu>
      </div>
    );
  },
};

/**
 * Dense menu items with reduced padding
 */
export const DenseItems: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <Menu
        {...args}
        open={open}
        onOpenChange={setOpen}
        trigger={<Button onClick={() => setOpen(!open)}>Dense Menu</Button>}
      >
        <MenuItem text="Compact Option 1" dense />
        <MenuItem text="Compact Option 2" dense />
        <MenuItem text="Compact Option 3" dense />
        <MenuItem text="Compact Option 4" dense />
        <MenuItem text="Compact Option 5" dense />
      </Menu>
    );
  },
};

/**
 * Menu with disabled items
 */
export const DisabledItems: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <Menu
        {...args}
        open={open}
        onOpenChange={setOpen}
        trigger={<Button onClick={() => setOpen(!open)}>Menu with Disabled</Button>}
      >
        <MenuItem text="Enabled Item" />
        <MenuItem text="Disabled Item" disabled />
        <MenuItem text="Another Enabled Item" />
        <MenuItem text="Another Disabled Item" disabled />
      </Menu>
    );
  },
};

/**
 * Menu with click handlers
 */
export const WithClickHandlers: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);
    const [selected, setSelected] = useState<string>('');

    const handleClick = (item: string) => {
      setSelected(item);
      setOpen(false);
    };

    return (
      <div className="space-y-2">
        {selected && (
          <div className="text-sm text-grey-600">Selected: {selected}</div>
        )}
        <Menu
          {...args}
          open={open}
          onOpenChange={setOpen}
          trigger={<Button onClick={() => setOpen(!open)}>Select Option</Button>}
        >
          <MenuItem text="Option A" onClick={() => handleClick('Option A')} />
          <MenuItem text="Option B" onClick={() => handleClick('Option B')} />
          <MenuItem text="Option C" onClick={() => handleClick('Option C')} />
        </Menu>
      </div>
    );
  },
};

/**
 * Menu with custom content
 */
export const CustomContent: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <Menu
        {...args}
        open={open}
        onOpenChange={setOpen}
        trigger={<Button onClick={() => setOpen(!open)}>Custom Menu</Button>}
      >
        <MenuItem>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-primary-500 text-white flex items-center justify-center text-sm font-bold">
              JD
            </div>
            <div>
              <div className="font-medium">John Doe</div>
              <div className="text-xs text-grey-600">john@example.com</div>
            </div>
          </div>
        </MenuItem>
        <MenuDivider />
        <MenuItem>
          <div className="flex items-center justify-between w-full">
            <span>Dark Mode</span>
            <div className="w-10 h-5 bg-grey-300 rounded-full" />
          </div>
        </MenuItem>
      </Menu>
    );
  },
};

/**
 * Keyboard navigation demonstration
 */
export const KeyboardNavigation: Story = {
  args: {
    open: false,
  },
  render: function Render(args) {
    const [open, setOpen] = useState(false);

    return (
      <div className="space-y-2">
        <Button onClick={() => setOpen(!open)}>Open Menu</Button>
        <div className="text-sm text-grey-600">
          Keyboard controls:
          <br />
          • ↑/↓ Arrow keys - Navigate items
          <br />
          • ENTER - Select item
          <br />
          • ESC - Close menu
        </div>
        <Menu
          {...args}
          open={open}
          onOpenChange={setOpen}
          trigger={<Button onClick={() => setOpen(!open)}>Keyboard Menu</Button>}
        >
          <MenuItem text="First Item" />
          <MenuItem text="Second Item" />
          <MenuItem text="Third Item" />
          <MenuDivider />
          <MenuItem text="Last Item" />
        </Menu>
      </div>
    );
  },
};
