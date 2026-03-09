import { AppBar } from './AppBar.tailwind';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof AppBar> = {
  title: 'Components/Layout/AppBar',
  component: AppBar,
  tags: ['autodocs'],
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'AppBar is a header bar component for navigation and branding. Commonly used with Toolbar for layout.',
      },
    },
  },
  argTypes: {
    position: {
      control: 'select',
      options: ['static', 'fixed', 'sticky', 'absolute', 'relative'],
      description: 'CSS positioning behavior',
    },
    color: {
      control: 'select',
      options: ['default', 'primary', 'secondary', 'transparent', 'inherit'],
      description: 'Color variant',
    },
    elevation: {
      control: 'select',
      options: [0, 1, 2, 3, 4, 6, 8, 12, 16, 24],
      description: 'Shadow depth',
    },
    height: {
      control: 'select',
      options: ['compact', 'normal', 'tall'],
      description: 'AppBar height',
    },
    blur: {
      control: 'boolean',
      description: 'Enable backdrop blur effect',
    },
    component: {
      control: 'select',
      options: ['header', 'nav', 'div'],
      description: 'HTML element to render as',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof AppBar>;

/**
 * Default AppBar with static positioning
 */
export const Default: Story = {
  args: {
    children: (
      <div className="px-6 w-full flex items-center justify-between">
        <h1 className="text-xl font-semibold">My Application</h1>
        <nav className="flex gap-4">
          <Button variant="ghost" size="sm">
            Home
          </Button>
          <Button variant="ghost" size="sm">
            About
          </Button>
          <Button variant="ghost" size="sm">
            Contact
          </Button>
        </nav>
      </div>
    ),
  },
};

/**
 * Different position variants - static, fixed, sticky, absolute, relative
 */
export const Positions: Story = {
  render: () => (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-grey-600 mb-2 px-4">Static (default)</p>
        <AppBar position="static">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Static AppBar</h2>
          </div>
        </AppBar>
      </div>

      <div className="relative h-32 border-2 border-dashed border-grey-300">
        <p className="text-sm text-grey-600 mb-2 px-4 pt-2">Fixed (within container)</p>
        <AppBar position="fixed">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Fixed AppBar</h2>
          </div>
        </AppBar>
      </div>

      <div className="h-48 overflow-auto border-2 border-dashed border-grey-300">
        <p className="text-sm text-grey-600 mb-2 px-4 pt-2">Sticky (scroll to see effect)</p>
        <AppBar position="sticky">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Sticky AppBar</h2>
          </div>
        </AppBar>
        <div className="p-6 space-y-4">
          <p>Scroll down to see the sticky behavior...</p>
          {Array.from({ length: 10 }).map((_, i) => (
            <p key={i}>Content line {i + 1}</p>
          ))}
        </div>
      </div>

      <div className="relative h-32 border-2 border-dashed border-grey-300">
        <p className="text-sm text-grey-600 mb-2 px-4 pt-2">Absolute (positioned within container)</p>
        <AppBar position="absolute">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Absolute AppBar</h2>
          </div>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2 px-4">Relative</p>
        <AppBar position="relative">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Relative AppBar</h2>
          </div>
        </AppBar>
      </div>
    </div>
  ),
};

/**
 * Different color variants - default, primary, secondary, transparent
 */
export const Colors: Story = {
  render: () => (
    <div className="space-y-4">
      <AppBar color="default">
        <div className="px-6 w-full">
          <h2 className="text-lg font-medium">Default Color</h2>
        </div>
      </AppBar>

      <AppBar color="primary">
        <div className="px-6 w-full">
          <h2 className="text-lg font-medium">Primary Color</h2>
        </div>
      </AppBar>

      <AppBar color="secondary">
        <div className="px-6 w-full">
          <h2 className="text-lg font-medium">Secondary Color</h2>
        </div>
      </AppBar>

      <div className="bg-gradient-to-r from-purple-500 to-pink-500 p-8 rounded-lg">
        <AppBar color="transparent">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium text-white">Transparent (inherits background)</h2>
          </div>
        </AppBar>
      </div>
    </div>
  ),
};

/**
 * Different elevation (shadow) levels
 */
export const Elevations: Story = {
  render: () => (
    <div className="space-y-6 bg-grey-50 p-8">
      {[0, 1, 2, 3, 4, 6, 8].map((elevation) => (
        <div key={elevation}>
          <p className="text-sm text-grey-600 mb-2">Elevation {elevation}</p>
          <AppBar elevation={elevation as never}>
            <div className="px-6 w-full">
              <h2 className="text-lg font-medium">Elevation {elevation}</h2>
            </div>
          </AppBar>
        </div>
      ))}
    </div>
  ),
};

/**
 * Different height variants - compact, normal, tall
 */
export const Heights: Story = {
  render: () => (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-grey-600 mb-2">Compact (48px)</p>
        <AppBar height="compact">
          <div className="px-6 w-full flex items-center">
            <h2 className="text-base font-medium">Compact AppBar</h2>
          </div>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">Normal (64px) - Default</p>
        <AppBar height="normal">
          <div className="px-6 w-full flex items-center">
            <h2 className="text-lg font-medium">Normal AppBar</h2>
          </div>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">Tall (80px)</p>
        <AppBar height="tall">
          <div className="px-6 w-full flex items-center">
            <h2 className="text-xl font-medium">Tall AppBar</h2>
          </div>
        </AppBar>
      </div>
    </div>
  ),
};

/**
 * AppBar with blur backdrop effect (useful with transparent backgrounds)
 */
export const WithBlur: Story = {
  render: () => (
    <div className="relative h-96">
      {/* Background content */}
      <div className="absolute inset-0 overflow-hidden">
        <img
          src="https://images.unsplash.com/photo-1557683316-973673baf926?w=1200&h=400&fit=crop"
          alt="Background"
          className="w-full h-full object-cover"
        />
      </div>

      {/* AppBar with blur */}
      <AppBar position="sticky" color="transparent" blur elevation={0}>
        <div className="px-6 w-full flex items-center justify-between">
          <h1 className="text-xl font-bold text-white drop-shadow-lg">Glassmorphism AppBar</h1>
          <nav className="flex gap-2">
            <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
              Home
            </Button>
            <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
              Features
            </Button>
            <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
              Pricing
            </Button>
          </nav>
        </div>
      </AppBar>

      {/* Content below */}
      <div className="relative p-8 text-white">
        <h2 className="text-2xl font-bold mb-4">Welcome</h2>
        <p className="text-lg">Scroll to see the blur effect on the AppBar</p>
      </div>
    </div>
  ),
};

/**
 * Complete application header example
 */
export const CompleteHeader: Story = {
  render: () => (
    <AppBar position="sticky" color="primary" elevation={4}>
      <div className="px-6 w-full flex items-center justify-between">
        {/* Logo section */}
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-white rounded-lg flex items-center justify-center">
            <span className="text-primary-500 font-bold text-lg">Y</span>
          </div>
          <h1 className="text-xl font-bold">YAPPC</h1>
        </div>

        {/* Navigation */}
        <nav className="hidden md:flex gap-1">
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Dashboard
          </Button>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Projects
          </Button>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Team
          </Button>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Settings
          </Button>
        </nav>

        {/* Actions */}
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" className="border-white text-white hover:bg-white/20">
            Login
          </Button>
          <Button variant="solid" size="sm" className="bg-white text-primary-500 hover:bg-grey-100">
            Sign Up
          </Button>
        </div>
      </div>
    </AppBar>
  ),
};

/**
 * AppBar with search functionality
 */
export const WithSearch: Story = {
  render: () => (
    <AppBar position="sticky" color="default" elevation={2}>
      <div className="px-6 w-full flex items-center gap-4">
        {/* Logo */}
        <h1 className="text-xl font-bold text-primary-500 mr-4">Brand</h1>

        {/* Search bar */}
        <div className="flex-1 max-w-xl">
          <input
            type="search"
            placeholder="Search..."
            className="w-full px-4 py-2 rounded-lg border border-grey-300 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        {/* User actions */}
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
              />
            </svg>
          </Button>
          <div className="w-10 h-10 rounded-full bg-primary-500 flex items-center justify-center text-white font-medium">
            JD
          </div>
        </div>
      </div>
    </AppBar>
  ),
};

/**
 * Mobile-friendly responsive AppBar
 */
export const Responsive: Story = {
  render: () => (
    <AppBar position="sticky" color="primary" elevation={4}>
      <div className="px-4 md:px-6 w-full flex items-center justify-between">
        {/* Mobile menu button */}
        <button className="md:hidden p-2 text-white hover:bg-white/20 rounded-lg">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>

        {/* Logo */}
        <h1 className="text-lg md:text-xl font-bold">My App</h1>

        {/* Desktop navigation - hidden on mobile */}
        <nav className="hidden md:flex gap-2">
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Home
          </Button>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Features
          </Button>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            About
          </Button>
        </nav>

        {/* Actions */}
        <Button variant="outline" size="sm" className="border-white text-white hover:bg-white/20">
          Login
        </Button>
      </div>
    </AppBar>
  ),
};

/**
 * AppBar with tabs navigation
 */
export const WithTabs: Story = {
  render: () => (
    <div>
      <AppBar position="sticky" color="primary" elevation={0}>
        <div className="px-6 w-full flex items-center justify-between">
          <h1 className="text-xl font-bold">Dashboard</h1>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Settings
          </Button>
        </div>
      </AppBar>

      <AppBar position="sticky" color="default" elevation={2} height="compact">
        <div className="px-6 w-full flex gap-6">
          <button className="px-3 py-2 text-sm font-medium text-primary-500 border-b-2 border-primary-500">
            Overview
          </button>
          <button className="px-3 py-2 text-sm font-medium text-grey-600 hover:text-grey-900 border-b-2 border-transparent">
            Analytics
          </button>
          <button className="px-3 py-2 text-sm font-medium text-grey-600 hover:text-grey-900 border-b-2 border-transparent">
            Reports
          </button>
          <button className="px-3 py-2 text-sm font-medium text-grey-600 hover:text-grey-900 border-b-2 border-transparent">
            Notifications
          </button>
        </div>
      </AppBar>
    </div>
  ),
};

/**
 * Semantic HTML variants - header, nav, div
 */
export const SemanticVariants: Story = {
  render: () => (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-grey-600 mb-2">As &lt;header&gt; (default)</p>
        <AppBar component="header">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Header Element</h2>
          </div>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">As &lt;nav&gt;</p>
        <AppBar component="nav">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Nav Element</h2>
          </div>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">As &lt;div&gt;</p>
        <AppBar component="div">
          <div className="px-6 w-full">
            <h2 className="text-lg font-medium">Div Element</h2>
          </div>
        </AppBar>
      </div>
    </div>
  ),
};

/**
 * Dark mode support
 */
export const DarkMode: Story = {
  parameters: {
    backgrounds: { default: 'dark' },
  },
  render: () => (
    <div className="dark">
      <AppBar color="default" elevation={4}>
        <div className="px-6 w-full flex items-center justify-between">
          <h1 className="text-xl font-bold">Dark Mode AppBar</h1>
          <nav className="flex gap-2">
            <Button variant="ghost" size="sm">
              Home
            </Button>
            <Button variant="ghost" size="sm">
              About
            </Button>
            <Button variant="solid" size="sm">
              Contact
            </Button>
          </nav>
        </div>
      </AppBar>
    </div>
  ),
};
