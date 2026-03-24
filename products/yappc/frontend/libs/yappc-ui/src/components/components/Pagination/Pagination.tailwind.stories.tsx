import React from 'react';

import { Pagination } from './Pagination.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Pagination> = {
  title: 'Components/Pagination',
  component: Pagination,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
    },
    variant: {
      control: 'select',
      options: ['default', 'outlined', 'rounded'],
    },
    showFirstLast: {
      control: 'boolean',
    },
    disabled: {
      control: 'boolean',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Pagination>;

export const Default: Story = {
  render: () => {
    const [page, setPage] = React.useState(1);

    return (
      <Pagination
        currentPage={page}
        totalPages={10}
        onPageChange={setPage}
      />
    );
  },
};

export const WithFirstLast: Story = {
  render: () => {
    const [page, setPage] = React.useState(1);

    return (
      <Pagination
        currentPage={page}
        totalPages={10}
        onPageChange={setPage}
        showFirstLast
      />
    );
  },
};

export const Sizes: Story = {
  render: () => {
    const [page, setPage] = React.useState(1);

    return (
      <div className="flex flex-col gap-4 items-center">
        <Pagination
          currentPage={page}
          totalPages={10}
          onPageChange={setPage}
          size="small"
        />
        <Pagination
          currentPage={page}
          totalPages={10}
          onPageChange={setPage}
          size="medium"
        />
        <Pagination
          currentPage={page}
          totalPages={10}
          onPageChange={setPage}
          size="large"
        />
      </div>
    );
  },
};

export const Variants: Story = {
  render: () => {
    const [page, setPage] = React.useState(5);

    return (
      <div className="flex flex-col gap-6 items-center">
        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">Default</p>
          <Pagination
            currentPage={page}
            totalPages={10}
            onPageChange={setPage}
            variant="default"
          />
        </div>

        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">Outlined</p>
          <Pagination
            currentPage={page}
            totalPages={10}
            onPageChange={setPage}
            variant="outlined"
          />
        </div>

        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">Rounded</p>
          <Pagination
            currentPage={page}
            totalPages={10}
            onPageChange={setPage}
            variant="rounded"
          />
        </div>
      </div>
    );
  },
};

export const FewPages: Story = {
  render: () => {
    const [page, setPage] = React.useState(2);

    return (
      <Pagination
        currentPage={page}
        totalPages={5}
        onPageChange={setPage}
      />
    );
  },
};

export const ManyPages: Story = {
  render: () => {
    const [page, setPage] = React.useState(25);

    return (
      <Pagination
        currentPage={page}
        totalPages={50}
        onPageChange={setPage}
        showFirstLast
      />
    );
  },
};

export const CustomSiblingCount: Story = {
  render: () => {
    const [page, setPage] = React.useState(10);

    return (
      <div className="flex flex-col gap-6 items-center">
        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">Sibling Count: 0</p>
          <Pagination
            currentPage={page}
            totalPages={20}
            onPageChange={setPage}
            siblingCount={0}
          />
        </div>

        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">Sibling Count: 1 (default)</p>
          <Pagination
            currentPage={page}
            totalPages={20}
            onPageChange={setPage}
            siblingCount={1}
          />
        </div>

        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">Sibling Count: 2</p>
          <Pagination
            currentPage={page}
            totalPages={20}
            onPageChange={setPage}
            siblingCount={2}
          />
        </div>
      </div>
    );
  },
};

export const DisabledState: Story = {
  render: () => {
    return (
      <Pagination
        currentPage={5}
        totalPages={10}
        onPageChange={() => {}}
        disabled
      />
    );
  },
};

export const EdgeCases: Story = {
  render: () => {
    const [firstPage, setFirstPage] = React.useState(1);
    const [lastPage, setLastPage] = React.useState(10);

    return (
      <div className="flex flex-col gap-6 items-center">
        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">First Page</p>
          <Pagination
            currentPage={firstPage}
            totalPages={10}
            onPageChange={setFirstPage}
            showFirstLast
          />
        </div>

        <div className="flex flex-col gap-2 items-center">
          <p className="text-sm text-grey-600">Last Page</p>
          <Pagination
            currentPage={lastPage}
            totalPages={10}
            onPageChange={setLastPage}
            showFirstLast
          />
        </div>
      </div>
    );
  },
};

export const TablePaginationExample: Story = {
  render: () => {
    const [page, setPage] = React.useState(1);
    const itemsPerPage = 10;
    const totalItems = 142;
    const totalPages = Math.ceil(totalItems / itemsPerPage);

    const startItem = (page - 1) * itemsPerPage + 1;
    const endItem = Math.min(page * itemsPerPage, totalItems);

    return (
      <div className="flex flex-col gap-4">
        <div className="text-sm text-grey-600 dark:text-grey-400">
          Showing {startItem}-{endItem} of {totalItems} items
        </div>
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          onPageChange={setPage}
          showFirstLast
        />
      </div>
    );
  },
};

export const DarkMode: Story = {
  render: () => {
    const [page, setPage] = React.useState(5);

    return (
      <div className="dark bg-grey-900 p-8 rounded-lg">
        <Pagination
          currentPage={page}
          totalPages={10}
          onPageChange={setPage}
          showFirstLast
        />
      </div>
    );
  },
  parameters: {
    backgrounds: { default: 'dark' },
  },
};

export const Playground: Story = {
  render: (args) => {
    const [page, setPage] = React.useState(args.currentPage);

    return (
      <Pagination
        {...args}
        currentPage={page}
        onPageChange={setPage}
      />
    );
  },
  args: {
    currentPage: 5,
    totalPages: 10,
    size: 'medium',
    variant: 'default',
    showFirstLast: false,
    siblingCount: 1,
    disabled: false,
  },
};
