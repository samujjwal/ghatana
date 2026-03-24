import { Accordion, AccordionItem } from './Accordion.baseui';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Accordion> = {
  title: 'Components/Advanced/Accordion',
  component: Accordion,
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component:
          'Accordion component for collapsible content sections. Built on Base UI Collapsible with smooth height transitions.',
      },
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Accordion>;

/**
 * Default Accordion with multiple items
 */
export const Default: Story = {
  render: () => (
    <Accordion>
      <AccordionItem value="item-1" title="What is Base UI?">
        Base UI is a library of headless UI components for building accessible web applications and design systems.
      </AccordionItem>
      <AccordionItem value="item-2" title="Why use Accordion?">
        Accordions help organize and hide complex content, improving readability and user experience.
      </AccordionItem>
      <AccordionItem value="item-3" title="How does it work?">
        Click on any section header to expand or collapse its content. Only one section can be open at a time by default.
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Accordion with one item expanded by default
 */
export const DefaultExpanded: Story = {
  render: () => (
    <Accordion>
      <AccordionItem value="item-1" title="Introduction" defaultOpen>
        This section is expanded by default using the defaultOpen prop.
      </AccordionItem>
      <AccordionItem value="item-2" title="Getting Started">
        This section starts collapsed.
      </AccordionItem>
      <AccordionItem value="item-3" title="Advanced Usage">
        This section also starts collapsed.
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Accordion with disabled items
 */
export const WithDisabledItems: Story = {
  render: () => (
    <Accordion>
      <AccordionItem value="item-1" title="Available Section">
        This section can be expanded and collapsed.
      </AccordionItem>
      <AccordionItem value="item-2" title="Disabled Section" disabled>
        This section is disabled and cannot be interacted with.
      </AccordionItem>
      <AccordionItem value="item-3" title="Another Available Section">
        This section is also available for interaction.
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Accordion with rich content
 */
export const RichContent: Story = {
  render: () => (
    <Accordion>
      <AccordionItem value="item-1" title="Features">
        <ul className="list-disc list-inside space-y-2">
          <li>Built on Base UI Collapsible primitive</li>
          <li>Smooth height transitions</li>
          <li>Accessible keyboard navigation</li>
          <li>Dark mode support</li>
          <li>Customizable icons</li>
        </ul>
      </AccordionItem>
      <AccordionItem value="item-2" title="Code Example">
        <pre className="bg-grey-100 dark:bg-grey-800 p-4 rounded-lg overflow-x-auto">
          <code>{`<Accordion>
  <AccordionItem value="1" title="Title">
    Content here
  </AccordionItem>
</Accordion>`}</code>
        </pre>
      </AccordionItem>
      <AccordionItem value="item-3" title="Additional Resources">
        <div className="space-y-2">
          <a href="#" className="block text-primary-500 hover:underline">
            Documentation
          </a>
          <a href="#" className="block text-primary-500 hover:underline">
            GitHub Repository
          </a>
          <a href="#" className="block text-primary-500 hover:underline">
            Component Examples
          </a>
        </div>
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Accordion with custom icons
 */
export const CustomIcons: Story = {
  render: () => (
    <Accordion>
      <AccordionItem
        value="item-1"
        title="Plus/Minus Icon"
        icon={
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
        }
      >
        This accordion uses a plus icon instead of the default chevron.
      </AccordionItem>
      <AccordionItem
        value="item-2"
        title="Arrow Icon"
        icon={
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        }
      >
        This accordion uses a right arrow icon.
      </AccordionItem>
      <AccordionItem
        value="item-3"
        title="Custom SVG"
        icon={
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
          </svg>
        }
      >
        This accordion uses a filled triangle icon.
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Accordion with long content that scrolls
 */
export const LongContent: Story = {
  render: () => (
    <Accordion>
      <AccordionItem value="item-1" title="Short Content">
        This is a brief section with minimal content.
      </AccordionItem>
      <AccordionItem value="item-2" title="Long Content">
        <div className="space-y-4">
          {Array.from({ length: 10 }).map((_, i) => (
            <p key={i}>
              This is paragraph {i + 1} of the long content section. The accordion panel will expand to accommodate all
              content with a smooth transition.
            </p>
          ))}
        </div>
      </AccordionItem>
      <AccordionItem value="item-3" title="Medium Content">
        <p>This section has a moderate amount of content.</p>
        <p className="mt-2">It demonstrates how different content lengths look in the same accordion.</p>
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Accordion as FAQ section
 */
export const FAQ: Story = {
  render: () => (
    <div className="max-w-3xl">
      <h2 className="text-2xl font-bold mb-4 text-grey-900 dark:text-white">Frequently Asked Questions</h2>
      <Accordion>
        <AccordionItem value="q1" title="How do I install the component library?">
          <p>You can install the library using npm or pnpm:</p>
          <pre className="bg-grey-100 dark:bg-grey-800 p-3 rounded mt-2">
            <code>pnpm add @ghatana/yappc-ui</code>
          </pre>
        </AccordionItem>
        <AccordionItem value="q2" title="Is the library TypeScript-compatible?">
          Yes, the library is written in TypeScript and includes full type definitions for all components.
        </AccordionItem>
        <AccordionItem value="q3" title="Does it support dark mode?">
          Yes, all components automatically adapt to dark mode using Tailwind CSS dark mode utilities.
        </AccordionItem>
        <AccordionItem value="q4" title="Can I customize the styling?">
          Absolutely! Components accept className props and use Tailwind CSS, so you can easily override styles or add
          custom classes.
        </AccordionItem>
        <AccordionItem value="q5" title="What about accessibility?">
          All components are built on Base UI primitives, ensuring WCAG 2.1 AA compliance with proper ARIA attributes,
          keyboard navigation, and screen reader support.
        </AccordionItem>
      </Accordion>
    </div>
  ),
};

/**
 * Nested accordions
 */
export const Nested: Story = {
  render: () => (
    <Accordion>
      <AccordionItem value="section-1" title="Section 1">
        <p className="mb-3">This section contains a nested accordion:</p>
        <Accordion>
          <AccordionItem value="sub-1-1" title="Subsection 1.1">
            Content for subsection 1.1
          </AccordionItem>
          <AccordionItem value="sub-1-2" title="Subsection 1.2">
            Content for subsection 1.2
          </AccordionItem>
        </Accordion>
      </AccordionItem>
      <AccordionItem value="section-2" title="Section 2">
        <p>Regular content without nesting.</p>
      </AccordionItem>
      <AccordionItem value="section-3" title="Section 3">
        <p className="mb-3">Another nested accordion example:</p>
        <Accordion>
          <AccordionItem value="sub-3-1" title="Subsection 3.1">
            Nested content 3.1
          </AccordionItem>
          <AccordionItem value="sub-3-2" title="Subsection 3.2">
            Nested content 3.2
          </AccordionItem>
          <AccordionItem value="sub-3-3" title="Subsection 3.3">
            Nested content 3.3
          </AccordionItem>
        </Accordion>
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Accordion with form inputs
 */
export const WithFormInputs: Story = {
  render: () => (
    <Accordion>
      <AccordionItem value="personal" title="Personal Information" defaultOpen>
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium mb-1">Name</label>
            <input
              type="text"
              className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="Enter your name"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Email</label>
            <input
              type="email"
              className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="Enter your email"
            />
          </div>
        </div>
      </AccordionItem>
      <AccordionItem value="address" title="Address">
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium mb-1">Street</label>
            <input
              type="text"
              className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="Enter street address"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium mb-1">City</label>
              <input
                type="text"
                className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
                placeholder="City"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">ZIP Code</label>
              <input
                type="text"
                className="w-full px-3 py-2 border border-grey-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
                placeholder="ZIP"
              />
            </div>
          </div>
        </div>
      </AccordionItem>
      <AccordionItem value="preferences" title="Preferences">
        <div className="space-y-3">
          <label className="flex items-center">
            <input type="checkbox" className="mr-2" />
            <span>Subscribe to newsletter</span>
          </label>
          <label className="flex items-center">
            <input type="checkbox" className="mr-2" />
            <span>Enable notifications</span>
          </label>
          <label className="flex items-center">
            <input type="checkbox" className="mr-2" />
            <span>Share data for analytics</span>
          </label>
        </div>
      </AccordionItem>
    </Accordion>
  ),
};

/**
 * Minimal styling
 */
export const Minimal: Story = {
  render: () => (
    <div className="border-0">
      <Accordion className="border-0 shadow-none">
        <AccordionItem value="item-1" title="Section 1">
          Content for section 1
        </AccordionItem>
        <AccordionItem value="item-2" title="Section 2">
          Content for section 2
        </AccordionItem>
        <AccordionItem value="item-3" title="Section 3">
          Content for section 3
        </AccordionItem>
      </Accordion>
    </div>
  ),
};

/**
 * Keyboard accessibility demonstration
 */
export const KeyboardAccessible: Story = {
  render: () => (
    <div>
      <p className="mb-4 text-sm text-grey-600 dark:text-grey-400">
        Try navigating with keyboard: Tab to focus headers, Enter/Space to expand/collapse
      </p>
      <Accordion>
        <AccordionItem value="item-1" title="Keyboard Navigation">
          <ul className="list-disc list-inside space-y-1">
            <li>Tab - Focus next accordion header</li>
            <li>Shift + Tab - Focus previous header</li>
            <li>Enter or Space - Toggle expand/collapse</li>
            <li>All interactions are screen reader accessible</li>
          </ul>
        </AccordionItem>
        <AccordionItem value="item-2" title="ARIA Attributes">
          This component uses proper ARIA attributes for accessibility, including aria-expanded and semantic button
          elements.
        </AccordionItem>
        <AccordionItem value="item-3" title="Focus Management">
          Focus indicators are clearly visible and keyboard navigation follows logical tab order.
        </AccordionItem>
      </Accordion>
    </div>
  ),
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  parameters: {
    backgrounds: { default: 'dark' },
  },
  render: () => (
    <div className="dark">
      <Accordion>
        <AccordionItem value="item-1" title="Dark Mode Support">
          This accordion automatically adapts to dark mode with appropriate colors for backgrounds, text, and borders.
        </AccordionItem>
        <AccordionItem value="item-2" title="Theme Integration">
          All colors use theme tokens that switch seamlessly between light and dark modes.
        </AccordionItem>
        <AccordionItem value="item-3" title="Accessibility">
          Color contrast ratios are maintained in both light and dark modes for WCAG compliance.
        </AccordionItem>
      </Accordion>
    </div>
  ),
};
