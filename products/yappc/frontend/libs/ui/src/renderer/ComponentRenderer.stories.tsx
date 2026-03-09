/**
 * Component Renderer Stories
 *
 * Demonstrates JSON-to-React rendering capabilities
 */

import { Box, Typography } from '@ghatana/ui';
import { useState } from 'react';

import { ComponentRenderer } from './ComponentRenderer';

import type { ComponentSchema } from './ComponentRenderer';
import type { Meta, StoryObj } from '@storybook/react';


const meta: Meta<typeof ComponentRenderer> = {
  title: 'Renderer/ComponentRenderer',
  component: ComponentRenderer,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component: `
# Component Renderer

Renders React UI components from JSON schema definitions with data binding and event handling.

## Features
- ✅ Supports all atomic design components
- ✅ Data binding from context
- ✅ Event handler attachment
- ✅ Conditional rendering
- ✅ Nested component composition
- ✅ Custom component registry

## Use Cases
- Dynamic UI generation
- No-code/low-code builders
- CMS-driven interfaces
- Configuration-based UIs
- Template systems
        `,
      },
    },
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof ComponentRenderer>;

// ============================================================================
// Basic Examples
// ============================================================================

export const SimpleButton: Story = {
  args: {
    schema: {
      type: 'Button',
      props: {
        variant: 'contained',
        color: 'primary',
      },
      children: 'Click Me',
    },
  },
};

export const TextField: Story = {
  args: {
    schema: {
      type: 'TextField',
      props: {
        label: 'Username',
        placeholder: 'Enter your username',
        fullWidth: true,
      },
    },
  },
};

export const Card: Story = {
  args: {
    schema: {
      type: 'Card',
      props: {
        sx: { p: 3, maxWidth: 400 },
      },
      children: [
        {
          type: 'Typography',
          props: { variant: 'h5', gutterBottom: true },
          children: 'Welcome',
        },
        {
          type: 'Typography',
          props: { variant: 'body1' },
          children: 'This card is rendered from JSON schema.',
        },
      ],
    },
  },
};

// ============================================================================
// Data Binding
// ============================================================================

export const WithDataBinding: Story = {
  render: () => {
    const schema: ComponentSchema = {
      type: 'Stack',
      props: { spacing: 2 },
      children: [
        {
          type: 'TextField',
          props: {
            label: 'User Name',
            fullWidth: true,
          },
          dataBinding: {
            source: 'user',
            path: 'name',
          },
        },
        {
          type: 'TextField',
          props: {
            label: 'User Email',
            fullWidth: true,
          },
          dataBinding: {
            source: 'user',
            path: 'email',
          },
        },
      ],
    };

    const context = {
      data: {
        user: {
          name: 'John Doe',
          email: 'john@example.com',
        },
      },
    };

    return (
      <Box maxWidth={400}>
        <Typography as="h6" gutterBottom>
          Data Binding Example
        </Typography>
        <ComponentRenderer schema={schema} context={context} />
      </Box>
    );
  },
};

// ============================================================================
// Event Handling
// ============================================================================

export const WithEventHandlers: Story = {
  render: () => {
    const [message, setMessage] = useState('');

    const schema: ComponentSchema = {
      type: 'Stack',
      props: { spacing: 2 },
      children: [
        {
          type: 'Button',
          props: { variant: 'contained', color: 'primary' },
          children: 'Click Me',
          events: { onClick: 'handleClick' },
        },
        {
          type: 'Button',
          props: { variant: 'outlined', color: 'secondary' },
          children: 'Reset',
          events: { onClick: 'handleReset' },
        },
      ],
    };

    const context = {
      handlers: {
        handleClick: () => setMessage('Button clicked!'),
        handleReset: () => setMessage(''),
      },
    };

    return (
      <Box>
        <Typography as="h6" gutterBottom>
          Event Handling Example
        </Typography>
        <ComponentRenderer schema={schema} context={context} />
        {message && (
          <Typography as="p" className="mt-4" color="success.main">
            {message}
          </Typography>
        )}
      </Box>
    );
  },
};

// ============================================================================
// Complex Examples
// ============================================================================

export const ComplexForm: Story = {
  render: () => {
    const [formData, setFormData] = useState({
      name: '',
      email: '',
      message: '',
    });
    const [submitted, setSubmitted] = useState(false);

    const schema: ComponentSchema = {
      type: 'Card',
      props: { sx: { p: 3, maxWidth: 500 } },
      children: [
        {
          type: 'Typography',
          props: { variant: 'h5', gutterBottom: true },
          children: 'Contact Form',
        },
        {
          type: 'Stack',
          props: { spacing: 2, sx: { mt: 2 } },
          children: [
            {
              type: 'TextField',
              props: {
                label: 'Name',
                required: true,
                fullWidth: true,
              },
              dataBinding: { source: 'form', path: 'name' },
            },
            {
              type: 'TextField',
              props: {
                label: 'Email',
                type: 'email',
                required: true,
                fullWidth: true,
              },
              dataBinding: { source: 'form', path: 'email' },
            },
            {
              type: 'TextField',
              props: {
                label: 'Message',
                multiline: true,
                rows: 4,
                fullWidth: true,
              },
              dataBinding: { source: 'form', path: 'message' },
            },
            {
              type: 'Button',
              props: {
                variant: 'contained',
                color: 'primary',
                fullWidth: true,
              },
              children: 'Submit',
              events: { onClick: 'handleSubmit' },
            },
          ],
        },
      ],
    };

    const context = {
      data: { form: formData },
      handlers: {
        handleSubmit: () => {
          setSubmitted(true);
          setTimeout(() => setSubmitted(false), 3000);
        },
      },
    };

    return (
      <Box>
        <ComponentRenderer schema={schema} context={context} />
        {submitted && (
          <Typography as="p" className="mt-4" color="success.main">
            Form submitted successfully!
          </Typography>
        )}
      </Box>
    );
  },
};

export const Dashboard: Story = {
  render: () => {
    const schema: ComponentSchema = {
      type: 'Grid',
      props: { container: true, spacing: 3 },
      children: [
        {
          type: 'Grid',
          props: { item: true, xs: 12, md: 4 },
          children: [
            {
              type: 'Card',
              props: { sx: { p: 2 } },
              children: [
                {
                  type: 'Typography',
                  props: { variant: 'h6' },
                  children: 'Total Users',
                },
                {
                  type: 'Typography',
                  props: { variant: 'h3', color: 'primary' },
                  children: '1,234',
                },
              ],
            },
          ],
        },
        {
          type: 'Grid',
          props: { item: true, xs: 12, md: 4 },
          children: [
            {
              type: 'Card',
              props: { sx: { p: 2 } },
              children: [
                {
                  type: 'Typography',
                  props: { variant: 'h6' },
                  children: 'Active Projects',
                },
                {
                  type: 'Typography',
                  props: { variant: 'h3', color: 'success.main' },
                  children: '42',
                },
              ],
            },
          ],
        },
        {
          type: 'Grid',
          props: { item: true, xs: 12, md: 4 },
          children: [
            {
              type: 'Card',
              props: { sx: { p: 2 } },
              children: [
                {
                  type: 'Typography',
                  props: { variant: 'h6' },
                  children: 'Revenue',
                },
                {
                  type: 'Typography',
                  props: { variant: 'h3', color: 'success.main' },
                  children: '$50K',
                },
              ],
            },
          ],
        },
      ],
    };

    return (
      <Box>
        <Typography as="h5" gutterBottom>
          Dashboard (Rendered from JSON)
        </Typography>
        <ComponentRenderer schema={schema} />
      </Box>
    );
  },
};
