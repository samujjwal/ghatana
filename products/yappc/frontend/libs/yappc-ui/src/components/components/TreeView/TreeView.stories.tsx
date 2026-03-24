import React from 'react';

import { TreeView, type TreeNode } from './TreeView';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof TreeView> = {
  title: 'Components/TreeView',
  component: TreeView,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof TreeView>;

const fileSystemData: TreeNode[] = [
  {
    id: '1',
    label: 'Documents',
    children: [
      {
        id: '1-1',
        label: 'Work',
        children: [
          { id: '1-1-1', label: 'Projects.docx' },
          { id: '1-1-2', label: 'Reports.pdf' },
        ],
      },
      {
        id: '1-2',
        label: 'Personal',
        children: [
          { id: '1-2-1', label: 'Photos' },
          { id: '1-2-2', label: 'Videos' },
        ],
      },
    ],
  },
  {
    id: '2',
    label: 'Downloads',
    children: [
      { id: '2-1', label: 'installer.exe' },
      { id: '2-2', label: 'document.pdf' },
    ],
  },
  {
    id: '3',
    label: 'Desktop',
  },
];

/**
 * Default tree view
 */
export const Default: Story = {
  args: {
    data: fileSystemData,
  },
};

/**
 * With default expanded state
 */
export const DefaultExpanded: Story = {
  args: {
    data: fileSystemData,
    defaultExpanded: true,
  },
};

/**
 * With selection
 */
export const WithSelection: Story = {
  render: () => {
    const [selected, setSelected] = React.useState<(string | number)[]>([]);

    return (
      <div>
        <TreeView data={fileSystemData} selected={selected} onSelect={setSelected} />
        <div className="mt-4 p-3 bg-grey-100 rounded text-sm">
          Selected: {selected.length > 0 ? selected.join(', ') : 'None'}
        </div>
      </div>
    );
  },
};

/**
 * Multi-select mode
 */
export const MultiSelect: Story = {
  render: () => {
    const [selected, setSelected] = React.useState<(string | number)[]>([]);

    return (
      <div>
        <TreeView data={fileSystemData} selected={selected} onSelect={setSelected} multiSelect />
        <div className="mt-4 p-3 bg-grey-100 rounded text-sm">
          Selected: {selected.length > 0 ? selected.join(', ') : 'None'}
        </div>
      </div>
    );
  },
};

/**
 * Controlled expansion
 */
export const ControlledExpansion: Story = {
  render: () => {
    const [expanded, setExpanded] = React.useState<(string | number)[]>(['1']);

    return (
      <div>
        <div className="mb-4 flex gap-2">
          <button
            onClick={() => setExpanded(['1', '1-1', '1-2', '2'])}
            className="px-3 py-1.5 text-sm bg-primary-500 text-white rounded hover:bg-primary-600"
          >
            Expand All
          </button>
          <button
            onClick={() => setExpanded([])}
            className="px-3 py-1.5 text-sm bg-grey-200 text-grey-700 rounded hover:bg-grey-300"
          >
            Collapse All
          </button>
        </div>
        <TreeView data={fileSystemData} expanded={expanded} onExpand={setExpanded} />
      </div>
    );
  },
};

/**
 * Async loading children
 */
export const AsyncLoading: Story = {
  render: () => {
    const asyncData: TreeNode[] = [
      { id: 'folder1', label: 'Folder 1' },
      { id: 'folder2', label: 'Folder 2' },
      { id: 'folder3', label: 'Folder 3' },
    ];

    const loadChildren = async (node: TreeNode): Promise<TreeNode[]> => {
      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 1000));

      return [
        { id: `${node.id}-child1`, label: `${node.label} - Child 1` },
        { id: `${node.id}-child2`, label: `${node.label} - Child 2` },
        { id: `${node.id}-child3`, label: `${node.label} - Child 3` },
      ];
    };

    return <TreeView data={asyncData} onLoadChildren={loadChildren} />;
  },
};

/**
 * Custom icons
 */
export const CustomIcons: Story = {
  render: () => {
    const customData: TreeNode[] = [
      {
        id: '1',
        label: 'Home',
        icon: <span className="text-lg">🏠</span>,
        children: [
          { id: '1-1', label: 'Living Room', icon: <span className="text-lg">🛋️</span> },
          { id: '1-2', label: 'Kitchen', icon: <span className="text-lg">🍳</span> },
          { id: '1-3', label: 'Bedroom', icon: <span className="text-lg">🛏️</span> },
        ],
      },
      {
        id: '2',
        label: 'Work',
        icon: <span className="text-lg">💼</span>,
        children: [
          { id: '2-1', label: 'Office', icon: <span className="text-lg">🏢</span> },
          { id: '2-2', label: 'Meeting Room', icon: <span className="text-lg">👥</span> },
        ],
      },
    ];

    return <TreeView data={customData} />;
  },
};

