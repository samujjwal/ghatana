import React from 'react';

import { Autocomplete } from './Autocomplete';

import type { AutocompleteOption } from './Autocomplete';
import type { Meta, StoryObj } from '@storybook/react';


const meta: Meta<typeof Autocomplete> = {
  title: 'Components/Autocomplete',
  component: Autocomplete,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
    },
    multiple: {
      control: 'boolean',
    },
    disabled: {
      control: 'boolean',
    },
    loading: {
      control: 'boolean',
    },
    error: {
      control: 'boolean',
    },
    clearable: {
      control: 'boolean',
    },
    openOnFocus: {
      control: 'boolean',
    },
    autoHighlight: {
      control: 'boolean',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Autocomplete>;

// Sample data
const fruits: AutocompleteOption[] = [
  { value: 1, label: 'Apple' },
  { value: 2, label: 'Banana' },
  { value: 3, label: 'Cherry' },
  { value: 4, label: 'Date' },
  { value: 5, label: 'Elderberry' },
  { value: 6, label: 'Fig' },
  { value: 7, label: 'Grape' },
  { value: 8, label: 'Honeydew' },
];

const countries: AutocompleteOption[] = [
  { value: 'us', label: 'United States' },
  { value: 'ca', label: 'Canada' },
  { value: 'mx', label: 'Mexico' },
  { value: 'uk', label: 'United Kingdom' },
  { value: 'de', label: 'Germany' },
  { value: 'fr', label: 'France' },
  { value: 'jp', label: 'Japan' },
  { value: 'au', label: 'Australia' },
  { value: 'in', label: 'India' },
  { value: 'cn', label: 'China' },
];

const programmingLanguages: AutocompleteOption[] = [
  { value: 'js', label: 'JavaScript' },
  { value: 'ts', label: 'TypeScript' },
  { value: 'py', label: 'Python' },
  { value: 'java', label: 'Java' },
  { value: 'cpp', label: 'C++' },
  { value: 'cs', label: 'C#' },
  { value: 'go', label: 'Go' },
  { value: 'rust', label: 'Rust' },
  { value: 'rb', label: 'Ruby' },
  { value: 'php', label: 'PHP' },
  { value: 'swift', label: 'Swift' },
  { value: 'kotlin', label: 'Kotlin' },
];

export const Default: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    return (
      <div className="w-[320px]">
        <Autocomplete
          options={fruits}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Select a fruit..."
        />
      </div>
    );
  },
};

export const WithLabel: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Favorite Fruit"
          options={fruits}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Select a fruit..."
        />
      </div>
    );
  },
};

export const Sizes: Story = {
  render: () => {
    const [small, setSmall] = React.useState<string | number>('');
    const [medium, setMedium] = React.useState<string | number>('');
    const [large, setLarge] = React.useState<string | number>('');

    return (
      <div className="flex flex-col gap-4 w-[320px]">
        <Autocomplete
          label="Small"
          size="small"
          options={fruits}
          value={small}
          onChange={(v) => setSmall(v as string | number)}
          placeholder="Select..."
        />
        <Autocomplete
          label="Medium"
          size="medium"
          options={fruits}
          value={medium}
          onChange={(v) => setMedium(v as string | number)}
          placeholder="Select..."
        />
        <Autocomplete
          label="Large"
          size="large"
          options={fruits}
          value={large}
          onChange={(v) => setLarge(v as string | number)}
          placeholder="Select..."
        />
      </div>
    );
  },
};

export const MultipleSelection: Story = {
  render: () => {
    const [value, setValue] = React.useState<(string | number)[]>([]);

    return (
      <div className="w-[400px]">
        <Autocomplete
          label="Select Languages"
          multiple
          options={programmingLanguages}
          value={value}
          onChange={(newValue) => setValue(newValue as (string | number)[])}
          placeholder="Select languages..."
        />
      </div>
    );
  },
};

export const WithHelperText: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Country"
          options={countries}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Select country..."
          helperText="Choose your country of residence"
        />
      </div>
    );
  },
};

export const WithError: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Required Field"
          options={fruits}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Select a fruit..."
          error
          helperText="This field is required"
        />
      </div>
    );
  },
};

