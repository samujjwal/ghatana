/**
 * SplitPane Storybook Stories
 *
 * @package @ghatana/ui
 */

import type { Meta, StoryObj } from '@storybook/react';
import { SplitPane } from './SplitPane';

const meta = {
    title: 'Data/SplitPane',
    component: SplitPane,
    parameters: { layout: 'padded' },
    tags: ['autodocs'],
} satisfies Meta<typeof SplitPane>;

export default meta;
type Story = StoryObj<typeof meta>;

const LeftPanel = () => (
    <div className="p-4 bg-blue-50 dark:bg-blue-900">
        <h3 className="font-semibold mb-2">Left Panel</h3>
        <p className="text-sm text-gray-700 dark:text-gray-300">
            Drag the divider to resize. This is the left panel content.
        </p>
        <ul className="mt-4 text-sm space-y-2">
            <li>Item 1</li>
            <li>Item 2</li>
            <li>Item 3</li>
        </ul>
    </div>
);

const RightPanel = () => (
    <div className="p-4 bg-purple-50 dark:bg-purple-900">
        <h3 className="font-semibold mb-2">Right Panel</h3>
        <p className="text-sm text-gray-700 dark:text-gray-300">
            This is the right panel content. It resizes as you adjust the divider.
        </p>
        <div className="mt-4 text-sm">
            <p>Details view</p>
        </div>
    </div>
);

const TopPanel = () => (
    <div className="p-4 bg-green-50 dark:bg-green-900">
        <h3 className="font-semibold mb-2">Top Panel</h3>
        <p className="text-sm text-gray-700 dark:text-gray-300">
            This is the top panel content.
        </p>
    </div>
);

const BottomPanel = () => (
    <div className="p-4 bg-orange-50 dark:bg-orange-900">
        <h3 className="font-semibold mb-2">Bottom Panel</h3>
        <p className="text-sm text-gray-700 dark:text-gray-300">
            This is the bottom panel content.
        </p>
    </div>
);

export const HorizontalLayout: Story = {
    args: {
        orientation: 'horizontal',
        dividerSize: 8,
        children: [<LeftPanel key="left" />, <RightPanel key="right" />],
    },
    render: (args) => (
        <div className="h-96 border border-gray-300 dark:border-gray-700">
            <SplitPane {...args} />
        </div>
    ),
};

export const VerticalLayout: Story = {
    args: {
        orientation: 'vertical',
        dividerSize: 8,
        children: [<TopPanel key="top" />, <BottomPanel key="bottom" />],
    },
    render: (args) => (
        <div className="h-96 border border-gray-300 dark:border-gray-700">
            <SplitPane {...args} />
        </div>
    ),
};

export const WithInitialSizes: Story = {
    args: {
        orientation: 'horizontal',
        initialSizes: [30, 70],
        children: [<LeftPanel key="left" />, <RightPanel key="right" />],
    },
    render: (args) => (
        <div className="h-96 border border-gray-300 dark:border-gray-700">
            <SplitPane {...args} />
        </div>
    ),
};

export const WithMinMaxSizes: Story = {
    args: {
        orientation: 'horizontal',
        minSizes: [200, 200],
        maxSizes: [600, 600],
        children: [<LeftPanel key="left" />, <RightPanel key="right" />],
    },
    render: (args) => (
        <div className="h-96 border border-gray-300 dark:border-gray-700">
            <SplitPane {...args} />
        </div>
    ),
};

export const KeyboardAdjustable: Story = {
    args: {
        orientation: 'horizontal',
        keyboardAdjustable: true,
        children: [<LeftPanel key="left" />, <RightPanel key="right" />],
    },
    render: (args) => (
        <div className="h-96 border border-gray-300 dark:border-gray-700">
            <div className="mb-2 text-xs text-gray-600 dark:text-gray-400">
                Use arrow keys to adjust divider (focus the divider first)
            </div>
            <SplitPane {...args} />
        </div>
    ),
};