/**
 * Disabled nodes
 */
export const DisabledNodes: Story = {
  render: () => {
    const dataWithDisabled: TreeNode[] = [
      {
        id: '1',
        label: 'Available Folder',
        children: [
          { id: '1-1', label: 'File 1' },
          { id: '1-2', label: 'File 2 (Disabled)', disabled: true },
        ],
      },
      {
        id: '2',
        label: 'Disabled Folder',
        disabled: true,
        children: [
          { id: '2-1', label: 'Hidden File' },
        ],
      },
    ];

    return <TreeView data={dataWithDisabled} />;
  },
};

/**
 * Custom render function
 */
export const CustomRender: Story = {
  render: () => {
    const renderNode = (node: TreeNode, isSelected: boolean) => (
      <div className="flex items-center gap-2">
        <span className={isSelected ? 'font-semibold' : ''}>{node.label}</span>
        {node.children && (
          <span className="text-xs text-grey-500">({node.children.length} items)</span>
        )}
      </div>
    );

    return <TreeView data={fileSystemData} renderNode={renderNode} />;
  },
};

/**
 * Project explorer example
 */
export const ProjectExplorer: Story = {
  render: () => {
    const [selected, setSelected] = React.useState<(string | number)[]>([]);

    const projectData: TreeNode[] = [
      {
        id: 'src',
        label: 'src',
        children: [
          {
            id: 'components',
            label: 'components',
            children: [
              { id: 'Button.tsx', label: 'Button.tsx' },
              { id: 'Input.tsx', label: 'Input.tsx' },
              { id: 'Modal.tsx', label: 'Modal.tsx' },
            ],
          },
          {
            id: 'utils',
            label: 'utils',
            children: [
              { id: 'helpers.ts', label: 'helpers.ts' },
              { id: 'validators.ts', label: 'validators.ts' },
            ],
          },
          { id: 'App.tsx', label: 'App.tsx' },
          { id: 'index.tsx', label: 'index.tsx' },
        ],
      },
      {
        id: 'public',
        label: 'public',
        children: [
          { id: 'index.html', label: 'index.html' },
          { id: 'favicon.ico', label: 'favicon.ico' },
        ],
      },
      { id: 'package.json', label: 'package.json' },
      { id: 'tsconfig.json', label: 'tsconfig.json' },
    ];

    return (
      <div className="max-w-md border border-grey-300 rounded-lg overflow-hidden">
        <div className="px-4 py-2 bg-grey-100 border-b border-grey-300 font-semibold text-sm">
          Project Explorer
        </div>
        <div className="p-2">
          <TreeView
            data={projectData}
            selected={selected}
            onSelect={setSelected}
            defaultExpanded
          />
        </div>
      </div>
    );
  },
};

/**
 * Organization chart
 */
export const OrganizationChart: Story = {
  render: () => {
    const orgData: TreeNode[] = [
      {
        id: 'ceo',
        label: 'CEO - John Smith',
        icon: <span className="text-lg">👔</span>,
        children: [
          {
            id: 'cto',
            label: 'CTO - Sarah Johnson',
            icon: <span className="text-lg">💻</span>,
            children: [
              { id: 'dev1', label: 'Developer - Mike Brown', icon: <span className="text-lg">👨‍💻</span> },
              { id: 'dev2', label: 'Developer - Lisa Davis', icon: <span className="text-lg">👩‍💻</span> },
            ],
          },
          {
            id: 'cfo',
            label: 'CFO - Robert Wilson',
            icon: <span className="text-lg">💰</span>,
            children: [
              { id: 'acc1', label: 'Accountant - Emma Taylor', icon: <span className="text-lg">📊</span> },
            ],
          },
        ],
      },
    ];

    return <TreeView data={orgData} defaultExpanded />;
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  render: () => (
    <div className="bg-grey-900 p-8 rounded-lg">
      <TreeView data={fileSystemData} defaultExpanded />
    </div>
  ),
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  args: {
    data: fileSystemData,
    multiSelect: false,
    defaultExpanded: false,
    showExpandIcon: true,
    indent: 24,
    disabled: false,
  },
};
