import { Input } from './Input';

import type { Meta, StoryObj } from '@storybook/react-vite';

const meta: Meta<typeof Input> = {
  title: 'Components/Input',
  component: Input,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: 'select',
      options: ['small', 'medium', 'large'],
      description: 'The size of the input',
    },
    type: {
      control: 'select',
      options: ['text', 'email', 'password', 'number', 'tel', 'url'],
      description: 'The type of input',
    },
    disabled: {
      control: 'boolean',
      description: 'If true, the input is disabled',
    },
    required: {
      control: 'boolean',
      description: 'If true, the input is required',
    },
    fullWidth: {
      control: 'boolean',
      description: 'If true, the input takes up the full width',
    },
    showCounter: {
      control: 'boolean',
      description: 'If true, shows character counter',
    },
    clearable: {
      control: 'boolean',
      description: 'If true, shows a clear button when input has a value',
    },
    showPasswordToggle: {
      control: 'boolean',
      description: 'If true, shows a toggle to show/hide password (only for password inputs)',
    },
    loading: {
      control: 'boolean',
      description: 'If true, shows a loading spinner',
    },
    autocomplete: {
      control: 'boolean',
      description: 'If true, enables autocomplete functionality',
    },
    mask: {
      control: 'select',
      description: 'Type of input mask to apply',
    },
    format: {
      control: 'select',
      options: [undefined, 'uppercase', 'lowercase', 'capitalize', 'number', 'percent', 'email', 'trim', 'custom'],
      description: 'Type of formatting to apply',
    },
    formatOnBlur: {
      control: 'boolean',
      description: 'If true, formatting is applied on blur instead of on every change',
    },
    debounced: {
      control: 'boolean',
      description: 'If true, enables debounced input',
    },
    debounceDelay: {
      control: 'number',
      description: 'Debounce delay in milliseconds',
    },
    errorSeverity: {
      control: 'select',
      options: ['error', 'warning', 'info'],
      description: 'Severity level for error messages',
    },
    showErrorIcon: {
      control: 'boolean',
      description: 'If true, shows an icon next to the error message',
    },
    ariaDescription: {
      control: 'text',
      description: 'Additional ARIA description for the input',
    },
    announceChanges: {
      control: 'boolean',
      description: 'Announce changes to screen readers',
    },
    theme: {
      control: 'select',
      options: ['light', 'dark', 'system'],
      description: 'Theme for the input',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    placeholder: 'Enter text...',
  },
};

export const WithLabel: Story = {
  args: {
    label: 'Email Address',
    placeholder: 'Enter your email',
    type: 'email',
  },
};

export const WithHelperText: Story = {
  args: {
    label: 'Username',
    placeholder: 'Choose a username',
    helperText: 'Username must be at least 3 characters',
  },
};

export const WithError: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    error: 'Please enter a valid email address',
    type: 'email',
  },
};

export const Required: Story = {
  args: {
    label: 'Password',
    type: 'password',
    required: true,
    placeholder: 'Enter your password',
  },
};

export const WithCounter: Story = {
  args: {
    label: 'Bio',
    placeholder: 'Tell us about yourself',
    showCounter: true,
    maxLength: 100,
    helperText: 'Maximum 100 characters',
  },
};

export const Small: Story = {
  args: {
    label: 'Small Input',
    size: 'small',
    placeholder: 'Small size',
  },
};

export const Medium: Story = {
  args: {
    label: 'Medium Input',
    size: 'medium',
    placeholder: 'Medium size',
  },
};

export const Large: Story = {
  args: {
    label: 'Large Input',
    size: 'large',
    placeholder: 'Large size',
  },
};

export const FullWidth: Story = {
  args: {
    label: 'Full Width Input',
    fullWidth: true,
    placeholder: 'This input spans full width',
  },
  parameters: {
    layout: 'padded',
  },
};

export const Disabled: Story = {
  args: {
    label: 'Disabled Input',
    placeholder: 'This input is disabled',
    disabled: true,
    value: 'Cannot edit this',
  },
};

