import React from 'react';

import { TimePicker } from './TimePicker';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof TimePicker> = {
  title: 'Components/TimePicker',
  component: TimePicker,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof TimePicker>;

/**
 * Default time picker (12-hour format)
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    return <TimePicker value={value} onChange={setValue} />;
  },
};

/**
 * With initial value
 */
export const WithValue: Story = {
  render: () => {
    const time = new Date();
    time.setHours(14, 30, 0, 0);
    const [value, setValue] = React.useState<Date | null>(time);
    return <TimePicker value={value} onChange={setValue} />;
  },
};

/**
 * 24-hour format
 */
export const TwentyFourHour: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    return <TimePicker value={value} onChange={setValue} format="24h" />;
  },
};

/**
 * Different sizes
 */
export const Sizes: Story = {
  render: () => {
    const time = new Date();
    time.setHours(14, 30, 0, 0);
    const [value, setValue] = React.useState<Date | null>(time);
    
    return (
      <div className="flex flex-col gap-4 items-start">
        <TimePicker size="small" value={value} onChange={setValue} />
        <TimePicker size="medium" value={value} onChange={setValue} />
        <TimePicker size="large" value={value} onChange={setValue} />
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
      <div className="flex flex-col gap-4 items-start">
        <div>
          <TimePicker value={value} onChange={setValue} minuteStep={15} />
          <div className="mt-1 text-sm text-grey-600">15-minute intervals</div>
        </div>
        <div>
          <TimePicker value={value} onChange={setValue} minuteStep={30} />
          <div className="mt-1 text-sm text-grey-600">30-minute intervals</div>
        </div>
      </div>
    );
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => {
    const time = new Date();
    time.setHours(14, 30, 0, 0);
    const [value] = React.useState<Date | null>(time);
    return <TimePicker value={value} disabled />;
  },
};

/**
 * Read-only state
 */
export const ReadOnly: Story = {
  render: () => {
    const time = new Date();
    time.setHours(14, 30, 0, 0);
    const [value] = React.useState<Date | null>(time);
    return <TimePicker value={value} readOnly />;
  },
};

/**
 * Meeting time picker example
 */
export const MeetingTime: Story = {
  render: () => {
    const [value, setValue] = React.useState<Date | null>(null);
    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">
          Meeting Time
        </label>
        <TimePicker
          value={value}
          onChange={setValue}
          minuteStep={15}
          placeholder="Select meeting time"
        />
        <div className="mt-2 text-sm text-grey-600">Available in 15-minute intervals</div>
      </div>
    );
  },
};

/**
 * Opening hours example
 */
export const OpeningHours: Story = {
  render: () => {
    const openTime = new Date();
    openTime.setHours(9, 0, 0, 0);
    const closeTime = new Date();
    closeTime.setHours(17, 0, 0, 0);

    const [open, setOpen] = React.useState<Date | null>(openTime);
    const [close, setClose] = React.useState<Date | null>(closeTime);

    return (
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-grey-700 mb-2">Opening Time</label>
          <TimePicker value={open} onChange={setOpen} format="24h" />
        </div>
        <div>
          <label className="block text-sm font-medium text-grey-700 mb-2">Closing Time</label>
          <TimePicker value={close} onChange={setClose} format="24h" />
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
    const time = new Date();
    time.setHours(14, 30, 0, 0);
    const [value, setValue] = React.useState<Date | null>(time);
    return (
      <div className="bg-grey-900 p-8 rounded-lg">
        <TimePicker value={value} onChange={setValue} />
      </div>
    );
  },
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  args: {
    format: '12h',
    minuteStep: 1,
    placeholder: 'Select time',
    size: 'medium',
    disabled: false,
    readOnly: false,
  },
};
