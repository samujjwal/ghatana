/**
 * Enhanced Select Stories
 *
 * Comprehensive demonstrations of Select molecule component with all features
 */


import { Stack, Box, Typography, Grid } from '@ghatana/ui';
import { useState } from 'react';

import { Select, SelectProps } from './Select.enhanced';

import type { SelectOption } from './Select.enhanced';
import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Select> = {
  title: 'Molecules/Select',
  component: Select,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: `
# Select (Molecule)

A composable dropdown select component with validation states and design token integration. Built on MUI Select for enhanced functionality.

## Features
- ✅ WCAG 2.1 AA compliant (44px minimum height)
- ✅ Design token integration
- ✅ Three shape variants (rounded, soft, square)
- ✅ Validation states (success, error, warning)
- ✅ Accessible labels and ARIA attributes
- ✅ Keyboard navigation
- ✅ Native MUI Select features

## Design Tokens Used
- **Border Radius**: \`borderRadiusSm\` (4px), \`borderRadiusMd\` (8px)
- **Spacing**: \`spacingSm\` (8px)
- **Typography**: \`fontSizeSm\` (0.875rem)
        `,
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    shape: {
      control: 'select',
      options: ['rounded', 'soft', 'square'],
      description: 'Select shape variant using design tokens',
    },
    validationState: {
      control: 'select',
      options: [undefined, 'success', 'error', 'warning'],
      description: 'Validation state with visual indicators',
    },
    size: {
      control: 'select',
      options: ['small', 'medium'],
      description: 'Select size (all maintain WCAG min height)',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Select>;

// Sample data
const countryOptions: SelectOption[] = [
  { value: 'us', label: 'United States' },
  { value: 'uk', label: 'United Kingdom' },
  { value: 'ca', label: 'Canada' },
  { value: 'au', label: 'Australia' },
  { value: 'de', label: 'Germany' },
  { value: 'fr', label: 'France' },
  { value: 'jp', label: 'Japan' },
];

const timezoneOptions: SelectOption[] = [
  { value: 'pst', label: 'Pacific Standard Time (PST)' },
  { value: 'mst', label: 'Mountain Standard Time (MST)' },
  { value: 'cst', label: 'Central Standard Time (CST)' },
  { value: 'est', label: 'Eastern Standard Time (EST)' },
  { value: 'utc', label: 'Coordinated Universal Time (UTC)' },
  { value: 'gmt', label: 'Greenwich Mean Time (GMT)' },
];

const languageOptions: SelectOption[] = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Spanish' },
  { value: 'fr', label: 'French' },
  { value: 'de', label: 'German' },
  { value: 'ja', label: 'Japanese' },
  { value: 'zh', label: 'Chinese' },
];

const priorityOptions: SelectOption[] = [
  { value: 'low', label: 'Low Priority' },
  { value: 'medium', label: 'Medium Priority' },
  { value: 'high', label: 'High Priority' },
  { value: 'urgent', label: 'Urgent' },
];

// ============================================================================
// Basic Examples
// ============================================================================

export const Default: Story = {
  args: {
    label: 'Country',
    options: countryOptions,
    fullWidth: true,
  },
};

export const WithValue: Story = {
  args: {
    label: 'Country',
    options: countryOptions,
    value: 'us',
    fullWidth: true,
  },
};

export const Required: Story = {
  args: {
    label: 'Country',
    options: countryOptions,
    required: true,
    helperText: 'This field is required',
    fullWidth: true,
  },
};

export const WithHelperText: Story = {
  args: {
    label: 'Timezone',
    options: timezoneOptions,
    helperText: 'Select your preferred timezone',
    fullWidth: true,
  },
};

export const Disabled: Story = {
  args: {
    label: 'Country',
    options: countryOptions,
    value: 'us',
    disabled: true,
    helperText: 'This field is disabled',
    fullWidth: true,
  },
};

// ============================================================================
// Shape Variants
// ============================================================================

export const ShapeVariants: Story = {
  render: () => (
    <Stack spacing={3}>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Rounded (4px - borderRadiusSm)
        </Typography>
        <Select
          label="Rounded Shape"
          options={countryOptions}
          shape="rounded"
          helperText="Standard rounded corners"
          fullWidth
        />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Soft (8px - borderRadiusMd)
        </Typography>
        <Select
          label="Soft Shape"
          options={countryOptions}
          shape="soft"
          helperText="Softer rounded corners"
          fullWidth
        />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Square (2px)
        </Typography>
        <Select
          label="Square Shape"
          options={countryOptions}
          shape="square"
          helperText="Minimal rounding"
          fullWidth
        />
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Validation States
// ============================================================================

export const ValidationStates: Story = {
  render: () => (
    <Stack spacing={3}>
      <Select
        label="Success State"
        options={countryOptions}
        value="us"
        validationState="success"
        helperText="Country validated successfully"
        fullWidth
      />
      <Select
        label="Error State"
        options={countryOptions}
        value=""
        error
        validationState="error"
        helperText="Please select a country"
        fullWidth
      />
      <Select
        label="Warning State"
        options={countryOptions}
        value="us"
        validationState="warning"
        helperText="This selection may affect other settings"
        fullWidth
      />
    </Stack>
  ),
};

export const ValidationSuccess: Story = {
  args: {
    label: 'Country',
    options: countryOptions,
    value: 'us',
    validationState: 'success',
    helperText: 'Selection validated',
    fullWidth: true,
  },
};

export const ValidationError: Story = {
  args: {
    label: 'Country',
    options: countryOptions,
    error: true,
    validationState: 'error',
    helperText: 'Please select a country',
    fullWidth: true,
  },
};

export const ValidationWarning: Story = {
  args: {
    label: 'Priority',
    options: priorityOptions,
    value: 'urgent',
    validationState: 'warning',
    helperText: 'Urgent tasks require immediate attention',
    fullWidth: true,
  },
};

// ============================================================================
// Interactive Examples
// ============================================================================

export const ControlledSelect: Story = {
  render: () => {
    const [country, setCountry] = useState('');
    const [validationState, setValidationState] = useState<
      'success' | 'error' | 'warning' | undefined
    >(undefined);

    const handleChange = (event: unknown) => {
      const value = event.target.value;
      setCountry(value);
      setValidationState(value ? 'success' : undefined);
    };

    return (
      <Stack spacing={2}>
        <Select
          label="Country"
          options={countryOptions}
          value={country}
          onChange={handleChange}
          validationState={validationState}
          helperText={
            country
              ? `You selected: ${countryOptions.find((o) => o.value === country)?.label}`
              : 'Please select a country'
          }
          fullWidth
        />
        <Typography as="p" className="text-sm" color="text.secondary">
          Current value: {country || '(none)'}
        </Typography>
      </Stack>
    );
  },
};

export const DependentSelects: Story = {
  render: () => {
    const [country, setCountry] = useState('');
    const [language, setLanguage] = useState('');

    return (
      <Stack spacing={3}>
        <Select
          label="Country"
          options={countryOptions}
          value={country}
          onChange={(e) => setCountry(e.target.value as string)}
          helperText="Select your country first"
          fullWidth
        />
        <Select
          label="Language"
          options={languageOptions}
          value={language}
          onChange={(e) => setLanguage(e.target.value as string)}
          disabled={!country}
          helperText={country ? 'Select your preferred language' : 'Please select a country first'}
          fullWidth
        />
      </Stack>
    );
  },
};

// ============================================================================
// Form Example
// ============================================================================

export const ProfileForm: Story = {
  render: () => {
    const [country, setCountry] = useState('');
    const [timezone, setTimezone] = useState('');
    const [language, setLanguage] = useState('');

    return (
      <Box maxWidth={500}>
        <Typography as="h5" gutterBottom>
          Profile Settings
        </Typography>
        <Stack spacing={3} className="mt-4">
          <Select
            label="Country"
            options={countryOptions}
            value={country}
            onChange={(e) => setCountry(e.target.value as string)}
            required
            helperText="Your current location"
            fullWidth
          />
          <Select
            label="Timezone"
            options={timezoneOptions}
            value={timezone}
            onChange={(e) => setTimezone(e.target.value as string)}
            required
            helperText="Used for scheduling and notifications"
            fullWidth
          />
          <Select
            label="Language"
            options={languageOptions}
            value={language}
            onChange={(e) => setLanguage(e.target.value as string)}
            required
            helperText="Interface language preference"
            fullWidth
          />
        </Stack>
      </Box>
    );
  },
};

// ============================================================================
// With Disabled Options
// ============================================================================

export const WithDisabledOptions: Story = {
  render: () => {
    const optionsWithDisabled: SelectOption[] = [
      { value: 'basic', label: 'Basic Plan' },
      { value: 'pro', label: 'Pro Plan (Current)', disabled: true },
      { value: 'enterprise', label: 'Enterprise Plan' },
    ];

    return (
      <Select
        label="Subscription Plan"
        options={optionsWithDisabled}
        value="pro"
        helperText="Your current plan is disabled from selection"
        fullWidth
      />
    );
  },
};

// ============================================================================
// Size Variants
// ============================================================================

export const SizeVariants: Story = {
  render: () => (
    <Stack spacing={3}>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Small Size (still maintains WCAG 44px min height)
        </Typography>
        <Select
          label="Small Select"
          options={countryOptions}
          size="sm"
          helperText="Compact appearance"
          fullWidth
        />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Medium Size (default)
        </Typography>
        <Select
          label="Medium Select"
          options={countryOptions}
          size="md"
          helperText="Standard size"
          fullWidth
        />
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Accessibility Demo
// ============================================================================

export const AccessibilityDemo: Story = {
  render: () => (
    <Stack spacing={4}>
      <Box>
        <Typography as="h6" gutterBottom>
          WCAG 2.1 AA Compliance
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          All selects and menu items maintain minimum 44px height for touch targets
        </Typography>
        <Stack spacing={2}>
          <Select
            label="Small Size (44px min)"
            options={countryOptions}
            size="sm"
            helperText="Maintains accessibility standards"
            fullWidth
          />
          <Select
            label="Medium Size (44px min)"
            options={countryOptions}
            size="md"
            helperText="Default accessible size"
            fullWidth
          />
        </Stack>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Screen Reader Support
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Proper ARIA attributes and label associations
        </Typography>
        <Stack spacing={2}>
          <Select
            label="Required Field"
            options={countryOptions}
            required
            error
            validationState="error"
            helperText="This field is required"
            fullWidth
          />
          <Select
            label="Valid Selection"
            options={countryOptions}
            value="us"
            validationState="success"
            helperText="Selection validated"
            fullWidth
          />
        </Stack>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Keyboard Navigation
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Tab to focus, Enter/Space to open, Arrow keys to navigate, Escape to close
        </Typography>
        <Stack spacing={2}>
          <Select label="Select 1" options={countryOptions} fullWidth />
          <Select label="Select 2" options={timezoneOptions} fullWidth />
          <Select label="Select 3" options={languageOptions} fullWidth />
        </Stack>
      </Box>
    </Stack>
  ),
};

// ============================================================================
// Responsive Grid
// ============================================================================

export const ResponsiveGrid: Story = {
  render: () => (
    <Box>
      <Typography as="h6" gutterBottom>
        User Preferences
      </Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6}>
          <Select
            label="Country"
            options={countryOptions}
            helperText="Your location"
            fullWidth
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <Select
            label="Timezone"
            options={timezoneOptions}
            helperText="Your timezone"
            fullWidth
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <Select
            label="Language"
            options={languageOptions}
            helperText="Interface language"
            fullWidth
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <Select
            label="Priority"
            options={priorityOptions}
            helperText="Default priority"
            fullWidth
          />
        </Grid>
      </Grid>
    </Box>
  ),
};

// ============================================================================
// Interactive Playground
// ============================================================================

export const Playground: Story = {
  args: {
    label: 'Playground',
    options: countryOptions,
    shape: 'rounded',
    fullWidth: true,
  },
};
