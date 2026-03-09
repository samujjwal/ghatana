/**
 * Storybook stories for Switch component
 */
import { useState } from 'react';

import { Switch } from './Switch.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Switch (Tailwind)',
  component: Switch,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    colorScheme: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'grey'],
      description: 'Color scheme',
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'Size of switch',
    },
    label: {
      control: 'text',
      description: 'Label text',
    },
    disabled: {
      control: 'boolean',
      description: 'Disabled state',
    },
  },
} satisfies Meta<typeof Switch>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default switch
 */
export const Default: Story = {
  args: {
    label: 'Enable notifications',
  },
};

/**
 * All color schemes
 */
export const ColorSchemes: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <Switch label="Primary" colorScheme="primary" checked />
      <Switch label="Secondary" colorScheme="secondary" checked />
      <Switch label="Success" colorScheme="success" checked />
      <Switch label="Error" colorScheme="error" checked />
      <Switch label="Warning" colorScheme="warning" checked />
      <Switch label="Grey" colorScheme="grey" checked />
    </div>
  ),
};

/**
 * All sizes
 */
export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <Switch size="sm" label="Small switch" checked />
      <Switch size="md" label="Medium switch" checked />
      <Switch size="lg" label="Large switch" checked />
    </div>
  ),
};

/**
 * Switch states
 */
export const States: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <Switch label="Off (unchecked)" checked={false} />
      <Switch label="On (checked)" checked={true} />
      <Switch label="Disabled off" disabled checked={false} />
      <Switch label="Disabled on" disabled checked={true} />
    </div>
  ),
};

/**
 * Interactive example
 */
export const Interactive: Story = {
  render: () => {
    const [isEnabled, setIsEnabled] = useState(false);

    return (
      <div className="flex flex-col gap-4 p-6 bg-grey-50 rounded-lg w-80">
        <Switch
          label="Enable feature"
          checked={isEnabled}
          onChange={(e) => setIsEnabled((e.target as HTMLInputElement).checked)}
        />
        <p className="text-sm text-grey-700">
          Feature is <strong>{isEnabled ? 'enabled' : 'disabled'}</strong>
        </p>
      </div>
    );
  },
};

/**
 * Settings panel example
 */
export const SettingsExample: Story = {
  render: () => {
    const [settings, setSettings] = useState({
      notifications: true,
      darkMode: false,
      autoSave: true,
      analytics: false,
    });

    const updateSetting = (key: keyof typeof settings) => (e: React.ChangeEvent) => {
      setSettings({ ...settings, [key]: (e.target as HTMLInputElement).checked });
    };

    return (
      <div className="w-96 p-6 bg-white rounded-lg border border-grey-200">
        <h2 className="text-xl font-bold mb-4">Preferences</h2>
        
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between py-2 border-b border-grey-100">
            <div>
              <p className="font-medium">Push Notifications</p>
              <p className="text-sm text-grey-600">Receive notifications on your device</p>
            </div>
            <Switch
              checked={settings.notifications}
              onChange={updateSetting('notifications')}
              colorScheme="primary"
            />
          </div>

          <div className="flex items-center justify-between py-2 border-b border-grey-100">
            <div>
              <p className="font-medium">Dark Mode</p>
              <p className="text-sm text-grey-600">Use dark theme</p>
            </div>
            <Switch
              checked={settings.darkMode}
              onChange={updateSetting('darkMode')}
              colorScheme="grey"
            />
          </div>

          <div className="flex items-center justify-between py-2 border-b border-grey-100">
            <div>
              <p className="font-medium">Auto-save</p>
              <p className="text-sm text-grey-600">Automatically save your work</p>
            </div>
            <Switch
              checked={settings.autoSave}
              onChange={updateSetting('autoSave')}
              colorScheme="success"
            />
          </div>

          <div className="flex items-center justify-between py-2">
            <div>
              <p className="font-medium">Analytics</p>
              <p className="text-sm text-grey-600">Help us improve the app</p>
            </div>
            <Switch
              checked={settings.analytics}
              onChange={updateSetting('analytics')}
              colorScheme="warning"
            />
          </div>
        </div>
      </div>
    );
  },
};

/**
 * Accessibility features
 */
export const Accessibility: Story = {
  render: () => (
    <div className="flex flex-col gap-6 w-96">
      <div>
        <h3 className="text-lg font-semibold mb-2">Keyboard Navigation</h3>
        <p className="text-sm text-grey-600 mb-4">
          Tab to focus, Space to toggle
        </p>
        <div className="flex flex-col gap-2">
          <Switch label="Press Tab to focus me" />
          <Switch label="Tab again for next switch" />
          <Switch label="Space to toggle" />
        </div>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">Screen Reader Support</h3>
        <Switch
          label="Accessible switch with ARIA"
          aria-label="Enable notifications"
          aria-describedby="switch-help"
        />
        <p id="switch-help" className="text-sm text-grey-600 mt-2">
          This description is announced by screen readers
        </p>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-2">Focus Indicators</h3>
        <p className="text-sm text-grey-600 mb-4">
          Click to focus and see colored focus rings
        </p>
        <div className="flex flex-col gap-2">
          <Switch label="Primary focus ring" colorScheme="primary" />
          <Switch label="Success focus ring" colorScheme="success" />
          <Switch label="Error focus ring" colorScheme="error" />
        </div>
      </div>
    </div>
  ),
};

/**
 * Playground for testing
 */
export const Playground: Story = {
  args: {
    label: 'Switch label',
    colorScheme: 'primary',
    size: 'md',
  },
};