export const Password: Story = {
  args: {
    label: 'Password',
    type: 'password',
    placeholder: 'Enter your password',
    helperText: 'Password must be at least 8 characters',
  },
};

export const PasswordWithVisibilityToggle: Story = {
  args: {
    label: 'Password',
    type: 'password',
    placeholder: 'Enter your password',
    helperText: 'Password must be at least 8 characters',
    showPasswordToggle: true,
    defaultValue: 'SecurePassword123!',
  },
};

export const PasswordWithVisibilityAndClear: Story = {
  args: {
    label: 'Password',
    type: 'password',
    placeholder: 'Enter your password',
    helperText: 'Password must be at least 8 characters',
    showPasswordToggle: true,
    clearable: true,
    defaultValue: 'SecurePassword123!',
  },
};

export const Number: Story = {
  args: {
    label: 'Age',
    type: 'number',
    placeholder: 'Enter your age',
    min: 0,
    max: 120,
  },
};

export const WithStartIcon: Story = {
  args: {
    label: 'Search',
    placeholder: 'Search...',
    startIcon: (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
    ),
  },
};

export const WithEndIcon: Story = {
  args: {
    label: 'Email',
    type: 'email',
    placeholder: 'Enter your email',
    endIcon: (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
        <polyline points="22 4 12 14.01 9 11.01" />
      </svg>
    ),
  },
};

export const Clearable: Story = {
  args: {
    label: 'Search',
    placeholder: 'Type to search...',
    clearable: true,
    defaultValue: 'Initial search term',
  },
};

export const ClearableWithIcon: Story = {
  args: {
    label: 'Search with icon',
    placeholder: 'Type to search...',
    clearable: true,
    defaultValue: 'Search term',
    startIcon: (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
    ),
  },
};

export const Loading: Story = {
  args: {
    label: 'Loading Input',
    placeholder: 'Loading...',
    loading: true,
  },
};

export const LoadingWithValue: Story = {
  args: {
    label: 'Loading Input with Value',
    defaultValue: 'Loading this value...',
    loading: true,
  },
};

export const LoadingWithIcon: Story = {
  args: {
    label: 'Loading with Icon',
    placeholder: 'Loading with icon...',
    loading: true,
    startIcon: (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
    ),
  },
};

export const Autocomplete: Story = {
  args: {
    label: 'Country',
    placeholder: 'Start typing a country name...',
    autocomplete: true,
    options: [
      { value: 'us', label: 'United States' },
      { value: 'ca', label: 'Canada' },
      { value: 'mx', label: 'Mexico' },
      { value: 'uk', label: 'United Kingdom' },
      { value: 'fr', label: 'France' },
      { value: 'de', label: 'Germany' },
      { value: 'it', label: 'Italy' },
      { value: 'es', label: 'Spain' },
      { value: 'jp', label: 'Japan' },
      { value: 'cn', label: 'China' },
    ],
    helperText: 'Select a country from the list',
  },
};

export const AutocompleteWithClear: Story = {
  args: {
    label: 'Country',
    placeholder: 'Start typing a country name...',
    autocomplete: true,
    clearable: true,
    options: [
      { value: 'us', label: 'United States' },
      { value: 'ca', label: 'Canada' },
      { value: 'mx', label: 'Mexico' },
      { value: 'uk', label: 'United Kingdom' },
      { value: 'fr', label: 'France' },
      { value: 'de', label: 'Germany' },
      { value: 'it', label: 'Italy' },
      { value: 'es', label: 'Spain' },
      { value: 'jp', label: 'Japan' },
      { value: 'cn', label: 'China' },
    ],
    helperText: 'Select a country from the list',
  },
};

