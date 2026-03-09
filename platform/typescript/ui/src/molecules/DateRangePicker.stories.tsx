import type { Meta, StoryObj } from '@storybook/react';
import { DateRangePicker, type DateRange, type DateRangePreset } from './DateRangePicker';
import { useState } from 'react';

/**
 * DateRangePicker provides an intuitive interface for selecting date ranges.
 * 
 * ## Features
 * 
 * - **Quick Presets**: Today, Yesterday, Last 7/30 Days, This/Last Month
 * - **Custom Range**: Manual start/end date selection
 * - **Validation**: Min/max date constraints
 * - **Flexible**: Custom presets, disabled state, optional UI elements
 * - **Accessible**: Keyboard navigation, ARIA labels, screen reader support
 * 
 * ## Usage
 * 
 * ```tsx
 * import { DateRangePicker } from '@ghatana/ui';
 * 
 * function MyComponent() {
 *   const [range, setRange] = useState({ startDate: '', endDate: '' });
 *   
 *   return (
 *     <DateRangePicker
 *       onDateRangeChange={setRange}
 *       initialRange={range}
 *     />
 *   );
 * }
 * ```
 */
const meta = {
  title: 'Molecules/DateRangePicker',
  component: DateRangePicker,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'A versatile date range picker with preset options and custom date selection.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    onDateRangeChange: {
      description: 'Callback fired when date range changes',
      table: {
        type: { summary: '(range: DateRange) => void' },
      },
    },
    initialRange: {
      description: 'Initial date range value',
      table: {
        type: { summary: 'DateRange' },
      },
    },
    minDate: {
      control: 'date',
      description: 'Minimum selectable date (ISO format: YYYY-MM-DD)',
      table: {
        type: { summary: 'string' },
      },
    },
    maxDate: {
      control: 'date',
      description: 'Maximum selectable date (ISO format: YYYY-MM-DD)',
      table: {
        type: { summary: 'string' },
      },
    },
    presets: {
      description: 'Custom preset configurations',
      table: {
        type: { summary: 'DateRangePreset[]' },
      },
    },
    disabled: {
      control: 'boolean',
      description: 'Whether the component is disabled',
    },
    showApplyButton: {
      control: 'boolean',
      description: 'Whether to show the apply button for custom ranges',
    },
    showSelectedRange: {
      control: 'boolean',
      description: 'Whether to show the selected range display',
    },
  },
} satisfies Meta<typeof DateRangePicker>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default date range picker with all standard presets.
 */
export const Default: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
  },
};

/**
 * Date range picker with an initial range set.
 */
export const WithInitialRange: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    initialRange: {
      startDate: '2025-01-01',
      endDate: '2025-01-31',
    },
  },
};

/**
 * Date range picker with min/max constraints.
 */
export const WithConstraints: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    minDate: '2024-01-01',
    maxDate: '2025-12-31',
    initialRange: {
      startDate: '2025-01-01',
      endDate: '2025-01-31',
    },
  },
};

/**
 * Date range picker with custom presets (quarters).
 */
export const WithCustomPresets: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    presets: [
      {
        id: 'q1',
        label: 'Q1 2025',
        getValue: () => ({ startDate: '2025-01-01', endDate: '2025-03-31' }),
      },
      {
        id: 'q2',
        label: 'Q2 2025',
        getValue: () => ({ startDate: '2025-04-01', endDate: '2025-06-30' }),
      },
      {
        id: 'q3',
        label: 'Q3 2025',
        getValue: () => ({ startDate: '2025-07-01', endDate: '2025-09-30' }),
      },
      {
        id: 'q4',
        label: 'Q4 2025',
        getValue: () => ({ startDate: '2025-10-01', endDate: '2025-12-31' }),
      },
      {
        id: 'ytd',
        label: 'Year to Date',
        getValue: () => ({ startDate: '2025-01-01', endDate: new Date().toISOString().split('T')[0] }),
      },
      {
        id: 'alltime',
        label: 'All Time',
        getValue: () => ({ startDate: '2020-01-01', endDate: new Date().toISOString().split('T')[0] }),
      },
    ],
  },
};

/**
 * Disabled date range picker.
 */
export const Disabled: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    disabled: true,
    initialRange: {
      startDate: '2025-01-01',
      endDate: '2025-01-31',
    },
  },
};

/**
 * Date range picker without Apply button.
 */
export const WithoutApplyButton: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    showApplyButton: false,
  },
};

/**
 * Date range picker without selected range display.
 */
export const WithoutSelectedDisplay: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    showSelectedRange: false,
  },
};

/**
 * Minimal date range picker (no optional features).
 */
export const Minimal: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    showApplyButton: false,
    showSelectedRange: false,
  },
};

/**
 * Interactive example showing state management.
 */
export const Interactive: Story = {
  render: () => {
    const [selectedRange, setSelectedRange] = useState<DateRange>({
      startDate: '2025-01-01',
      endDate: '2025-01-31',
    });

    return (
      <div className="space-y-4">
        <DateRangePicker
          onDateRangeChange={setSelectedRange}
          initialRange={selectedRange}
        />
        <div className="bg-gray-100 p-4 rounded-lg">
          <p className="font-medium text-gray-900 mb-2">Selected Range:</p>
          <div className="space-y-1 text-sm text-gray-700">
            <p>Start: <span className="font-mono">{selectedRange.startDate}</span></p>
            <p>End: <span className="font-mono">{selectedRange.endDate}</span></p>
          </div>
        </div>
      </div>
    );
  },
};

/**
 * Example with custom styling.
 */
export const CustomStyling: Story = {
  args: {
    onDateRangeChange: (range: DateRange) => console.log('Date range changed:', range),
    className: 'border-2 border-purple-500 shadow-lg',
    initialRange: {
      startDate: '2025-01-01',
      endDate: '2025-01-31',
    },
  },
};

/**
 * Analytics dashboard use case.
 */
export const AnalyticsDashboard: Story = {
  render: () => {
    const [range, setRange] = useState<DateRange>({
      startDate: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      endDate: new Date().toISOString().split('T')[0],
    });

    return (
      <div className="max-w-2xl space-y-4">
        <div className="bg-white p-6 rounded-lg shadow">
          <h2 className="text-xl font-bold mb-4">Analytics Dashboard</h2>
          <DateRangePicker
            onDateRangeChange={setRange}
            initialRange={range}
          />
        </div>
        <div className="bg-blue-50 p-6 rounded-lg">
          <h3 className="text-lg font-semibold mb-3">Data Summary</h3>
          <div className="grid grid-cols-3 gap-4">
            <div className="bg-white p-4 rounded shadow">
              <p className="text-sm text-gray-600">Total Users</p>
              <p className="text-2xl font-bold">12,345</p>
            </div>
            <div className="bg-white p-4 rounded shadow">
              <p className="text-sm text-gray-600">Page Views</p>
              <p className="text-2xl font-bold">45,678</p>
            </div>
            <div className="bg-white p-4 rounded shadow">
              <p className="text-sm text-gray-600">Conversions</p>
              <p className="text-2xl font-bold">789</p>
            </div>
          </div>
          <p className="text-sm text-gray-600 mt-4">
            Data from {range.startDate} to {range.endDate}
          </p>
        </div>
      </div>
    );
  },
};
