import React from 'react';

import { DatePicker } from './DatePicker';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof DatePicker> = {
  title: 'Components/DatePicker',
  component: DatePicker,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof DatePicker>;

/**
 * Default date picker
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    return <DatePicker value={value} onChange={setValue} />;
  },
};

/**
 * With initial value
 */
export const WithValue: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(new Date());
    return <DatePicker value={value} onChange={setValue} />;
  },
};

/**
 * Different sizes
 */
export const Sizes: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(new Date());
    return (
      <div className="flex flex-col gap-4 items-start">
        <DatePicker size="small" value={value} onChange={setValue} />
        <DatePicker size="medium" value={value} onChange={setValue} />
        <DatePicker size="large" value={value} onChange={setValue} />
      </div>
    );
  },
};

/**
 * With min and max dates
 */
export const MinMaxDates: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    const today = new Date();
    const minDate = new Date(today.getFullYear(), today.getMonth(), 1);
    const maxDate = new Date(today.getFullYear(), today.getMonth() + 1, 0);

    return (
      <div>
        <DatePicker value={value} onChange={setValue} minDate={minDate} maxDate={maxDate} />
        <div className="mt-2 text-sm text-grey-600">
          Only dates in current month are selectable
        </div>
      </div>
    );
  },
};

/**
 * With disabled dates
 */
export const DisabledDates: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    
    // Disable weekends
    const shouldDisableDate = (date: Date) => {
      const day = date.getDay();
      return day === 0 || day === 6; // Sunday or Saturday
    };

    return (
      <div>
        <DatePicker value={value} onChange={setValue} shouldDisableDate={shouldDisableDate} />
        <div className="mt-2 text-sm text-grey-600">Weekends are disabled</div>
      </div>
    );
  },
};

/**
 * With week numbers
 */
export const WithWeekNumbers: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(new Date());
    return <DatePicker value={value} onChange={setValue} showWeekNumbers />;
  },
};

/**
 * Monday as first day of week
 */
export const MondayFirst: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(new Date());
    return <DatePicker value={value} onChange={setValue} firstDayOfWeek={1} />;
  },
};

/**
 * Custom date format
 */
export const CustomFormat: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(new Date());
    return (
      <div className="flex flex-col gap-4 items-start">
        <DatePicker value={value} onChange={setValue} format="MM/DD/YYYY" placeholder="MM/DD/YYYY" />
        <DatePicker value={value} onChange={setValue} format="DD/MM/YYYY" placeholder="DD/MM/YYYY" />
        <DatePicker value={value} onChange={setValue} format="YYYY-MM-DD" placeholder="YYYY-MM-DD" />
      </div>
    );
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => {
    const [value] = React.useState<Date | null>(new Date());
    return <DatePicker value={value} disabled />;
  },
};

/**
 * Read-only state
 */
export const ReadOnly: Story = {
  render: () => {
    const [value] = React.useState<Date | null>(new Date());
    return <DatePicker value={value} readOnly />;
  },
};

/**
 * Birthday picker example
 */
export const BirthdayPicker: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    const today = new Date();
    const maxDate = new Date(today.getFullYear() - 18, today.getMonth(), today.getDate());
    const minDate = new Date(today.getFullYear() - 120, 0, 1);

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">Date of Birth</label>
        <DatePicker
          value={value}
          onChange={setValue}
          minDate={minDate}
          maxDate={maxDate}
          placeholder="Select your birthday"
        />
        <div className="mt-2 text-sm text-grey-600">Must be at least 18 years old</div>
      </div>
    );
  },
};

/**
 * Appointment picker example
 */
export const AppointmentPicker: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const shouldDisableDate = (date: Date) => {
      // Disable past dates and weekends
      if (date < today) return true;
      const day = date.getDay();
      return day === 0 || day === 6;
    };

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">
          Appointment Date
        </label>
        <DatePicker
          value={value}
          onChange={setValue}
          shouldDisableDate={shouldDisableDate}
          placeholder="Select appointment date"
        />
        <div className="mt-2 text-sm text-grey-600">
          Available weekdays only, no past dates
        </div>
      </div>
    );
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(new Date());
    return (
      <div className="bg-grey-900 p-8 rounded-lg">
        <DatePicker value={value} onChange={setValue} />
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
    placeholder: 'Select date',
    size: 'medium',
    showWeekNumbers: false,
    firstDayOfWeek: 0,
    disabled: false,
    readOnly: false,
  },
};
