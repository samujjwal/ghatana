import { Tooltip } from './Tooltip.baseui';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Tooltip> = {
  title: 'Components/Tooltip',
  component: Tooltip,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Tooltip>;

/**
 * Default tooltip on hover
 */
export const Default: Story = {
  render: () => (
    <Tooltip content="This is a helpful tooltip">
      <Button>Hover me</Button>
    </Tooltip>
  ),
};

/**
 * Tooltip placements: top, bottom, left, right
 */
export const Placements: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-8 p-12">
      <div />
      <Tooltip content="Tooltip on top" placement="top">
        <Button>Top</Button>
      </Tooltip>
      <div />
      
      <Tooltip content="Tooltip on left" placement="left">
        <Button>Left</Button>
      </Tooltip>
      <div />
      <Tooltip content="Tooltip on right" placement="right">
        <Button>Right</Button>
      </Tooltip>
      
      <div />
      <Tooltip content="Tooltip on bottom" placement="bottom">
        <Button>Bottom</Button>
      </Tooltip>
      <div />
    </div>
  ),
};

/**
 * Tooltip with different delay times
 */
export const Delays: Story = {
  render: () => (
    <div className="flex gap-4">
      <Tooltip content="No delay" delay={0}>
        <Button>No Delay</Button>
      </Tooltip>
      
      <Tooltip content="200ms delay (default)" delay={200}>
        <Button>Default (200ms)</Button>
      </Tooltip>
      
      <Tooltip content="500ms delay" delay={500}>
        <Button>Slow (500ms)</Button>
      </Tooltip>
      
      <Tooltip content="1000ms delay" delay={1000}>
        <Button>Very Slow (1s)</Button>
      </Tooltip>
    </div>
  ),
};

/**
 * Tooltip with and without arrow
 */
export const Arrow: Story = {
  render: () => (
    <div className="flex gap-4">
      <Tooltip content="Tooltip with arrow" showArrow={true}>
        <Button>With Arrow</Button>
      </Tooltip>
      
      <Tooltip content="Tooltip without arrow" showArrow={false}>
        <Button>No Arrow</Button>
      </Tooltip>
    </div>
  ),
};

/**
 * Tooltip with long content
 */
export const LongContent: Story = {
  render: () => (
    <Tooltip
      content="This is a much longer tooltip that demonstrates how the tooltip component handles text that wraps to multiple lines. The max-width is set to 300px."
      placement="top"
    >
      <Button>Hover for long content</Button>
    </Tooltip>
  ),
};

/**
 * Tooltip with rich content
 */
export const RichContent: Story = {
  render: () => (
    <Tooltip
      content={
        <div className="space-y-1">
          <div className="font-semibold">Keyboard Shortcut</div>
          <div className="text-xs opacity-90">Press Cmd+K to open</div>
        </div>
      }
      placement="right"
    >
      <Button>Rich Content</Button>
    </Tooltip>
  ),
};

/**
 * Tooltip on different trigger elements
 */
export const DifferentTriggers: Story = {
  render: () => (
    <div className="flex flex-col gap-4">
      <Tooltip content="Tooltip on a button">
        <Button>Button Trigger</Button>
      </Tooltip>
      
      <Tooltip content="Tooltip on a link">
        <a href="#" className="text-primary-600 underline">
          Link Trigger
        </a>
      </Tooltip>
      
      <Tooltip content="Tooltip on an icon">
        <button className="w-8 h-8 rounded-full bg-grey-200 flex items-center justify-center hover:bg-grey-300">
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
        </button>
      </Tooltip>
      
      <Tooltip content="Tooltip on text">
        <span className="border-b border-dashed border-grey-400 cursor-help">
          Hover over this text
        </span>
      </Tooltip>
    </div>
  ),
};

/**
 * Tooltip with custom styling
 */