export const DisabledState: Story = {
  render: () => {
    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Disabled"
          options={fruits}
          value={1}
          placeholder="Select a fruit..."
          disabled
        />
      </div>
    );
  },
};

export const LoadingState: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Loading Data"
          options={[]}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Search..."
          loading
          loadingText="Fetching options..."
        />
      </div>
    );
  },
};

export const EmptyState: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="No Options"
          options={[]}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Search..."
          noOptionsText="No results found"
        />
      </div>
    );
  },
};

export const CustomFiltering: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    // Custom filter: starts with query
    const startsWithFilter = (option: AutocompleteOption, query: string) => {
      if (!query) return true;
      return option.label.toLowerCase().startsWith(query.toLowerCase());
    };

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Custom Filter (Starts With)"
          options={fruits}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Type to filter..."
          filterFunction={startsWithFilter}
          helperText="Filter shows options that start with your input"
        />
      </div>
    );
  },
};

export const AsyncLoading: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');
    const [options, setOptions] = React.useState<AutocompleteOption[]>([]);
    const [loading, setLoading] = React.useState(false);

    React.useEffect(() => {
      // Simulate API call
      setLoading(true);
      const timer = setTimeout(() => {
        setOptions(programmingLanguages);
        setLoading(false);
      }, 1500);

      return () => clearTimeout(timer);
    }, []);

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Programming Language"
          options={options}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Select language..."
          loading={loading}
          loadingText="Loading languages..."
        />
      </div>
    );
  },
};

export const WithDisabledOptions: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    const optionsWithDisabled: AutocompleteOption[] = [
      { value: 1, label: 'Apple' },
      { value: 2, label: 'Banana (Out of stock)', disabled: true },
      { value: 3, label: 'Cherry' },
      { value: 4, label: 'Date (Coming soon)', disabled: true },
      { value: 5, label: 'Elderberry' },
    ];

    return (
      <div className="w-[320px]">
        <Autocomplete
          label="Available Fruits"
          options={optionsWithDisabled}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Select a fruit..."
        />
      </div>
    );
  },
};

export const FormValidation: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');
    const [touched, setTouched] = React.useState(false);
    const hasError = touched && !value;

    return (
      <div className="w-[320px]">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setTouched(true);
            if (value) {
              alert(`Selected: ${fruits.find((f) => f.value === value)?.label}`);
            }
          }}
        >
          <Autocomplete
            label="Favorite Fruit *"
            options={fruits}
            value={value}
            onChange={(newValue) => {
              setValue(newValue as string | number);
              setTouched(true);
            }}
            placeholder="Select a fruit..."
            error={hasError}
            helperText={hasError ? 'Please select a fruit' : 'Required field'}
          />
          <button
            type="submit"
            className="mt-4 px-4 py-2 bg-primary-500 text-white rounded-md hover:bg-primary-600"
          >
            Submit
          </button>
        </form>
      </div>
    );
  },
};

export const DarkMode: Story = {
  render: () => {
    const [value, setValue] = React.useState<string | number>('');

    return (
      <div className="dark bg-grey-900 p-8 rounded-lg w-[400px]">
        <Autocomplete
          label="Select Country"
          options={countries}
          value={value}
          onChange={(newValue) => setValue(newValue as string | number)}
          placeholder="Search countries..."
          helperText="Start typing to filter"
        />
      </div>
    );
  },
  parameters: {
    backgrounds: { default: 'dark' },
  },
};

export const Playground: Story = {
  args: {
    options: fruits,
    placeholder: 'Select an option...',
    label: 'Autocomplete',
    size: 'medium',
    multiple: false,
    disabled: false,
    loading: false,
    error: false,
    clearable: true,
    openOnFocus: true,
    autoHighlight: true,
    helperText: 'Start typing to filter options',
  },
  render: (args) => {
    const [value, setValue] = React.useState<string | number | (string | number)[]>(
      args.multiple ? [] : ''
    );

    return (
      <div className="w-[400px]">
        <Autocomplete
          {...args}
          value={value}
          onChange={(newValue) => setValue(newValue)}
        />
      </div>
    );
  },
};
