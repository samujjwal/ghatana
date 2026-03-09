import React from 'react';

import { DateTimePicker } from './DateTimePicker';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof DateTimePicker> = {
  title: 'Components/DateTimePicker',
  component: DateTimePicker,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof DateTimePicker>;

/**
 * Default date-time picker
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    return <DateTimePicker value={value} onChange={setValue} />;
  },
};

/**
 * With initial value
 */
export const WithValue: Story = {
  render: () => {
    const dateTime = new Date();
    dateTime.setHours(14, 30, 0, 0);
    const [value, setValue] = React.useState<Date | null>(dateTime);
    return <DateTimePicker value={value} onChange={setValue} />;
  },
};

/**
 * 24-hour time format
 */
export const TwentyFourHour: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    return <DateTimePicker value={value} onChange={setValue} timeFormat="24h" />;
  },
};

/**
 * Different sizes
 */
export const Sizes: Story = {
  render: () => {
    const dateTime = new Date();
    dateTime.setHours(14, 30, 0, 0);
    const [value, setValue] = React.useState<Date | null>(dateTime);

    return (
      <div className="flex flex-col gap-4 items-start">
        <DateTimePicker size="small" value={value} onChange={setValue} />
        <DateTimePicker size="medium" value={value} onChange={setValue} />
        <DateTimePicker size="large" value={value} onChange={setValue} />
      </div>
    );
  },
};

/**
 * With minute steps
 */
export const MinuteSteps: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    return (
      <div>
        <DateTimePicker value={value} onChange={setValue} minuteStep={15} />
        <div className="mt-2 text-sm text-grey-600">Time in 15-minute intervals</div>
      </div>
    );
  },
};

/**
 * Event scheduling example
 */
export const EventScheduling: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">Event Date & Time</label>
        <DateTimePicker
          value={value}
          onChange={setValue}
          minDate={today}
          minuteStep={30}
          datePlaceholder="Select date"
          timePlaceholder="Select time"
        />
        {value && (
          <div className="mt-2 text-sm text-grey-600">
            Scheduled for: {value.toLocaleString()}
          </div>
        )}
      </div>
    );
  },
};

/**
 * Deadline picker example
 */
export const DeadlinePicker: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    const today = new Date();

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">Deadline</label>
        <DateTimePicker
          value={value}
          onChange={setValue}
          minDate={today}
          timeFormat="24h"
          datePlaceholder="Select deadline date"
          timePlaceholder="Select time"
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
    const dateTime = new Date();
    const [value] = React.useState<Date | null>(dateTime);
    return <DateTimePicker value={value} disabled />;
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  render: () => {
    const dateTime = new Date();
    const [value, setValue] = React.useState<Date | null>(dateTime);
    return (
      <div className="bg-grey-900 p-8 rounded-lg">
        <DateTimePicker value={value} onChange={setValue} />
      </div>
    );
  },
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  args: {
    timeFormat: '12h',
    dateFormat: 'MM/DD/YYYY',
    minuteStep: 1,
    datePlaceholder: 'Select date',
    timePlaceholder: 'Select time',
    size: 'medium',
    disabled: false,
    readOnly: false,
  },
};