export const CustomStyling: Story = {
  render: () => (
    <div className="flex gap-4">
      <Tooltip
        content="Success message"
        className="bg-success-600 text-white"
      >
        <Button color="success">Success Tooltip</Button>
      </Tooltip>
      
      <Tooltip
        content="Warning message"
        className="bg-warning-500 text-white"
      >
        <Button color="warning">Warning Tooltip</Button>
      </Tooltip>
      
      <Tooltip
        content="Error message"
        className="bg-error-600 text-white"
      >
        <Button color="error">Error Tooltip</Button>
      </Tooltip>
    </div>
  ),
};

/**
 * Interactive tooltips showing in a form
 */
export const FormExample: Story = {
  render: () => (
    <div className="w-full max-w-md space-y-4">
      <div>
        <label className="flex items-center gap-2 text-sm font-medium mb-1">
          Username
          <Tooltip content="Your username must be unique and between 3-20 characters">
            <svg className="w-4 h-4 text-grey-500" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
            </svg>
          </Tooltip>
        </label>
        <input type="text" className="w-full px-3 py-2 border border-grey-300 rounded" />
      </div>
      
      <div>
        <label className="flex items-center gap-2 text-sm font-medium mb-1">
          Email
          <Tooltip content="We'll never share your email with anyone else">
            <svg className="w-4 h-4 text-grey-500" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
            </svg>
          </Tooltip>
        </label>
        <input type="email" className="w-full px-3 py-2 border border-grey-300 rounded" />
      </div>
      
      <div>
        <label className="flex items-center gap-2 text-sm font-medium mb-1">
          Password
          <Tooltip 
            content={
              <div className="space-y-1">
                <div className="font-semibold">Password Requirements:</div>
                <ul className="text-xs space-y-0.5 ml-4 list-disc">
                  <li>At least 8 characters</li>
                  <li>One uppercase letter</li>
                  <li>One number</li>
                  <li>One special character</li>
                </ul>
              </div>
            }
          >
            <svg className="w-4 h-4 text-grey-500" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
            </svg>
          </Tooltip>
        </label>
        <input type="password" className="w-full px-3 py-2 border border-grey-300 rounded" />
      </div>
    </div>
  ),
};

/**
 * Edge case: tooltip near viewport edges
 */
export const ViewportEdges: Story = {
  render: () => (
    <div className="w-full h-[400px] relative">
      <div className="absolute top-2 left-2">
        <Tooltip content="Tooltip near top-left corner" placement="bottom">
          <Button size="sm">Top Left</Button>
        </Tooltip>
      </div>
      
      <div className="absolute top-2 right-2">
        <Tooltip content="Tooltip near top-right corner" placement="bottom">
          <Button size="sm">Top Right</Button>
        </Tooltip>
      </div>
      
      <div className="absolute bottom-2 left-2">
        <Tooltip content="Tooltip near bottom-left corner" placement="top">
          <Button size="sm">Bottom Left</Button>
        </Tooltip>
      </div>
      
      <div className="absolute bottom-2 right-2">
        <Tooltip content="Tooltip near bottom-right corner" placement="top">
          <Button size="sm">Bottom Right</Button>
        </Tooltip>
      </div>
      
      <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2">
        <Tooltip content="Tooltip in center (has room on all sides)">
          <Button>Center</Button>
        </Tooltip>
      </div>
    </div>
  ),
};

/**
 * Keyboard accessibility
 */
export const KeyboardAccessibility: Story = {
  render: () => (
    <div className="space-y-4">
      <div className="text-sm text-grey-600 bg-grey-50 p-4 rounded">
        <strong>Keyboard controls:</strong>
        <ul className="list-disc list-inside mt-2">
          <li>TAB - Focus the trigger element</li>
          <li>ESC - Close the tooltip (when focused)</li>
          <li>Mouse leave - Auto close</li>
        </ul>
      </div>
      
      <div className="flex gap-4">
        <Tooltip content="Press ESC to close this tooltip">
          <Button>Focus me with TAB</Button>
        </Tooltip>
        
        <Tooltip content="This tooltip also responds to keyboard">
          <Button>Another focusable element</Button>
        </Tooltip>
      </div>
    </div>
  ),
};
