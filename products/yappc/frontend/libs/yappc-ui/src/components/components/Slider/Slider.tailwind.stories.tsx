/**
 * Slider Component Stories (Tailwind CSS + Base UI)
 */

import { useState } from 'react';

import { Slider } from './Slider.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Forms/Slider (Tailwind)',
  component: Slider,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A range slider component built with Base UI Slider primitives and styled with Tailwind CSS. Supports single and dual-thumb ranges, custom marks, and both horizontal and vertical orientations.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
      description: 'Size variant',
    },
    colorScheme: {
      control: 'select',
      options: ['primary', 'secondary', 'success', 'error', 'warning', 'grey'],
      description: 'Color scheme',
    },
    orientation: {
      control: 'select',
      options: ['horizontal', 'vertical'],
      description: 'Slider orientation',
    },
    disabled: {
      control: 'boolean',
      description: 'Whether the slider is disabled',
    },
    showValue: {
      control: 'boolean',
      description: 'Show current value above slider',
    },
  },
} satisfies Meta<typeof Slider>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Default single-value slider
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = useState(50);

    return (
      <div className="w-full max-w-md">
        <Slider
          label="Volume"
          value={value}
          onChange={setValue}
          min={0}
          max={100}
          step={1}
        />
      </div>
    );
  },
};

/**
 * All size variants
 */
export const Sizes: Story = {
  render: () => {
    const [value1, setValue1] = useState(30);
    const [value2, setValue2] = useState(50);
    const [value3, setValue3] = useState(70);

    return (
      <div className="space-y-8">
        <div className="w-full max-w-md">
          <Slider
            label="Small"
            size="sm"
            value={value1}
            onChange={setValue1}
            min={0}
            max={100}
          />
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Medium (default)"
            size="md"
            value={value2}
            onChange={setValue2}
            min={0}
            max={100}
          />
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Large"
            size="lg"
            value={value3}
            onChange={setValue3}
            min={0}
            max={100}
          />
        </div>
      </div>
    );
  },
};

/**
 * Color scheme variants
 */
export const ColorSchemes: Story = {
  render: () => {
    const [value, setValue] = useState(60);

    return (
      <div className="space-y-6">
        <div className="w-full max-w-md">
          <Slider
            label="Primary"
            colorScheme="primary"
            value={value}
            onChange={setValue}
            min={0}
            max={100}
          />
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Secondary"
            colorScheme="secondary"
            value={value}
            onChange={setValue}
            min={0}
            max={100}
          />
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Success"
            colorScheme="success"
            value={value}
            onChange={setValue}
            min={0}
            max={100}
          />
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Error"
            colorScheme="error"
            value={value}
            onChange={setValue}
            min={0}
            max={100}
          />
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Warning"
            colorScheme="warning"
            value={value}
            onChange={setValue}
            min={0}
            max={100}
          />
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Grey"
            colorScheme="grey"
            value={value}
            onChange={setValue}
            min={0}
            max={100}
          />
        </div>
      </div>
    );
  },
};

/**
 * Range slider with dual thumbs
 */
export const RangeSlider: Story = {
  render: () => {
    const [range, setRange] = useState<[number, number]>([25, 75]);

    return (
      <div className="w-full max-w-md">
        <Slider
          label="Price Range"
          value={range}
          onChange={setRange as (value: number | number[]) => void}
          min={0}
          max={100}
          step={5}
          helperText={`Selected range: $${range[0]} - $${range[1]}`}
        />
      </div>
    );
  },
};

/**
 * Slider with custom marks
 */
export const WithMarks: Story = {
  render: () => {
    const [value, setValue] = useState(50);

    const marks = [
      { value: 0, label: '0°C' },
      { value: 25, label: '25°C' },
      { value: 50, label: '50°C' },
      { value: 75, label: '75°C' },
      { value: 100, label: '100°C' },
    ];

    return (
      <div className="w-full max-w-md pb-8">
        <Slider
          label="Temperature"
          value={value}
          onChange={setValue}
          min={0}
          max={100}
          step={1}
          marks={marks}
        />
      </div>
    );
  },
};

/**
 * Slider with many marks (no labels)
 */
export const WithManyMarks: Story = {
  render: () => {
    const [value, setValue] = useState(5);

    const marks = Array.from({ length: 11 }, (_, i) => ({ value: i }));

    return (
      <div className="w-full max-w-md pb-4">
        <Slider
          label="Rating"
          value={value}
          onChange={setValue}
          min={0}
          max={10}
          step={1}
          marks={marks}
          showValue
        />
      </div>
    );
  },
};

/**
 * Vertical orientation
 */
