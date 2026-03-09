import { useState } from 'react';

import { Tabs, TabsList, Tab, TabPanel } from './Tabs.baseui';
import { Badge } from '../Badge';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Tabs> = {
  title: 'Components/Tabs',
  component: Tabs,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Tabs>;

/**
 * Default tabs with standard variant
 */
export const Default: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    return (
      <Tabs value={value} onValueChange={(v) => setValue(v as number)}>
        <TabsList>
          <Tab value={0}>First Tab</Tab>
          <Tab value={1}>Second Tab</Tab>
          <Tab value={2}>Third Tab</Tab>
        </TabsList>
        <TabPanel value={0}>
          <h3 className="text-lg font-semibold mb-2">First Panel</h3>
          <p>This is the content of the first tab.</p>
        </TabPanel>
        <TabPanel value={1}>
          <h3 className="text-lg font-semibold mb-2">Second Panel</h3>
          <p>This is the content of the second tab.</p>
        </TabPanel>
        <TabPanel value={2}>
          <h3 className="text-lg font-semibold mb-2">Third Panel</h3>
          <p>This is the content of the third tab.</p>
        </TabPanel>
      </Tabs>
    );
  },
};

/**
 * Tabs variants: standard, pills, underline
 */
export const Variants: Story = {
  render: function Render() {
    const [standardValue, setStandardValue] = useState(0);
    const [pillsValue, setPillsValue] = useState(0);
    const [underlineValue, setUnderlineValue] = useState(0);

    return (
      <div className="space-y-8">
        {/* Standard */}
        <div>
          <h3 className="text-sm font-semibold mb-4 text-grey-600">Standard (bottom border on selected)</h3>
          <Tabs value={standardValue} onValueChange={(v) => setStandardValue(v as number)} variant="standard">
            <TabsList>
              <Tab value={0}>Dashboard</Tab>
              <Tab value={1}>Analytics</Tab>
              <Tab value={2}>Settings</Tab>
            </TabsList>
            <TabPanel value={0}>Standard tab 1 content</TabPanel>
            <TabPanel value={1}>Standard tab 2 content</TabPanel>
            <TabPanel value={2}>Standard tab 3 content</TabPanel>
          </Tabs>
        </div>

        {/* Pills */}
        <div>
          <h3 className="text-sm font-semibold mb-4 text-grey-600">Pills (solid background on selected)</h3>
          <Tabs value={pillsValue} onValueChange={(v) => setPillsValue(v as number)} variant="pills">
            <TabsList>
              <Tab value={0}>Dashboard</Tab>
              <Tab value={1}>Analytics</Tab>
              <Tab value={2}>Settings</Tab>
            </TabsList>
            <TabPanel value={0}>Pills tab 1 content</TabPanel>
            <TabPanel value={1}>Pills tab 2 content</TabPanel>
            <TabPanel value={2}>Pills tab 3 content</TabPanel>
          </Tabs>
        </div>

        {/* Underline */}
        <div>
          <h3 className="text-sm font-semibold mb-4 text-grey-600">Underline (thin underline on selected)</h3>
          <Tabs value={underlineValue} onValueChange={(v) => setUnderlineValue(v as number)} variant="underline">
            <TabsList>
              <Tab value={0}>Dashboard</Tab>
              <Tab value={1}>Analytics</Tab>
              <Tab value={2}>Settings</Tab>
            </TabsList>
            <TabPanel value={0}>Underline tab 1 content</TabPanel>
            <TabPanel value={1}>Underline tab 2 content</TabPanel>
            <TabPanel value={2}>Underline tab 3 content</TabPanel>
          </Tabs>
        </div>
      </div>
    );
  },
};

/**
 * Tabs sizes: small, medium, large
 */
