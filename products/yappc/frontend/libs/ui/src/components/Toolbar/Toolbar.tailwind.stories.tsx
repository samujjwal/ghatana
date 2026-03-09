import { Toolbar } from './Toolbar.tailwind';
import { AppBar } from '../AppBar';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Toolbar> = {
  title: 'Components/Layout/Toolbar',
  component: Toolbar,
  tags: ['autodocs'],
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'Toolbar is a flex container component for organizing AppBar content and actions. Provides consistent spacing and alignment.',
      },
    },
  },
  argTypes: {
    justify: {
      control: 'select',
      options: ['start', 'center', 'end', 'between', 'around', 'evenly'],
      description: 'Content justification',
    },
    gap: {
      control: 'select',
      options: ['none', 'xs', 'sm', 'md', 'lg', 'xl'],
      description: 'Gap size between items',
    },
    padding: {
      control: 'select',
      options: ['none', 'sm', 'md', 'lg'],
      description: 'Padding around content',
    },
    centerItems: {
      control: 'boolean',
      description: 'Center items vertically',
    },
    fullWidth: {
      control: 'boolean',
      description: 'Take full width',
    },
    component: {
      control: 'select',
      options: ['div', 'nav', 'section'],
      description: 'HTML element to render as',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Toolbar>;

/**
 * Default Toolbar with basic content
 */
export const Default: Story = {
  args: {
    children: (
      <>
        <h1 className="text-xl font-semibold">My Application</h1>
        <Button variant="solid" size="sm">
          Action
        </Button>
      </>
    ),
  },
};

/**
 * Different justify (alignment) options
 */
export const JustifyOptions: Story = {
  render: () => (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-grey-600 mb-2">Start (default)</p>
        <AppBar>
          <Toolbar justify="start">
            <span className="font-medium">Logo</span>
            <Button variant="ghost" size="sm">
              Home
            </Button>
            <Button variant="ghost" size="sm">
              About
            </Button>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">Center</p>
        <AppBar>
          <Toolbar justify="center">
            <span className="font-medium">Logo</span>
            <Button variant="ghost" size="sm">
              Home
            </Button>
            <Button variant="ghost" size="sm">
              About
            </Button>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">End</p>
        <AppBar>
          <Toolbar justify="end">
            <span className="font-medium">Logo</span>
            <Button variant="ghost" size="sm">
              Home
            </Button>
            <Button variant="ghost" size="sm">
              About
            </Button>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">Between (space-between)</p>
        <AppBar>
          <Toolbar justify="between">
            <span className="font-medium">Logo</span>
            <div className="flex gap-2">
              <Button variant="ghost" size="sm">
                Home
              </Button>
              <Button variant="ghost" size="sm">
                About
              </Button>
            </div>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">Around (space-around)</p>
        <AppBar>
          <Toolbar justify="around">
            <span className="font-medium">Logo</span>
            <Button variant="ghost" size="sm">
              Home
            </Button>
            <Button variant="ghost" size="sm">
              About
            </Button>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">Evenly (space-evenly)</p>
        <AppBar>
          <Toolbar justify="evenly">
            <span className="font-medium">Logo</span>
            <Button variant="ghost" size="sm">
              Home
            </Button>
            <Button variant="ghost" size="sm">
              About
            </Button>
          </Toolbar>
        </AppBar>
      </div>
    </div>
  ),
};

/**
 * Different gap sizes between items
 */
export const GapSizes: Story = {
  render: () => (
    <div className="space-y-4">
      {(['none', 'xs', 'sm', 'md', 'lg', 'xl'] as const).map((gap) => (
        <div key={gap}>
          <p className="text-sm text-grey-600 mb-2">Gap: {gap}</p>
          <AppBar>
            <Toolbar gap={gap}>
              <Button variant="solid" size="sm">
                Button 1
              </Button>
              <Button variant="solid" size="sm">
                Button 2
              </Button>
              <Button variant="solid" size="sm">
                Button 3
              </Button>
            </Toolbar>
          </AppBar>
        </div>
      ))}
    </div>
  ),
};

/**
 * Different padding options
 */
export const PaddingOptions: Story = {
  render: () => (
    <div className="space-y-4">
      {(['none', 'sm', 'md', 'lg'] as const).map((padding) => (
        <div key={padding}>
          <p className="text-sm text-grey-600 mb-2">Padding: {padding}</p>
          <AppBar>
            <Toolbar padding={padding}>
              <span className="font-medium">Logo</span>
              <Button variant="ghost" size="sm">
                Home
              </Button>
            </Toolbar>
          </AppBar>
        </div>
      ))}
    </div>
  ),
};

/**
 * Typical AppBar with Toolbar pattern
 */
export const WithAppBar: Story = {
  render: () => (
    <AppBar position="sticky" color="primary" elevation={4}>
      <Toolbar justify="between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-white rounded-lg flex items-center justify-center">
            <span className="text-primary-500 font-bold">Y</span>
          </div>
          <h1 className="text-xl font-bold text-white">YAPPC</h1>
        </div>

        <nav className="flex gap-1">
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Dashboard
          </Button>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Projects
          </Button>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Settings
          </Button>
        </nav>

        <div className="flex gap-2">
          <Button variant="outline" size="sm" className="border-white text-white hover:bg-white/20">
            Login
          </Button>
          <Button variant="solid" size="sm" className="bg-white text-primary-500">
            Sign Up
          </Button>
        </div>
      </Toolbar>
    </AppBar>
  ),
};

/**
 * Toolbar with search bar
 */
export const WithSearch: Story = {
  render: () => (
    <AppBar color="default" elevation={2}>
      <Toolbar justify="between" gap="lg">
        <h1 className="text-xl font-bold text-primary-500">Brand</h1>

        <div className="flex-1 max-w-2xl">
          <input
            type="search"
            placeholder="Search..."
            className="w-full px-4 py-2 rounded-lg border border-grey-300 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

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
      </Toolbar>
    </AppBar>
  ),
};

/**
 * Toolbar with icon buttons
 */
export const WithIcons: Story = {
  render: () => (
    <AppBar color="default" elevation={2}>
      <Toolbar justify="between">
        <h1 className="text-xl font-bold">Dashboard</h1>

        <div className="flex gap-1">
          <Button variant="ghost" size="sm">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
            </svg>
          </Button>
          <Button variant="ghost" size="sm">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </Button>
          <Button variant="ghost" size="sm">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </Button>
        </div>
      </Toolbar>
    </AppBar>
  ),
};

/**
 * Multiple Toolbars stacked (e.g., main header + tabs)
 */
export const MultipleToolbars: Story = {
  render: () => (
    <div>
      <AppBar position="sticky" color="primary" elevation={0}>
        <Toolbar justify="between">
          <h1 className="text-xl font-bold text-white">Dashboard</h1>
          <Button variant="ghost" size="sm" className="text-white hover:bg-white/20">
            Settings
          </Button>
        </Toolbar>
      </AppBar>

      <AppBar position="sticky" color="default" elevation={2} height="compact">
        <Toolbar gap="sm" padding="sm">
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
            Settings
          </button>
        </Toolbar>
      </AppBar>
    </div>
  ),
};

/**
 * Responsive Toolbar that adapts to mobile
 */
export const Responsive: Story = {
  render: () => (
    <AppBar position="sticky" color="primary" elevation={4}>
      <Toolbar justify="between" className="px-4 md:px-6">
        {/* Mobile menu button */}
        <button className="md:hidden p-2 text-white hover:bg-white/20 rounded-lg">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>

        {/* Logo */}
        <h1 className="text-lg md:text-xl font-bold text-white">My App</h1>

        {/* Desktop nav - hidden on mobile */}
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

        {/* Action button */}
        <Button variant="outline" size="sm" className="border-white text-white hover:bg-white/20">
          Login
        </Button>
      </Toolbar>
    </AppBar>
  ),
};

/**
 * Toolbar with breadcrumbs
 */
export const WithBreadcrumbs: Story = {
  render: () => (
    <AppBar color="default" elevation={1}>
      <Toolbar>
        <nav className="flex items-center gap-2 text-sm">
          <a href="#" className="text-primary-500 hover:underline">
            Home
          </a>
          <span className="text-grey-400">/</span>
          <a href="#" className="text-primary-500 hover:underline">
            Projects
          </a>
          <span className="text-grey-400">/</span>
          <span className="text-grey-700 font-medium">Current Project</span>
        </nav>
      </Toolbar>
    </AppBar>
  ),
};

/**
 * Toolbar without vertical centering
 */
export const WithoutCentering: Story = {
  render: () => (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-grey-600 mb-2">With centering (default)</p>
        <AppBar>
          <Toolbar centerItems>
            <h1 className="text-2xl font-bold">Large Title</h1>
            <Button variant="solid" size="sm">
              Small Button
            </Button>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">Without centering (items at top)</p>
        <AppBar>
          <Toolbar centerItems={false}>
            <h1 className="text-2xl font-bold">Large Title</h1>
            <Button variant="solid" size="sm">
              Small Button
            </Button>
          </Toolbar>
        </AppBar>
      </div>
    </div>
  ),
};

/**
 * Semantic HTML variants
 */
export const SemanticVariants: Story = {
  render: () => (
    <div className="space-y-4">
      <div>
        <p className="text-sm text-grey-600 mb-2">As &lt;div&gt; (default)</p>
        <AppBar>
          <Toolbar component="div">
            <h2 className="text-lg font-medium">Div Element</h2>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">As &lt;nav&gt;</p>
        <AppBar>
          <Toolbar component="nav">
            <Button variant="ghost" size="sm">
              Home
            </Button>
            <Button variant="ghost" size="sm">
              About
            </Button>
            <Button variant="ghost" size="sm">
              Contact
            </Button>
          </Toolbar>
        </AppBar>
      </div>

      <div>
        <p className="text-sm text-grey-600 mb-2">As &lt;section&gt;</p>
        <AppBar>
          <Toolbar component="section">
            <h2 className="text-lg font-medium">Section Element</h2>
          </Toolbar>
        </AppBar>
      </div>
    </div>
  ),
};
