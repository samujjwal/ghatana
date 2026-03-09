/**
 * Enhanced TextField Stories
 *
 * Comprehensive demonstrations of TextField molecule component with all features
 */

import { Mail as EmailIcon } from 'lucide-react';
import { Lock as LockIcon } from 'lucide-react';
import { User as PersonIcon } from 'lucide-react';
import { Search as SearchIcon } from 'lucide-react';
import { Eye as VisibilityIcon } from 'lucide-react';
import { Stack, Box, Typography, Grid } from '@ghatana/ui';
import { useState } from 'react';

import { TextField, TextFieldProps } from './TextField.enhanced';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof TextField> = {
  title: 'Molecules/TextField',
  component: TextField,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: `
# TextField (Molecule)

A composable text input component with label, helper text, validation, and icon support. Integrates design tokens from @ghatana/yappc-shared-ui-core/tokens.

## Features
- ✅ WCAG 2.1 AA compliant (44px minimum height)
- ✅ Design token integration
- ✅ Three shape variants (rounded, soft, square)
- ✅ Validation states (success, error, warning)
- ✅ Character count with max length
- ✅ Clearable input
- ✅ Start/end icon support
- ✅ Icon click handlers
- ✅ Accessible labels and ARIA attributes

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
      description: 'Input shape variant using design tokens',
    },
    validationState: {
      control: 'select',
      options: [undefined, 'success', 'error', 'warning'],
      description: 'Validation state with visual indicators',
    },
    variant: {
      control: 'select',
      options: ['outlined', 'filled', 'standard'],
      description: 'MUI TextField variant',
    },
    size: {
      control: 'select',
      options: ['small', 'medium'],
      description: 'Input size (all maintain WCAG min height)',
    },
  },
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof TextField>;

// ============================================================================
// Basic Examples
// ============================================================================

export const Default: Story = {
  args: {
    label: 'Username',
    placeholder: 'Enter your username',
  },
};

export const Required: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    type: 'email',
    required: true,
    helperText: 'This field is required',
  },
};

export const FullWidth: Story = {
  args: {
    label: 'Full Width Input',
    placeholder: 'This input spans the full width',
    fullWidth: true,
  },
};

export const Multiline: Story = {
  args: {
    label: 'Message',
    placeholder: 'Enter your message',
    multiline: true,
    rows: 4,
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
        <TextField
          label="Rounded Shape"
          placeholder="Standard rounded corners"
          shape="rounded"
          fullWidth
        />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Soft (8px - borderRadiusMd)
        </Typography>
        <TextField
          label="Soft Shape"
          placeholder="Softer rounded corners"
          shape="soft"
          fullWidth
        />
      </Box>
      <Box>
        <Typography as="p" className="text-sm font-medium" gutterBottom>
          Square (2px)
        </Typography>
        <TextField
          label="Square Shape"
          placeholder="Minimal rounding"
          shape="square"
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
      <TextField
        label="Success State"
        value="valid-username"
        validationState="success"
        helperText="Username is available!"
        fullWidth
      />
      <TextField
        label="Error State"
        value="invalid"
        error
        validationState="error"
        helperText="This username is already taken"
        fullWidth
      />
      <TextField
        label="Warning State"
        value="username123"
        validationState="warning"
        helperText="Consider using a more secure username"
        fullWidth
      />
    </Stack>
  ),
};

export const ValidationSuccess: Story = {
  args: {
    label: 'Username',
    value: 'john_doe',
    validationState: 'success',
    helperText: 'Username is available!',
    fullWidth: true,
  },
};

export const ValidationError: Story = {
  args: {
    label: 'Email',
    value: 'invalid-email',
    error: true,
    validationState: 'error',
    helperText: 'Please enter a valid email address',
    fullWidth: true,
  },
};

export const ValidationWarning: Story = {
  args: {
    label: 'Password',
    value: '123456',
    type: 'password',
    validationState: 'warning',
    helperText: 'Consider using a stronger password',
    fullWidth: true,
  },
};

// ============================================================================
// Character Count
// ============================================================================

export const WithCharacterCount: Story = {
  render: () => {
    const [value, setValue] = useState('Hello World');

    return (
      <Stack spacing={3}>
        <TextField
          label="Tweet"
          placeholder="What's happening?"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          showCharacterCount
          maxLength={280}
          multiline
          rows={4}
          fullWidth
          helperText="Share your thoughts"
        />
        <TextField
          label="Username"
          placeholder="Choose a username"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          showCharacterCount
          maxLength={20}
          fullWidth
          helperText="Letters, numbers, and underscores only"
        />
      </Stack>
    );
  },
};

// ============================================================================
// Clearable Input
// ============================================================================

export const ClearableInput: Story = {
  render: () => {
    const [value, setValue] = useState('This text can be cleared');

    return (
      <Stack spacing={3}>
        <TextField
          label="Search"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          clearable
          onClear={() => setValue('')}
          fullWidth
          helperText="Click the × button to clear"
        />
        <Typography as="p" className="text-sm" color="text.secondary">
          Current value: "{value}"
        </Typography>
      </Stack>
    );
  },
};

// ============================================================================
// Icons
// ============================================================================

export const WithStartIcon: Story = {
  args: {
    label: 'Search',
    placeholder: 'Search...',
    startIcon: <SearchIcon />,
    fullWidth: true,
  },
};

export const WithEndIcon: Story = {
  args: {
    label: 'Email',
    placeholder: 'Enter your email',
    type: 'email',
    endIcon: <EmailIcon />,
    fullWidth: true,
  },
};

export const WithBothIcons: Story = {
  args: {
    label: 'Username',
    placeholder: 'Enter username',
    startIcon: <PersonIcon />,
    validationState: 'success',
    fullWidth: true,
  },
};

export const IconExamples: Story = {
  render: () => (
    <Stack spacing={3}>
      <TextField
        label="Search"
        placeholder="Search products..."
        startIcon={<SearchIcon />}
        clearable
        fullWidth
      />
      <TextField
        label="Email Address"
        placeholder="you@example.com"
        type="email"
        startIcon={<EmailIcon />}
        validationState="success"
        helperText="Email verified"
        fullWidth
      />
      <TextField
        label="Username"
        placeholder="Choose username"
        startIcon={<PersonIcon />}
        showCharacterCount
        maxLength={20}
        fullWidth
      />
    </Stack>
  ),
};

export const ClickableEndIcon: Story = {
  render: () => {
    const [showPassword, setShowPassword] = useState(false);

    return (
      <Stack spacing={2}>
        <TextField
          label="Password"
          type={showPassword ? 'text' : 'password'}
          placeholder="Enter password"
          startIcon={<LockIcon />}
          endIcon={<VisibilityIcon />}
          onEndIconClick={() => setShowPassword(!showPassword)}
          fullWidth
          helperText="Click the eye icon to toggle visibility"
        />
        <Typography as="p" className="text-sm" color="text.secondary">
          Password is currently: {showPassword ? 'visible' : 'hidden'}
        </Typography>
      </Stack>
    );
  },
};

// ============================================================================
// Combined Features
// ============================================================================

export const FullFeatured: Story = {
  render: () => {
    const [value, setValue] = useState('');
    const [validationState, setValidationState] = useState<
      'success' | 'error' | 'warning' | undefined
    >(undefined);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      const newValue = e.target.value;
      setValue(newValue);

      // Validation logic
      if (newValue.length === 0) {
        setValidationState(undefined);
      } else if (newValue.length < 3) {
        setValidationState('error');
      } else if (newValue.length < 8) {
        setValidationState('warning');
      } else {
        setValidationState('success');
      }
    };

    const getHelperText = () => {
      if (validationState === 'error') return 'Username must be at least 3 characters';
      if (validationState === 'warning') return 'Recommended minimum is 8 characters';
      if (validationState === 'success') return 'Username looks good!';
      return 'Choose a unique username';
    };

    return (
      <Box maxWidth={500}>
        <TextField
          label="Username"
          placeholder="Enter username"
          value={value}
          onChange={handleChange}
          startIcon={<PersonIcon />}
          clearable
          onClear={() => {
            setValue('');
            setValidationState(undefined);
          }}
          validationState={validationState}
          showCharacterCount
          maxLength={20}
          shape="soft"
          fullWidth
          required
          helperText={getHelperText()}
        />
      </Box>
    );
  },
};

// ============================================================================
// Form Example
// ============================================================================

export const LoginForm: Story = {
  render: () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);

    return (
      <Box maxWidth={400}>
        <Typography as="h5" gutterBottom>
          Sign In
        </Typography>
        <Stack spacing={3} className="mt-4">
          <TextField
            label="Email"
            type="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            startIcon={<EmailIcon />}
            clearable
            onClear={() => setEmail('')}
            required
            fullWidth
          />
          <TextField
            label="Password"
            type={showPassword ? 'text' : 'password'}
            placeholder="Enter password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            startIcon={<LockIcon />}
            endIcon={<VisibilityIcon />}
            onEndIconClick={() => setShowPassword(!showPassword)}
            required
            fullWidth
            helperText="At least 8 characters"
          />
        </Stack>
      </Box>
    );
  },
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
          All inputs maintain minimum 44px height for touch targets
        </Typography>
        <Stack spacing={2}>
          <TextField
            label="Small Size (still 44px min)"
            size="sm"
            fullWidth
            helperText="Maintains accessibility standards"
          />
          <TextField
            label="Medium Size (44px min)"
            size="md"
            fullWidth
            helperText="Default accessible size"
          />
        </Stack>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Screen Reader Support
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Proper ARIA attributes and associations
        </Typography>
        <Stack spacing={2}>
          <TextField
            label="Required Field"
            required
            error
            validationState="error"
            helperText="This field is required"
            fullWidth
          />
          <TextField
            label="Success State"
            value="Valid input"
            validationState="success"
            helperText="Input validated successfully"
            fullWidth
          />
        </Stack>
      </Box>

      <Box>
        <Typography as="h6" gutterBottom>
          Keyboard Navigation
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" paragraph>
          Tab to focus, Escape to clear (when clearable)
        </Typography>
        <Stack spacing={2}>
          <TextField label="Field 1" placeholder="Tab to navigate" fullWidth clearable />
          <TextField label="Field 2" placeholder="Tab to navigate" fullWidth clearable />
          <TextField label="Field 3" placeholder="Tab to navigate" fullWidth clearable />
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
        Contact Information
      </Typography>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6}>
          <TextField label="First Name" placeholder="John" required fullWidth />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextField label="Last Name" placeholder="Doe" required fullWidth />
        </Grid>
        <Grid item xs={12}>
          <TextField
            label="Email"
            type="email"
            placeholder="john.doe@example.com"
            startIcon={<EmailIcon />}
            required
            fullWidth
          />
        </Grid>
        <Grid item xs={12}>
          <TextField
            label="Message"
            placeholder="Tell us what you think..."
            multiline
            rows={4}
            showCharacterCount
            maxLength={500}
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
    placeholder: 'Customize me!',
    shape: 'rounded',
    fullWidth: true,
  },
};
