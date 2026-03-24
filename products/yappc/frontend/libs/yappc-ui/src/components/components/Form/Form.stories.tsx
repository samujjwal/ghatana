import { useState } from 'react';

import { Form } from './Form';
import { FormInput } from './FormInput';
import { Button } from '../Button';

import type { Meta, StoryObj } from '@storybook/react-vite';


const meta = {
  title: 'Components/Form',
  component: Form,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
} satisfies Meta<typeof Form>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    initialValues: { name: '', email: '', message: '' },
    onSubmit: (_values: Record<string, unknown>) => { },
    children: null,
  },
  render: () => {
     
    const [result, setResult] = useState<string>('');

    const handleSubmit = (values: Record<string, unknown>) => {
      setResult(JSON.stringify(values, null, 2));
    };

    const validate = (values: Record<string, unknown>) => {
      const errors: Record<string, string> = {};

      if (!values.name) {
        errors.name = 'Name is required';
      }

      if (!values.email) {
        errors.email = 'Email is required';
      } else if (!/^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i.test(values.email)) {
        errors.email = 'Invalid email address';
      }

      return errors;
    };

    return (
      <div style={{ width: '400px' }}>
        <Form
          initialValues={{
            name: '',
            email: '',
            message: '',
          }}
          onSubmit={handleSubmit}
          validate={validate}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <FormInput name="name" label="Name" placeholder="Enter your name" required />
            <FormInput name="email" label="Email" type="email" placeholder="Enter your email" required />
            <FormInput name="message" label="Message" placeholder="Enter your message" />

            <Button type="submit">Submit</Button>

            {result && (
              <div style={{ marginTop: '1rem', padding: '1rem', backgroundColor: '#f5f5f5', borderRadius: '4px' }}>
                <pre>{result}</pre>
              </div>
            )}
          </div>
        </Form>
      </div>
    );
  },
};

export const WithValidation: Story = {
  args: {
    initialValues: { username: '', password: '' },
    onSubmit: (_values: Record<string, unknown>) => { },
    children: null,
  },
  render: () => {
    const validatePassword = (value: string) => {
      if (!value) return 'Password is required';
      if (value.length < 8) return 'Password must be at least 8 characters';
      if (!/[A-Z]/.test(value)) return 'Password must contain at least one uppercase letter';
      if (!/[a-z]/.test(value)) return 'Password must contain at least one lowercase letter';
      if (!/[0-9]/.test(value)) return 'Password must contain at least one number';
      return undefined;
    };

    const handleSubmit = () => {
      alert('Form submitted successfully!');
    };

    return (
      <div style={{ width: '400px' }}>
        <Form
          initialValues={{
            username: '',
            password: '',
          }}
          onSubmit={handleSubmit}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <FormInput
              name="username"
              label="Username"
              placeholder="Enter your username"
              validate={(value) => !value ? 'Username is required' : undefined}
            />

            <FormInput
              name="password"
              label="Password"
              type="password"
              showPasswordToggle
              placeholder="Enter your password"
              validate={validatePassword}
            />

            <Button type="submit">Sign In</Button>
          </div>
        </Form>
      </div>
    );
  },
};

export const WithFormattingAndMasking: Story = {
  args: {
    initialValues: { name: '', phone: '', creditCard: '', zipCode: '' },
    onSubmit: (_values: Record<string, unknown>) => { },
    children: null,
  },
  render: () => {
    return (
      <div style={{ width: '400px' }}>
        <Form
          initialValues={{
            name: '',
            phone: '',
            creditCard: '',
            zipCode: '',
          }}
          onSubmit={(values) => console.log(values)}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <FormInput
              name="name"
              label="Name"
              placeholder="Enter your name"
              format="capitalize"
            />

            <FormInput
              name="phone"
              label="Phone"
              placeholder="(123) 456-7890"
              mask="phone"
            />

            <FormInput
              name="creditCard"
              label="Credit Card"
              placeholder="XXXX XXXX XXXX XXXX"
              mask="creditCard"
            />

            <FormInput
              name="zipCode"
              label="Zip Code"
              placeholder="12345"
              mask="zipCode"
            />

            <Button type="submit">Submit</Button>
          </div>
        </Form>
      </div>
    );
  },
};