export const AutocompleteWithIcon: Story = {
  args: {
    label: 'Search',
    placeholder: 'Search...',
    autocomplete: true,
    startIcon: (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
    ),
    options: [
      { value: 'react', label: 'React' },
      { value: 'angular', label: 'Angular' },
      { value: 'vue', label: 'Vue' },
      { value: 'svelte', label: 'Svelte' },
      { value: 'ember', label: 'Ember' },
    ],
    helperText: 'Search for a JavaScript framework',
  },
};

export const PhoneMask: Story = {
  args: {
    label: 'Phone Number',
    placeholder: '(123) 456-7890',
    mask: 'phone',
    helperText: 'Enter your phone number',
  },
};

export const DateMask: Story = {
  args: {
    label: 'Date',
    placeholder: 'MM/DD/YYYY',
    mask: 'date',
    helperText: 'Enter a date in MM/DD/YYYY format',
  },
};

export const CreditCardMask: Story = {
  args: {
    label: 'Credit Card',
    placeholder: 'XXXX XXXX XXXX XXXX',
    mask: 'creditCard',
    helperText: 'Enter your credit card number',
  },
};

export const ZipCodeMask: Story = {
  args: {
    label: 'Zip Code',
    placeholder: '12345-6789',
    mask: 'zipCode',
    helperText: 'Enter your zip code',
  },
};

export const CurrencyMask: Story = {
  args: {
    label: 'Amount',
    placeholder: '$0.00',
    mask: 'currency',
    helperText: 'Enter an amount',
  },
};

export const CustomMask: Story = {
  args: {
    label: 'Product Code',
    placeholder: 'ABC-12345',
    mask: 'custom',
    maskPattern: 'AAA-#####',
    helperText: 'Enter a product code (3 letters followed by 5 numbers)',
  },
};

export const UppercaseFormat: Story = {
  args: {
    label: 'Username',
    placeholder: 'Enter username',
    format: 'uppercase',
    helperText: 'Username will be converted to uppercase',
  },
};

export const LowercaseFormat: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    format: 'lowercase',
    helperText: 'Email will be converted to lowercase',
  },
};

export const CapitalizeFormat: Story = {
  args: {
    label: 'Full Name',
    placeholder: 'Enter your full name',
    format: 'capitalize',
    helperText: 'Name will be properly capitalized',
    defaultValue: 'john doe',
  },
};

export const NumberFormat: Story = {
  args: {
    label: 'Amount',
    placeholder: 'Enter amount',
    format: 'number',
    helperText: 'Numbers will be formatted with commas',
  },
};

export const PercentFormat: Story = {
  args: {
    label: 'Percentage',
    placeholder: 'Enter percentage',
    format: 'percent',
    helperText: 'Number will be formatted as percentage',
  },
};

export const FormatOnBlur: Story = {
  args: {
    label: 'Name',
    placeholder: 'Enter your name',
    format: 'capitalize',
    formatOnBlur: true,
    helperText: 'Name will be capitalized when you tab out of the field',
  },
};

export const CustomFormat: Story = {
  args: {
    label: 'Custom Format',
    placeholder: 'Type something...',
    format: 'custom',
    formatter: (value) => `🔥 ${value} 🔥`,
    helperText: 'Text will be surrounded by fire emojis',
  },
};

export const DebouncedInput: Story = {
  args: {
    label: 'Search',
    placeholder: 'Type to search...',
    debounced: true,
    debounceDelay: 500,
    helperText: 'Search will be triggered 500ms after you stop typing',
  },
  parameters: {
    docs: {
      description: {
        story: 'Input with debounced value changes. The onDebouncedChange callback will be called 500ms after the user stops typing.',
      },
    },
  },
};

export const DebouncedWithClear: Story = {
  args: {
    label: 'Search',
    placeholder: 'Type to search...',
    debounced: true,
    debounceDelay: 300,
    clearable: true,
    defaultValue: 'Initial search term',
    helperText: 'Search with debounced input and clear button',
  },
};

export const DebouncedWithIcon: Story = {
  args: {
    label: 'Search',
    placeholder: 'Type to search...',
    debounced: true,
    debounceDelay: 300,
    startIcon: (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
    ),
    helperText: 'Search with debounced input and search icon',
  },
};