export const Sizes: Story = {
  render: function Render() {
    const [smallValue, setSmallValue] = useState(0);
    const [mediumValue, setMediumValue] = useState(0);
    const [largeValue, setLargeValue] = useState(0);

    return (
      <div className="space-y-8">
        {/* Small */}
        <div>
          <h3 className="text-sm font-semibold mb-4 text-grey-600">Small</h3>
          <Tabs value={smallValue} onValueChange={(v) => setSmallValue(v as number)} size="small">
            <TabsList>
              <Tab value={0}>Tab 1</Tab>
              <Tab value={1}>Tab 2</Tab>
              <Tab value={2}>Tab 3</Tab>
            </TabsList>
            <TabPanel value={0}>Small tab content</TabPanel>
            <TabPanel value={1}>Small tab content</TabPanel>
            <TabPanel value={2}>Small tab content</TabPanel>
          </Tabs>
        </div>

        {/* Medium */}
        <div>
          <h3 className="text-sm font-semibold mb-4 text-grey-600">Medium (default)</h3>
          <Tabs value={mediumValue} onValueChange={(v) => setMediumValue(v as number)} size="medium">
            <TabsList>
              <Tab value={0}>Tab 1</Tab>
              <Tab value={1}>Tab 2</Tab>
              <Tab value={2}>Tab 3</Tab>
            </TabsList>
            <TabPanel value={0}>Medium tab content</TabPanel>
            <TabPanel value={1}>Medium tab content</TabPanel>
            <TabPanel value={2}>Medium tab content</TabPanel>
          </Tabs>
        </div>

        {/* Large */}
        <div>
          <h3 className="text-sm font-semibold mb-4 text-grey-600">Large</h3>
          <Tabs value={largeValue} onValueChange={(v) => setLargeValue(v as number)} size="large">
            <TabsList>
              <Tab value={0}>Tab 1</Tab>
              <Tab value={1}>Tab 2</Tab>
              <Tab value={2}>Tab 3</Tab>
            </TabsList>
            <TabPanel value={0}>Large tab content</TabPanel>
            <TabPanel value={1}>Large tab content</TabPanel>
            <TabPanel value={2}>Large tab content</TabPanel>
          </Tabs>
        </div>
      </div>
    );
  },
};

/**
 * Tabs with icons
 */
export const WithIcons: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    const HomeIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
      </svg>
    );

    const ChartIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
      </svg>
    );

    const SettingsIcon = (
      <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
    );

    return (
      <Tabs value={value} onValueChange={(v) => setValue(v as number)}>
        <TabsList>
          <Tab value={0} startIcon={HomeIcon}>Dashboard</Tab>
          <Tab value={1} startIcon={ChartIcon}>Analytics</Tab>
          <Tab value={2} startIcon={SettingsIcon}>Settings</Tab>
        </TabsList>
        <TabPanel value={0}>Dashboard content with charts and metrics</TabPanel>
        <TabPanel value={1}>Analytics data and reports</TabPanel>
        <TabPanel value={2}>Application settings and preferences</TabPanel>
      </Tabs>
    );
  },
};

/**
 * Tabs with badges
 */
export const WithBadges: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    return (
      <Tabs value={value} onValueChange={(v) => setValue(v as number)}>
        <TabsList>
          <Tab value={0} badge={<Badge color="primary">3</Badge>}>
            Messages
          </Tab>
          <Tab value={1} badge={<Badge color="error">12</Badge>}>
            Notifications
          </Tab>
          <Tab value={2} badge={<Badge color="success">New</Badge>}>
            Updates
          </Tab>
          <Tab value={3}>Archive</Tab>
        </TabsList>
        <TabPanel value={0}>
          <p>You have 3 unread messages</p>
        </TabPanel>
        <TabPanel value={1}>
          <p>You have 12 new notifications</p>
        </TabPanel>
        <TabPanel value={2}>
          <p>New features available!</p>
        </TabPanel>
        <TabPanel value={3}>
          <p>Archived items</p>
        </TabPanel>
      </Tabs>
    );
  },
};

/**
 * Tabs with disabled state
 */
export const DisabledTabs: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    return (
      <Tabs value={value} onValueChange={(v) => setValue(v as number)}>
        <TabsList>
          <Tab value={0}>Active Tab</Tab>
          <Tab value={1} disabled>Disabled Tab</Tab>
          <Tab value={2}>Another Active Tab</Tab>
          <Tab value={3} disabled>Also Disabled</Tab>
        </TabsList>
        <TabPanel value={0}>First tab content</TabPanel>
        <TabPanel value={1}>This content won't be accessible</TabPanel>
        <TabPanel value={2}>Third tab content</TabPanel>
        <TabPanel value={3}>This content won't be accessible either</TabPanel>
      </Tabs>
    );
  },
};

/**
 * Controlled tabs with external state
 */
export const ControlledTabs: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    return (
      <div className="space-y-4">
        <div className="flex gap-2">
          <button
            onClick={() => setValue(0)}
            className="px-3 py-1 text-sm bg-grey-100 rounded hover:bg-grey-200"
          >
            Go to Tab 1
          </button>
          <button
            onClick={() => setValue(1)}
            className="px-3 py-1 text-sm bg-grey-100 rounded hover:bg-grey-200"
          >
            Go to Tab 2
          </button>
          <button
            onClick={() => setValue(2)}
            className="px-3 py-1 text-sm bg-grey-100 rounded hover:bg-grey-200"
          >
            Go to Tab 3
          </button>
        </div>
        
        <Tabs value={value} onValueChange={(v) => setValue(v as number)}>
          <TabsList>
            <Tab value={0}>Tab 1</Tab>
            <Tab value={1}>Tab 2</Tab>
            <Tab value={2}>Tab 3</Tab>
          </TabsList>
          <TabPanel value={0}>
            <p>Current tab: <strong>Tab 1</strong></p>
          </TabPanel>
          <TabPanel value={1}>
            <p>Current tab: <strong>Tab 2</strong></p>
          </TabPanel>
          <TabPanel value={2}>
            <p>Current tab: <strong>Tab 3</strong></p>
          </TabPanel>
        </Tabs>
      </div>
    );
  },
};

