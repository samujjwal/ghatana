/**
 * TreeTable Storybook Stories
 *
 * @package @ghatana/ui
 */

import type { Meta, StoryObj } from '@storybook/react';
import { TreeTable } from './TreeTable';

const meta = {
    title: 'Data/TreeTable',
    component: TreeTable,
    parameters: { layout: 'padded' },
    tags: ['autodocs'],
} satisfies Meta<typeof TreeTable>;

export default meta;
type Story = StoryObj<typeof meta>;

interface Task {
    id: string;
    name: string;
    assignee: string;
    status: string;
    dueDate: string;
}

const mockTasks: Task[] = [
    {
        id: '1',
        name: 'Project Alpha',
        assignee: 'Alice',
        status: 'In Progress',
        dueDate: '2025-12-01',
    },
    {
        id: '2',
        name: 'Setup infrastructure',
        assignee: 'Bob',
        status: 'In Progress',
        dueDate: '2025-11-30',
    },
    {
        id: '3',
        name: 'Configure CI/CD',
        assignee: 'Charlie',
        status: 'Pending',
        dueDate: '2025-12-05',
    },
];

const mockTreeData = [
    {
        id: 'task-1',
        data: {
            id: 'task-1',
            name: 'Feature Development',
            assignee: 'Alice',
            status: 'In Progress',
            dueDate: '2025-12-01',
        },
        children: [
            {
                id: 'task-1-1',
                data: {
                    id: 'task-1-1',
                    name: 'Backend API',
                    assignee: 'Bob',
                    status: 'In Progress',
                    dueDate: '2025-11-30',
                },
            },
            {
                id: 'task-1-2',
                data: {
                    id: 'task-1-2',
                    name: 'Frontend UI',
                    assignee: 'Charlie',
                    status: 'Pending',
                    dueDate: '2025-12-05',
                },
            },
        ],
    },
    {
        id: 'task-2',
        data: {
            id: 'task-2',
            name: 'Testing',
            assignee: 'Diana',
            status: 'Pending',
            dueDate: '2025-12-10',
        },
        children: [
            {
                id: 'task-2-1',
                data: {
                    id: 'task-2-1',
                    name: 'Unit Tests',
                    assignee: 'Eve',
                    status: 'Not Started',
                    dueDate: '2025-12-08',
                },
            },
        ],
    },
];

export const Basic: Story = {
    args: {
        data: mockTreeData,
        columns: [
            { key: 'name', label: 'Task Name' },
            { key: 'assignee', label: 'Assignee' },
            { key: 'status', label: 'Status' },
            { key: 'dueDate', label: 'Due Date' },
        ],
    },
};

export const WithSelection: Story = {
    args: {
        data: mockTreeData,
        columns: [
            { key: 'name', label: 'Task Name' },
            { key: 'assignee', label: 'Assignee' },
            { key: 'status', label: 'Status' },
        ],
        selectable: true,
    },
};

export const WithCustomRendering: Story = {
    args: {
        data: mockTreeData,
        columns: [
            { key: 'name', label: 'Task' },
            {
                key: 'status',
                label: 'Status',
                render: (value) => (
                    <span
                        className={`px-2 py-1 rounded text-xs font-semibold ${value === 'In Progress'
                                ? 'bg-blue-100 text-blue-800'
                                : value === 'Pending'
                                    ? 'bg-amber-100 text-amber-800'
                                    : 'bg-gray-100 text-gray-800'
                            }`}
                    >
                        {String(value)}
                    </span>
                ),
            },
        ],
    },
};

export const ExpandedInitially: Story = {
    args: {
        data: mockTreeData,
        columns: [
            { key: 'name', label: 'Task' },
            { key: 'assignee', label: 'Assignee' },
            { key: 'status', label: 'Status' },
        ],
        defaultExpandedIds: ['task-1', 'task-2'],
    },
};

export const Interactive: Story = {
    args: {
        data: mockTreeData,
        columns: [
            { key: 'name', label: 'Task' },
            { key: 'assignee', label: 'Assignee' },
            { key: 'status', label: 'Status' },
        ],
        selectable: true,
    },
    render: (args) => (
        <TreeTable
            {...args}
            onRowClick={(id) => console.log('Row clicked:', id)}
            onSelectionChange={(ids) => console.log('Selected:', ids)}
        />
    ),
};