export const KeyboardNavigation: Story = {
  args: {
    label: 'Interactive Input',
    keyboardNavigation: true,
    placeholder: 'Press keys to see events',
    helperText: 'Try pressing Enter, Tab, Escape, or Arrow keys',
  },
  parameters: {
    docs: {
      description: {
        story: 'Input with keyboard navigation. Callbacks are triggered when specific keys are pressed.',
      },
    },
  },
};

export const KeyboardNavigationWithAutocomplete: Story = {
  args: {
    label: 'Country',
    placeholder: 'Start typing a country name...',
    keyboardNavigation: true,
    autocomplete: true,
    options: [
      { value: 'us', label: 'United States' },
      { value: 'ca', label: 'Canada' },
      { value: 'mx', label: 'Mexico' },
      { value: 'uk', label: 'United Kingdom' },
      { value: 'fr', label: 'France' },
    ],
    helperText: 'Use arrow keys to navigate options and Enter to select',
  },
};

export const FullFeatured: Story = {
  args: {
    label: 'Search',
    placeholder: 'Type to search...',
    clearable: true,
    debounced: true,
    keyboardNavigation: true,
    autocomplete: true,
    startIcon: (
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
    ),
    options: [
      { value: 'react', label: 'React' },
      { value: 'angular', label: 'Angular' },
      { value: 'vue', label: 'Vue' },
      { value: 'svelte', label: 'Svelte' },
      { value: 'ember', label: 'Ember' },
    ],
    helperText: 'Full-featured input with all capabilities enabled',
  },
};

export const WithAriaLabel: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    ariaLabel: 'Email address input field',
    helperText: 'We will never share your email',
  },
  parameters: {
    docs: {
      description: {
        story: 'Input with an additional ARIA label for screen readers.',
      },
    },
  },
};

export const WithAriaDescription: Story = {
  args: {
    label: 'Password',
    type: 'password',
    placeholder: 'Enter your password',
    ariaDescription: 'Password must contain at least 8 characters, including uppercase, lowercase, and numbers',
    helperText: 'Password must be secure',
  },
  parameters: {
    docs: {
      description: {
        story: 'Input with an additional ARIA description for screen readers.',
      },
    },
  },
};

export const WithAnnouncements: Story = {
  args: {
    label: 'Search',
    placeholder: 'Type to search...',
    announceChanges: true,
    helperText: 'Changes will be announced to screen readers',
  },
  parameters: {
    docs: {
      description: {
        story: 'Input that announces changes to screen readers.',
      },
    },
  },
};

export const FullyAccessible: Story = {
  args: {
    label: 'Search',
    placeholder: 'Type to search...',
    ariaLabel: 'Search input field',
    ariaDescription: 'Type to search for products, press Enter to submit',
    announceChanges: true,
    clearable: true,
    keyboardNavigation: true,
    autocomplete: true,
    options: [
      { value: 'laptop', label: 'Laptop' },
      { value: 'phone', label: 'Phone' },
      { value: 'tablet', label: 'Tablet' },
      { value: 'desktop', label: 'Desktop' },
      { value: 'accessories', label: 'Accessories' },
    ],
    helperText: 'Fully accessible input with all accessibility features enabled',
  },
};

export const LightTheme: Story = {
  args: {
    label: 'Username',
    placeholder: 'Enter your username',
    theme: 'light',
    helperText: 'Input with light theme',
  },
  globals: {
    backgrounds: {
      value: "light"
    }
  },
};

export const DarkTheme: Story = {
  args: {
    label: 'Username',
    placeholder: 'Enter your username',
    theme: 'dark',
    helperText: 'Input with dark theme',
  },
  globals: {
    backgrounds: {
      value: "dark"
    }
  },
};

export const SystemTheme: Story = {
  args: {
    label: 'Username',
    placeholder: 'Enter your username',
    theme: 'system',
    helperText: 'Input with system theme (follows OS preference)',
  },
};

