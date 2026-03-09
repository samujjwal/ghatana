/**
 * Container Component Stories (Tailwind CSS)
 */

import { Container } from './Container.tailwind';
import { Box } from '../Box/Box.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Layout/Container (Tailwind)',
  component: Container,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'A responsive container component with max-width constraints that centers content horizontally. Built with Tailwind CSS container utilities.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    maxWidth: {
      control: 'select',
      options: ['xs', 'sm', 'md', 'lg', 'xl', '2xl', '3xl', '4xl', '5xl', '6xl', '7xl', 'full', false],
      description: 'Maximum width constraint',
    },
    centered: {
      control: 'boolean',
      description: 'Center the container horizontally',
    },
    padding: {
      control: 'boolean',
      description: 'Add horizontal padding',
    },
  },
} satisfies Meta<typeof Container>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default container (max-w-lg, centered, with padding)
 */
export const Default: Story = {
  render: () => (
    <Box bg="bg-grey-100" className="min-h-screen">
      <Container>
        <Box p="p-6" bg="bg-white" shadow="shadow" rounded="rounded-lg">
          <h2 className="mb-4 text-2xl font-bold text-grey-900">Default Container</h2>
          <p className="text-grey-700">
            This container has a max-width of 'lg' (512px), is centered, and includes horizontal padding.
            It's perfect for forms, articles, and other focused content.
          </p>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * All size variants
 */
export const Sizes: Story = {
  render: () => (
    <Box bg="bg-grey-100" p="p-8" className="space-y-8">
      <Container maxWidth="xs">
        <Box p="p-4" bg="bg-primary-100" rounded="rounded" className="text-center">
          <strong>xs</strong> - 320px max-width
        </Box>
      </Container>
      
      <Container maxWidth="sm">
        <Box p="p-4" bg="bg-primary-100" rounded="rounded" className="text-center">
          <strong>sm</strong> - 384px max-width
        </Box>
      </Container>
      
      <Container maxWidth="md">
        <Box p="p-4" bg="bg-secondary-100" rounded="rounded" className="text-center">
          <strong>md</strong> - 448px max-width
        </Box>
      </Container>
      
      <Container maxWidth="lg">
        <Box p="p-4" bg="bg-secondary-100" rounded="rounded" className="text-center">
          <strong>lg</strong> - 512px max-width (default)
        </Box>
      </Container>
      
      <Container maxWidth="xl">
        <Box p="p-4" bg="bg-success-100" rounded="rounded" className="text-center">
          <strong>xl</strong> - 576px max-width
        </Box>
      </Container>
      
      <Container maxWidth="2xl">
        <Box p="p-4" bg="bg-success-100" rounded="rounded" className="text-center">
          <strong>2xl</strong> - 672px max-width
        </Box>
      </Container>
      
      <Container maxWidth="3xl">
        <Box p="p-4" bg="bg-warning-100" rounded="rounded" className="text-center">
          <strong>3xl</strong> - 768px max-width
        </Box>
      </Container>
      
      <Container maxWidth="4xl">
        <Box p="p-4" bg="bg-warning-100" rounded="rounded" className="text-center">
          <strong>4xl</strong> - 896px max-width
        </Box>
      </Container>
      
      <Container maxWidth="5xl">
        <Box p="p-4" bg="bg-error-100" rounded="rounded" className="text-center">
          <strong>5xl</strong> - 1024px max-width
        </Box>
      </Container>
      
      <Container maxWidth="6xl">
        <Box p="p-4" bg="bg-error-100" rounded="rounded" className="text-center">
          <strong>6xl</strong> - 1152px max-width
        </Box>
      </Container>
      
      <Container maxWidth="7xl">
        <Box p="p-4" bg="bg-grey-300" rounded="rounded" className="text-center">
          <strong>7xl</strong> - 1280px max-width
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Without padding
 */
export const WithoutPadding: Story = {
  render: () => (
    <Box bg="bg-grey-100" className="min-h-screen">
      <Container maxWidth="2xl" padding={false}>
        <Box bg="bg-white" shadow="shadow-lg">
          <Box p="p-6" border="border-b border-grey-200">
            <h2 className="text-2xl font-bold text-grey-900">Container Without Padding</h2>
          </Box>
          <Box p="p-6">
            <p className="text-grey-700">
              This container has no horizontal padding, allowing content to extend to the edges.
              Useful for full-bleed images or layouts where you want precise control.
            </p>
          </Box>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Full width container
 */
export const FullWidth: Story = {
  render: () => (
    <Box bg="bg-grey-100" p="p-8">
      <Container maxWidth="full">
        <Box p="p-6" bg="bg-primary-500" color="text-white" rounded="rounded-lg" className="text-center">
          <h2 className="mb-2 text-2xl font-bold">Full Width Container</h2>
          <p>This container spans the full width of its parent with padding.</p>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Not centered
 */
export const NotCentered: Story = {
  render: () => (
    <Box bg="bg-grey-100" p="p-8">
      <Container maxWidth="lg" centered={false}>
        <Box p="p-6" bg="bg-secondary-100" rounded="rounded-lg">
          <h3 className="mb-2 text-lg font-semibold text-grey-900">Left-aligned Container</h3>
          <p className="text-grey-700">
            This container is not centered and will align to the left edge.
          </p>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Article layout example
 */
export const ArticleLayout: Story = {
  render: () => (
    <Box bg="bg-grey-50" className="min-h-screen">
      {/* Hero section - full width */}
      <Container maxWidth="full" padding={false}>
        <Box h="h-96" bg="bg-gradient-to-r from-primary-600 to-secondary-600" className="flex items-center justify-center">
          <Container maxWidth="4xl">
            <Box className="text-center text-white">
              <h1 className="mb-4 text-5xl font-bold">Article Title</h1>
              <p className="text-xl opacity-90">A compelling subtitle that draws readers in</p>
            </Box>
          </Container>
        </Box>
      </Container>
      
      {/* Article content - constrained width */}
      <Container maxWidth="2xl" className="py-12">
        <Box bg="bg-white" p="p-8" shadow="shadow-lg" rounded="rounded-xl">
          <div className="space-y-4 text-grey-700">
            <p>
              Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor
              incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud
              exercitation ullamco laboris.
            </p>
            <p>
              Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu
              fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in
              culpa qui officia deserunt mollit anim id est laborum.
            </p>
            <p>
              Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque
              laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis.
            </p>
          </div>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Multi-section page
 */
export const MultiSectionPage: Story = {
  render: () => (
    <div>
      {/* Header - full width bg, constrained content */}
      <Box bg="bg-grey-900" color="text-white">
        <Container maxWidth="6xl" className="py-4">
          <Box display="flex" className="items-center justify-between">
            <Box as="h1" className="text-xl font-bold">Brand</Box>
            <Box as="nav" display="flex" className="gap-6">
              <a href="#" className="hover:text-primary-300">Home</a>
              <a href="#" className="hover:text-primary-300">About</a>
              <a href="#" className="hover:text-primary-300">Contact</a>
            </Box>
          </Box>
        </Container>
      </Box>
      
      {/* Hero section */}
      <Box bg="bg-primary-600" color="text-white" className="py-20">
        <Container maxWidth="4xl" className="text-center">
          <h2 className="mb-4 text-4xl font-bold">Welcome to Our Platform</h2>
          <p className="mb-8 text-xl">Build amazing things with our tools</p>
          <button className="rounded-lg bg-white px-8 py-3 font-semibold text-primary-600 hover:bg-grey-100">
            Get Started
          </button>
        </Container>
      </Box>
      
      {/* Features section */}
      <Box bg="bg-white" className="py-16">
        <Container maxWidth="6xl">
          <h3 className="mb-8 text-center text-3xl font-bold text-grey-900">Features</h3>
          <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <Box key={i} p="p-6" bg="bg-grey-50" rounded="rounded-lg">
                <h4 className="mb-2 text-xl font-semibold text-grey-900">Feature {i}</h4>
                <p className="text-grey-600">
                  Description of this amazing feature and why it matters to users.
                </p>
              </Box>
            ))}
          </div>
        </Container>
      </Box>
      
      {/* CTA section */}
      <Box bg="bg-grey-900" color="text-white" className="py-16">
        <Container maxWidth="3xl" className="text-center">
          <h3 className="mb-4 text-3xl font-bold">Ready to get started?</h3>
          <p className="mb-8 text-lg">Join thousands of users already building with us.</p>
          <button className="rounded-lg bg-primary-600 px-8 py-3 font-semibold hover:bg-primary-700">
            Sign Up Free
          </button>
        </Container>
      </Box>
      
      {/* Footer */}
      <Box bg="bg-grey-800" color="text-grey-300" className="py-8">
        <Container maxWidth="6xl">
          <Box display="flex" className="items-center justify-between">
            <p>&copy; 2024 Company Name. All rights reserved.</p>
            <Box display="flex" className="gap-4">
              <a href="#" className="hover:text-white">Privacy</a>
              <a href="#" className="hover:text-white">Terms</a>
            </Box>
          </Box>
        </Container>
      </Box>
    </div>
  ),
};

/**
 * Nested containers example
 */
export const NestedContainers: Story = {
  render: () => (
    <Box bg="bg-grey-100" className="min-h-screen py-8">
      <Container maxWidth="7xl">
        <Box p="p-8" bg="bg-white" shadow="shadow-lg" rounded="rounded-xl">
          <h2 className="mb-6 text-2xl font-bold text-grey-900">Outer Container (7xl)</h2>
          
          <Container maxWidth="4xl">
            <Box p="p-6" bg="bg-primary-50" rounded="rounded-lg" className="mb-6">
              <h3 className="mb-2 text-xl font-semibold text-grey-900">Inner Container (4xl)</h3>
              <p className="text-grey-700">
                Containers can be nested to create different content widths within the same layout.
              </p>
            </Box>
          </Container>
          
          <Container maxWidth="2xl">
            <Box p="p-6" bg="bg-secondary-50" rounded="rounded-lg">
              <h3 className="mb-2 text-xl font-semibold text-grey-900">Narrow Container (2xl)</h3>
              <p className="text-grey-700">
                This narrower container is perfect for focused content like forms or text.
              </p>
            </Box>
          </Container>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Form layout example
 */
export const FormLayout: Story = {
  render: () => (
    <Box bg="bg-grey-100" className="min-h-screen py-12">
      <Container maxWidth="md">
        <Box p="p-8" bg="bg-white" shadow="shadow-xl" rounded="rounded-xl">
          <h2 className="mb-6 text-2xl font-bold text-grey-900">Create Account</h2>
          
          <form className="space-y-6">
            <div>
              <label className="mb-1 block text-sm font-medium text-grey-700">Full Name</label>
              <input
                type="text"
                className="w-full rounded-md border border-grey-300 px-4 py-2 focus:border-primary-500 focus:ring-2 focus:ring-primary-500"
                placeholder="John Doe"
              />
            </div>
            
            <div>
              <label className="mb-1 block text-sm font-medium text-grey-700">Email</label>
              <input
                type="email"
                className="w-full rounded-md border border-grey-300 px-4 py-2 focus:border-primary-500 focus:ring-2 focus:ring-primary-500"
                placeholder="john@example.com"
              />
            </div>
            
            <div>
              <label className="mb-1 block text-sm font-medium text-grey-700">Password</label>
              <input
                type="password"
                className="w-full rounded-md border border-grey-300 px-4 py-2 focus:border-primary-500 focus:ring-2 focus:ring-primary-500"
                placeholder="••••••••"
              />
            </div>
            
            <div>
              <label className="mb-1 block text-sm font-medium text-grey-700">Bio</label>
              <textarea
                rows={4}
                className="w-full rounded-md border border-grey-300 px-4 py-2 focus:border-primary-500 focus:ring-2 focus:ring-primary-500"
                placeholder="Tell us about yourself..."
              />
            </div>
            
            <div className="flex items-center gap-2">
              <input type="checkbox" id="terms" className="h-4 w-4 rounded border-grey-300 text-primary-600" />
              <label htmlFor="terms" className="text-sm text-grey-700">
                I agree to the terms and conditions
              </label>
            </div>
            
            <button
              type="submit"
              className="w-full rounded-lg bg-primary-600 py-3 font-semibold text-white hover:bg-primary-700"
            >
              Create Account
            </button>
          </form>
        </Box>
      </Container>
    </Box>
  ),
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    maxWidth: 'lg',
    centered: true,
    padding: true,
  },
  render: (args) => (
    <Box bg="bg-grey-100" className="min-h-screen py-8">
      <Container {...args}>
        <Box p="p-6" bg="bg-white" shadow="shadow" rounded="rounded-lg">
          <h3 className="mb-2 text-lg font-semibold text-grey-900">Container Playground</h3>
          <p className="text-grey-700">
            Adjust the controls to see how the container behaves with different settings.
          </p>
        </Box>
      </Container>
    </Box>
  ),
};
