import type { Meta, StoryObj } from '@storybook/react';
import { useState } from 'react';

/**
 * Accessibility Patterns Story
 * 
 * Demonstrates accessibility best practices and patterns used in YAPPC.
 * These patterns should be followed for WCAG 2.1 AA compliance.
 */

// Mock component for demonstration
const AccessibilityDemo = () => <div />;

const meta: Meta<typeof AccessibilityDemo> = {
    title: 'Design System/Accessibility Patterns',
    component: AccessibilityDemo,
    parameters: {
        layout: 'padded',
        docs: {
            description: {
                component: `
# Accessibility Patterns

This documentation covers the accessibility patterns used throughout the YAPPC application.
All components should follow these patterns to ensure WCAG 2.1 AA compliance.

## Quick Reference

| Pattern | Usage | Required |
|---------|-------|----------|
| Focus Rings | All interactive elements | ✅ |
| ARIA Labels | Buttons without text, icons | ✅ |
| Skip Links | Page layouts | ✅ |
| Live Regions | Dynamic content updates | ✅ |
| Landmarks | Page structure | ✅ |
| Keyboard Navigation | All interactions | ✅ |
                `,
            },
        },
    },
    tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof AccessibilityDemo>;

/**
 * Focus Ring Patterns
 * 
 * All interactive elements MUST have visible focus indicators.
 */
export const FocusRings: Story = {
    render: () => (
        <div className="space-y-8">
            <h2 className="text-xl font-semibold">Focus Ring Patterns</h2>
            <p className="text-sm text-gray-600">
                Tab through these elements to see the focus indicators.
            </p>

            <div className="space-y-4">
                <div>
                    <h3 className="text-sm font-medium mb-2">Standard Focus Ring</h3>
                    <button className="px-4 py-2 bg-blue-600 text-white rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2">
                        Button with Focus Ring
                    </button>
                    <pre className="mt-2 p-2 bg-gray-100 text-xs rounded">
                        {`className="focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"`}
                    </pre>
                </div>

                <div>
                    <h3 className="text-sm font-medium mb-2">Focus Visible (keyboard only)</h3>
                    <button className="px-4 py-2 bg-gray-200 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2">
                        Focus Visible Button
                    </button>
                    <pre className="mt-2 p-2 bg-gray-100 text-xs rounded">
                        {`className="focus-visible:ring-2 focus-visible:ring-blue-500"`}
                    </pre>
                </div>

                <div>
                    <h3 className="text-sm font-medium mb-2">Input Focus</h3>
                    <input
                        type="text"
                        placeholder="Text input"
                        className="px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    />
                </div>
            </div>
        </div>
    ),
};

/**
 * ARIA Labels
 * 
 * Proper labeling for screen readers.
 */
export const AriaLabels: Story = {
    render: () => (
        <div className="space-y-8">
            <h2 className="text-xl font-semibold">ARIA Labels</h2>

            <div className="space-y-4">
                <div>
                    <h3 className="text-sm font-medium mb-2">Icon Buttons</h3>
                    <div className="flex gap-2">
                        <button
                            aria-label="Delete item"
                            className="p-2 rounded-lg hover:bg-gray-100"
                        >
                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                        </button>
                        <button
                            aria-label="Edit item"
                            className="p-2 rounded-lg hover:bg-gray-100"
                        >
                            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                        </button>
                    </div>
                    <pre className="mt-2 p-2 bg-gray-100 text-xs rounded">
                        {`<button aria-label="Delete item">
  <TrashIcon aria-hidden="true" />
</button>`}
                    </pre>
                </div>

                <div>
                    <h3 className="text-sm font-medium mb-2">Decorative Elements</h3>
                    <div className="flex items-center gap-2">
                        <span aria-hidden="true">🎨</span>
                        <span>Canvas</span>
                    </div>
                    <pre className="mt-2 p-2 bg-gray-100 text-xs rounded">
                        {`<span aria-hidden="true">🎨</span>`}
                    </pre>
                </div>

                <div>
                    <h3 className="text-sm font-medium mb-2">Form Fields with Descriptions</h3>
                    <div className="max-w-xs">
                        <label htmlFor="email" className="block text-sm font-medium">
                            Email
                        </label>
                        <input
                            id="email"
                            type="email"
                            aria-describedby="email-hint"
                            className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-lg"
                        />
                        <p id="email-hint" className="mt-1 text-xs text-gray-500">
                            We'll never share your email.
                        </p>
                    </div>
                    <pre className="mt-2 p-2 bg-gray-100 text-xs rounded">
                        {`<input id="email" aria-describedby="email-hint" />
<p id="email-hint">We'll never share your email.</p>`}
                    </pre>
                </div>
            </div>
        </div>
    ),
};

/**
 * Skip Links
 * 
 * Allow keyboard users to bypass repetitive navigation.
 */
export const SkipLinks: Story = {
    render: () => (
        <div className="space-y-8">
            <h2 className="text-xl font-semibold">Skip Links</h2>
            <p className="text-sm text-gray-600">
                Press Tab to see the skip link appear. It's visually hidden until focused.
            </p>

            <div className="relative border rounded-lg overflow-hidden">
                {/* Skip Link - Hidden until focused */}
                <a
                    href="#demo-main"
                    className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:top-2 focus:left-2 focus:px-4 focus:py-2 focus:bg-blue-600 focus:text-white focus:rounded-lg"
                >
                    Skip to main content
                </a>

                {/* Demo Header */}
                <header className="bg-gray-100 p-4">
                    <nav className="flex gap-4">
                        <a href="#" className="text-blue-600 hover:underline">Home</a>
                        <a href="#" className="text-blue-600 hover:underline">Products</a>
                        <a href="#" className="text-blue-600 hover:underline">About</a>
                    </nav>
                </header>

                {/* Demo Main */}
                <main id="demo-main" tabIndex={-1} className="p-4">
                    <p>Main content area - tabIndex={-1} allows programmatic focus</p>
                </main>
            </div>

            <pre className="p-2 bg-gray-100 text-xs rounded overflow-x-auto">
                {`<a
  href="#main-content"
  className="sr-only focus:not-sr-only focus:absolute ..."
>
  Skip to main content
</a>

<main id="main-content" tabIndex={-1}>
  ...
</main>`}
            </pre>
        </div>
    ),
};

/**
 * Live Regions
 * 
 * Announce dynamic content changes to screen readers.
 */
export const LiveRegions: Story = {
    render: function LiveRegionsDemo() {
        const [status, setStatus] = useState('');
        const [error, setError] = useState('');

        return (
            <div className="space-y-8">
                <h2 className="text-xl font-semibold">Live Regions</h2>
                <p className="text-sm text-gray-600">
                    Screen readers will announce changes to these regions automatically.
                </p>

                <div className="space-y-6">
                    <div>
                        <h3 className="text-sm font-medium mb-2">Polite Announcements (status updates)</h3>
                        <div className="flex gap-2">
                            <button
                                onClick={() => setStatus('Saving...')}
                                className="px-3 py-1.5 bg-blue-600 text-white text-sm rounded"
                            >
                                Trigger Status
                            </button>
                            <button
                                onClick={() => setStatus('Saved successfully!')}
                                className="px-3 py-1.5 bg-green-600 text-white text-sm rounded"
                            >
                                Success
                            </button>
                        </div>
                        <div
                            role="status"
                            aria-live="polite"
                            aria-atomic="true"
                            className="mt-2 p-2 bg-gray-100 rounded min-h-[2rem]"
                        >
                            {status}
                        </div>
                        <pre className="mt-2 p-2 bg-gray-100 text-xs rounded">
                            {`<div role="status" aria-live="polite" aria-atomic="true">
  {status}
</div>`}
                        </pre>
                    </div>

                    <div>
                        <h3 className="text-sm font-medium mb-2">Assertive Announcements (errors)</h3>
                        <button
                            onClick={() => setError('Failed to save. Please try again.')}
                            className="px-3 py-1.5 bg-red-600 text-white text-sm rounded"
                        >
                            Trigger Error
                        </button>
                        <div
                            role="alert"
                            aria-live="assertive"
                            className="mt-2 p-2 bg-red-100 text-red-700 rounded min-h-[2rem]"
                        >
                            {error}
                        </div>
                        <pre className="mt-2 p-2 bg-gray-100 text-xs rounded">
                            {`<div role="alert" aria-live="assertive">
  {error}
</div>`}
                        </pre>
                    </div>
                </div>
            </div>
        );
    },
};

/**
 * Landmarks
 * 
 * Use proper landmark roles for page structure.
 */
export const Landmarks: Story = {
    render: () => (
        <div className="space-y-8">
            <h2 className="text-xl font-semibold">Landmark Roles</h2>
            <p className="text-sm text-gray-600">
                Landmarks help screen reader users navigate the page structure.
            </p>

            <div className="border rounded-lg overflow-hidden">
                <header role="banner" className="bg-blue-100 p-4">
                    <span className="text-sm font-mono">role="banner"</span> - Site header
                </header>

                <div className="flex">
                    <nav role="navigation" aria-label="Main" className="bg-gray-100 p-4 w-48">
                        <span className="text-sm font-mono">role="navigation"</span>
                    </nav>

                    <main role="main" aria-label="Main content" className="flex-1 p-4">
                        <span className="text-sm font-mono">role="main"</span> - Primary content

                        <div role="region" aria-label="Search results" className="mt-4 p-2 bg-gray-50 rounded">
                            <span className="text-xs font-mono">role="region"</span> - Named section
                        </div>
                    </main>

                    <aside role="complementary" aria-label="Sidebar" className="bg-gray-100 p-4 w-48">
                        <span className="text-sm font-mono">role="complementary"</span>
                    </aside>
                </div>

                <footer role="contentinfo" className="bg-gray-200 p-4">
                    <span className="text-sm font-mono">role="contentinfo"</span> - Site footer
                </footer>
            </div>

            <pre className="p-2 bg-gray-100 text-xs rounded overflow-x-auto">
                {`<header role="banner">...</header>
<nav role="navigation" aria-label="Main">...</nav>
<main role="main" aria-label="Main content">...</main>
<aside role="complementary" aria-label="Sidebar">...</aside>
<footer role="contentinfo">...</footer>`}
            </pre>
        </div>
    ),
};

/**
 * Keyboard Navigation
 * 
 * All interactive elements must be keyboard accessible.
 */
export const KeyboardNavigation: Story = {
    render: function KeyboardDemo() {
        const [selected, setSelected] = useState(0);
        const items = ['Item 1', 'Item 2', 'Item 3', 'Item 4'];

        const handleKeyDown = (e: React.KeyboardEvent, index: number) => {
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                setSelected(Math.min(index + 1, items.length - 1));
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setSelected(Math.max(index - 1, 0));
            } else if (e.key === 'Escape') {
                e.preventDefault();
                // Close action
            }
        };

        return (
            <div className="space-y-8">
                <h2 className="text-xl font-semibold">Keyboard Navigation</h2>
                <p className="text-sm text-gray-600">
                    Use Arrow keys to navigate, Enter/Space to select.
                </p>

                <div className="space-y-4">
                    <div>
                        <h3 className="text-sm font-medium mb-2">Arrow Key Navigation</h3>
                        <ul role="listbox" aria-label="Select an item" className="border rounded-lg divide-y">
                            {items.map((item, index) => (
                                <li
                                    key={item}
                                    role="option"
                                    aria-selected={selected === index}
                                    tabIndex={selected === index ? 0 : -1}
                                    onKeyDown={(e) => handleKeyDown(e, index)}
                                    onClick={() => setSelected(index)}
                                    className={`px-4 py-2 cursor-pointer focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-500 ${selected === index ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50'
                                        }`}
                                >
                                    {item}
                                </li>
                            ))}
                        </ul>
                    </div>

                    <div>
                        <h3 className="text-sm font-medium mb-2">Key Bindings</h3>
                        <table className="text-sm border rounded-lg">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-4 py-2 text-left">Key</th>
                                    <th className="px-4 py-2 text-left">Action</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y">
                                <tr>
                                    <td className="px-4 py-2"><kbd className="px-1.5 py-0.5 bg-gray-100 rounded">Tab</kbd></td>
                                    <td className="px-4 py-2">Move to next focusable element</td>
                                </tr>
                                <tr>
                                    <td className="px-4 py-2"><kbd className="px-1.5 py-0.5 bg-gray-100 rounded">Enter</kbd> / <kbd className="px-1.5 py-0.5 bg-gray-100 rounded">Space</kbd></td>
                                    <td className="px-4 py-2">Activate button/link</td>
                                </tr>
                                <tr>
                                    <td className="px-4 py-2"><kbd className="px-1.5 py-0.5 bg-gray-100 rounded">Escape</kbd></td>
                                    <td className="px-4 py-2">Close modal/dropdown</td>
                                </tr>
                                <tr>
                                    <td className="px-4 py-2"><kbd className="px-1.5 py-0.5 bg-gray-100 rounded">Arrow</kbd> keys</td>
                                    <td className="px-4 py-2">Navigate within component</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        );
    },
};