export const Vertical: Story = {
  render: () => {
    const [value, setValue] = useState(40);

    return (
      <div className="flex h-64 items-center justify-center">
        <Slider
          label="Volume"
          orientation="vertical"
          value={value}
          onChange={setValue}
          min={0}
          max={100}
          step={1}
          showValue
        />
      </div>
    );
  },
};

/**
 * With value display
 */
export const WithValue: Story = {
  render: () => {
    const [value, setValue] = useState(65);

    return (
      <div className="w-full max-w-md">
        <Slider
          label="Brightness"
          value={value}
          onChange={setValue}
          min={0}
          max={100}
          step={1}
          showValue
          formatValue={(val) => `${val}%`}
        />
      </div>
    );
  },
};

/**
 * Custom formatting
 */
export const CustomFormatting: Story = {
  render: () => {
    const [value, setValue] = useState(50);

    const formatValue = (val: number) => {
      if (val < 33) return 'Low';
      if (val < 67) return 'Medium';
      return 'High';
    };

    return (
      <div className="w-full max-w-md">
        <Slider
          label="Performance"
          value={value}
          onChange={setValue}
          min={0}
          max={100}
          step={1}
          showValue
          formatValue={formatValue}
        />
      </div>
    );
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => (
    <div className="w-full max-w-md">
      <Slider
        label="Disabled slider"
        defaultValue={50}
        min={0}
        max={100}
        disabled
        helperText="This slider is disabled"
      />
    </div>
  ),
};

/**
 * Form example with multiple sliders
 */
export const FormExample: Story = {
  render: () => {
    const [volume, setVolume] = useState(70);
    const [bass, setBass] = useState(50);
    const [treble, setTreble] = useState(50);
    const [balance, setBalance] = useState(0);

    return (
      <div className="mx-auto max-w-md space-y-6 rounded-lg border border-grey-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-semibold text-grey-900">Audio Settings</h3>

        <Slider
          label="Volume"
          value={volume}
          onChange={setVolume}
          min={0}
          max={100}
          step={1}
          showValue
          formatValue={(val) => `${val}%`}
          colorScheme="primary"
        />

        <Slider
          label="Bass"
          value={bass}
          onChange={setBass}
          min={0}
          max={100}
          step={5}
          showValue
          colorScheme="secondary"
        />

        <Slider
          label="Treble"
          value={treble}
          onChange={setTreble}
          min={0}
          max={100}
          step={5}
          showValue
          colorScheme="secondary"
        />

        <Slider
          label="Balance"
          value={balance}
          onChange={setBalance}
          min={-100}
          max={100}
          step={10}
          showValue
          formatValue={(val) => (val === 0 ? 'Center' : val > 0 ? `R+${val}` : `L${val}`)}
          colorScheme="grey"
          marks={[
            { value: -100, label: 'L' },
            { value: 0, label: 'C' },
            { value: 100, label: 'R' },
          ]}
        />

        <div className="flex justify-end gap-2 pt-4">
          <button
            type="button"
            onClick={() => {
              setVolume(70);
              setBass(50);
              setTreble(50);
              setBalance(0);
            }}
            className="rounded-md border border-grey-300 px-4 py-2 text-sm font-medium text-grey-700 hover:bg-grey-50"
          >
            Reset
          </button>
          <button
            type="button"
            className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
          >
            Apply
          </button>
        </div>
      </div>
    );
  },
};

/**
 * Accessibility demonstration
 */
export const Accessibility: Story = {
  render: () => {
    const [value, setValue] = useState(50);

    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-grey-200 bg-grey-50 p-4">
          <h3 className="mb-2 font-semibold text-grey-900">Accessibility Features:</h3>
          <ul className="space-y-1 text-sm text-grey-700">
            <li>• Keyboard navigation (Arrow keys to adjust, Home/End for min/max)</li>
            <li>• ARIA attributes for screen readers</li>
            <li>• Proper labeling and value announcements</li>
            <li>• Focus indicators</li>
            <li>• Helper text linked via aria-describedby</li>
          </ul>
        </div>

        <div className="w-full max-w-md">
          <Slider
            label="Accessible slider"
            value={value}
            onChange={setValue}
            min={0}
            max={100}
            step={1}
            showValue
            helperText="Use arrow keys to adjust value"
          />
        </div>
      </div>
    );
  },
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    label: 'Playground Slider',
    helperText: 'Adjust the value',
    min: 0,
    max: 100,
    step: 1,
    size: 'md',
    colorScheme: 'primary',
    orientation: 'horizontal',
    showValue: false,
    disabled: false,
  },
  render: (args) => {
    const [value, setValue] = useState(50);

    return (
      <div className={args.orientation === 'vertical' ? 'h-64' : 'w-full max-w-md'}>
        <Slider {...args} value={value} onChange={setValue} />
      </div>
    );
  },
};
