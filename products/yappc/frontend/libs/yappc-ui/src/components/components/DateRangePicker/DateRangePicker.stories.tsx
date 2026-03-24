import React from 'react';

import { DateRangePicker, type DateRange } from './DateRangePicker';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof DateRangePicker> = {
  title: 'Components/DateRangePicker',
  component: DateRangePicker,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof DateRangePicker>;

/**
 * Default date range picker
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = React.useState<DateRange>({ start: null, end: null });
    return <DateRangePicker value={value} onChange={setValue} />;
  },
};

/**
 * With initial range
 */
export const WithValue: Story = {
  render: () => {
    const start = new Date();
    const end = new Date();
    end.setDate(end.getDate() + 7);
    const [value, setValue] = React.useState<DateRange>({ start, end });
    return <DateRangePicker value={value} onChange={setValue} />;
  },
};

/**
 * Different sizes
 */
export const Sizes: Story = {
  render: () => {
    const start = new Date();
    const end = new Date();
    end.setDate(end.getDate() + 7);
    const [value, setValue] = React.useState<DateRange>({ start, end });

    return (
      <div className="flex flex-col gap-4 items-start">
        <DateRangePicker size="small" value={value} onChange={setValue} />
        <DateRangePicker size="medium" value={value} onChange={setValue} />
        <DateRangePicker size="large" value={value} onChange={setValue} />
      </div>
    );
  },
};

/**
 * Vacation booking example
 */
export const VacationBooking: Story = {
  render: () => {
    const [value, setValue] = React.useState<DateRange>({ start: null, end: null });
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">
          Select Vacation Dates
        </label>
        <DateRangePicker
          value={value}
          onChange={setValue}
          minDate={today}
          startPlaceholder="Check-in"
          endPlaceholder="Check-out"
        />
        {value.start && value.end && (
          <div className="mt-2 text-sm text-grey-600">
            Duration: {Math.ceil((value.end.getTime() - value.start.getTime()) / (1000 * 60 * 60 * 24))} days
          </div>
        )}
      </div>
    );
  },
};

/**
 * Report date range example
 */
export const ReportDateRange: Story = {
  render: () => {
    const thisMonthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    const today = new Date();
    const [value, setValue] = React.useState<DateRange>({ start: thisMonthStart, end: today });

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">Report Period</label>
        <DateRangePicker
          value={value}
          onChange={setValue}
          startPlaceholder="From"
          endPlaceholder="To"
        />
      </div>
    );
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => {
    const start = new Date();
    const end = new Date();
    end.setDate(end.getDate() + 7);
    const [value] = React.useState<DateRange>({ start, end });
    return <DateRangePicker value={value} disabled />;
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  render: () => {
    const start = new Date();
    const end = new Date();
    end.setDate(end.getDate() + 7);
    const [value, setValue] = React.useState<DateRange>({ start, end });
    return (
      <div className="bg-grey-900 p-8 rounded-lg">
        <DateRangePicker value={value} onChange={setValue} />
      </div>
    );
  },
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  args: {
    format: 'MM/DD/YYYY',
    startPlaceholder: 'Start date',
    endPlaceholder: 'End date',
    size: 'medium',
    disabled: false,
    readOnly: false,
  },
};