/**
 * Tabs with complex content
 */
export const ComplexContent: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    return (
      <Tabs value={value} onValueChange={(v) => setValue(v as number)} variant="pills">
        <TabsList>
          <Tab value={0}>Profile</Tab>
          <Tab value={1}>Account</Tab>
          <Tab value={2}>Preferences</Tab>
        </TabsList>
        
        <TabPanel value={0}>
          <div className="space-y-4">
            <h3 className="text-lg font-semibold">Profile Information</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">First Name</label>
                <input type="text" className="w-full px-3 py-2 border rounded" defaultValue="John" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Last Name</label>
                <input type="text" className="w-full px-3 py-2 border rounded" defaultValue="Doe" />
              </div>
            </div>
          </div>
        </TabPanel>
        
        <TabPanel value={1}>
          <div className="space-y-4">
            <h3 className="text-lg font-semibold">Account Settings</h3>
            <div>
              <label className="block text-sm font-medium mb-1">Email</label>
              <input type="email" className="w-full px-3 py-2 border rounded" defaultValue="john@example.com" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Password</label>
              <input type="password" className="w-full px-3 py-2 border rounded" />
            </div>
          </div>
        </TabPanel>
        
        <TabPanel value={2}>
          <div className="space-y-4">
            <h3 className="text-lg font-semibold">Preferences</h3>
            <div className="space-y-2">
              <label className="flex items-center gap-2">
                <input type="checkbox" defaultChecked />
                <span className="text-sm">Email notifications</span>
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" />
                <span className="text-sm">SMS notifications</span>
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" defaultChecked />
                <span className="text-sm">Newsletter subscription</span>
              </label>
            </div>
          </div>
        </TabPanel>
      </Tabs>
    );
  },
};

/**
 * Tabs with string values instead of numbers
 */
export const StringValues: Story = {
  render: function Render() {
    const [value, setValue] = useState('dashboard');

    return (
      <Tabs value={value} onValueChange={(v) => setValue(v as string)}>
        <TabsList>
          <Tab value="dashboard">Dashboard</Tab>
          <Tab value="analytics">Analytics</Tab>
          <Tab value="reports">Reports</Tab>
        </TabsList>
        <TabPanel value="dashboard">Dashboard overview and metrics</TabPanel>
        <TabPanel value="analytics">Detailed analytics and insights</TabPanel>
        <TabPanel value="reports">Generate and view reports</TabPanel>
      </Tabs>
    );
  },
};

/**
 * Keyboard accessibility demonstration
 */
export const KeyboardNavigation: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    return (
      <div className="space-y-4">
        <div className="text-sm text-grey-600 bg-grey-50 p-4 rounded">
          <strong>Keyboard controls:</strong>
          <ul className="list-disc list-inside mt-2 space-y-1">
            <li>TAB - Focus the tab list</li>
            <li>← → Arrow keys - Navigate between tabs</li>
            <li>HOME - Jump to first tab</li>
            <li>END - Jump to last tab</li>
            <li>ENTER/SPACE - Activate focused tab</li>
          </ul>
        </div>

        <Tabs value={value} onValueChange={(v) => setValue(v as number)}>
          <TabsList>
            <Tab value={0}>First</Tab>
            <Tab value={1}>Second</Tab>
            <Tab value={2}>Third</Tab>
            <Tab value={3}>Fourth</Tab>
            <Tab value={4}>Fifth</Tab>
          </TabsList>
          <TabPanel value={0}>Content 1</TabPanel>
          <TabPanel value={1}>Content 2</TabPanel>
          <TabPanel value={2}>Content 3</TabPanel>
          <TabPanel value={3}>Content 4</TabPanel>
          <TabPanel value={4}>Content 5</TabPanel>
        </Tabs>
      </div>
    );
  },
};

/**
 * Many tabs (scrollable scenario)
 */
export const ManyTabs: Story = {
  render: function Render() {
    const [value, setValue] = useState(0);

    return (
      <Tabs value={value} onValueChange={(v) => setValue(v as number)} variant="underline">
        <TabsList className="overflow-x-auto">
          {Array.from({ length: 15 }, (_, i) => (
            <Tab key={i} value={i}>
              Tab {i + 1}
            </Tab>
          ))}
        </TabsList>
        {Array.from({ length: 15 }, (_, i) => (
          <TabPanel key={i} value={i}>
            Content for tab {i + 1}
          </TabPanel>
        ))}
      </Tabs>
    );
  },
};