export const DarkThemeWithError: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    theme: 'dark',
    error: 'Invalid email format',
  },
  globals: {
    backgrounds: {
      value: "dark"
    }
  },
};

export const DarkThemeWithAutocomplete: Story = {
  args: {
    label: 'Country',
    placeholder: 'Start typing a country name...',
    theme: 'dark',
    autocomplete: true,
    options: [
      { value: 'us', label: 'United States' },
      { value: 'ca', label: 'Canada' },
      { value: 'mx', label: 'Mexico' },
      { value: 'uk', label: 'United Kingdom' },
      { value: 'fr', label: 'France' },
    ],
    helperText: 'Dark theme with autocomplete',
  },
  globals: {
    backgrounds: {
      value: "dark"
    }
  },
};

export const MobileOptimized: Story = {
  args: {
    label: 'Search',
    placeholder: 'Search...',
    mobileOptimized: true,
    helperText: 'This input is optimized for mobile devices',
  },
  parameters: {
    viewport: { defaultViewport: 'mobile1' },
    docs: {
      description: {
        story: 'Input optimized for mobile devices with larger touch targets and improved mobile UX.',
      },
    },
  },
};

export const MobileOptimizedWithAutocomplete: Story = {
  args: {
    label: 'Country',
    placeholder: 'Select a country...',
    mobileOptimized: true,
    autocomplete: true,
    options: [
      { value: 'us', label: 'United States' },
      { value: 'ca', label: 'Canada' },
      { value: 'mx', label: 'Mexico' },
      { value: 'uk', label: 'United Kingdom' },
      { value: 'fr', label: 'France' },
      { value: 'de', label: 'Germany' },
      { value: 'it', label: 'Italy' },
      { value: 'es', label: 'Spain' },
      { value: 'jp', label: 'Japan' },
    ],
    helperText: 'Mobile-optimized autocomplete dropdown',
  },
  parameters: {
    viewport: { defaultViewport: 'mobile1' },
  },
};

export const MobileOptimizedWithMask: Story = {
  args: {
    label: 'Phone Number',
    placeholder: '(123) 456-7890',
    mobileOptimized: true,
    mask: 'phone',
    helperText: 'Enter your phone number',
    type: 'tel',
  },
  parameters: {
    viewport: { defaultViewport: 'mobile1' },
  },
};

export const ResponsiveInput: Story = {
  args: {
    label: 'Responsive Input',
    placeholder: 'Resize your browser to see changes',
    mobileOptimized: true,
    helperText: 'This input adapts to different screen sizes',
    fullWidth: true,
  },
};

export const ErrorMessage: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    error: 'Invalid email format',
    type: 'email',
  },
  parameters: {
    docs: {
      description: {
        story: 'Input with an error message.',
      },
    },
  },
};

export const WarningMessage: Story = {
  args: {
    label: 'Username',
    placeholder: 'Enter your username',
    error: 'Username is already taken',
    errorSeverity: 'warning',
  },
};

export const InfoMessage: Story = {
  args: {
    label: 'Password',
    type: 'password',
    placeholder: 'Enter your password',
    error: 'Password strength: Medium',
    errorSeverity: 'info',
  },
};

export const ErrorWithoutIcon: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    error: 'Invalid email format',
    showErrorIcon: false,
  },
};

export const ErrorWithDarkTheme: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    error: 'Invalid email format',
    theme: 'dark',
  },
  globals: {
    backgrounds: {
      value: "dark"
    }
  },
};

export const AllErrorSeverities: Story = {
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <Input
        label="Error Example"
        placeholder="Enter value"
        error="This is an error message"
        errorSeverity="error"
      />
      <Input
        label="Warning Example"
        placeholder="Enter value"
        error="This is a warning message"
        errorSeverity="warning"
      />
      <Input
        label="Info Example"
        placeholder="Enter value"
        error="This is an info message"
        errorSeverity="info"
      />
    </div>
  ),
};
