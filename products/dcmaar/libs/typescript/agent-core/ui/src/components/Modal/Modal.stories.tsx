import type { Meta, StoryObj } from '@storybook/react';
import { useState } from 'react';
import { Button } from '../Button/Button';
import { Modal } from './Modal';
import { Card } from '../Card/Card';
import { Input } from '../Input/Input';
import { Label } from '../Label/Label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../Select/Select';
import { Textarea } from '../Textarea/Textarea';

const meta: Meta<typeof Modal> = {
  title: 'Components/Modal',
  component: Modal,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component: 'A modal dialog that interrupts the user with important content and expects a response.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    size: {
      control: {
        type: 'select',
        options: ['sm', 'md', 'lg', 'xl', '2xl', '3xl', 'full'],
      },
      description: 'The size of the modal',
      table: {
        type: { summary: 'string' },
        defaultValue: { summary: 'md' },
      },
    },
    showCloseButton: {
      control: 'boolean',
      description: 'Whether to show the close button',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: 'true' },
      },
    },
    closeOnOverlayClick: {
      control: 'boolean',
      description: 'Whether to close the modal when clicking outside',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: 'true' },
      },
    },
    closeOnEsc: {
      control: 'boolean',
      description: 'Whether to close the modal when pressing the escape key',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: 'true' },
      },
    },
  },
  args: {
    size: 'md',
    showCloseButton: true,
    closeOnOverlayClick: true,
    closeOnEsc: true,
  },
};

export default meta;

type Story = StoryObj<typeof Modal>;

// Basic Modal
const BasicModal = (args: any) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <Button onClick={() => setIsOpen(true)}>Open Modal</Button>
      <Modal
        {...args}
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        title="Basic Modal"
        description="This is a basic modal example with a title and description."
      >
        <div className="space-y-4">
          <p>This is the content of the modal. You can put any content here.</p>
          <p>Click outside or press ESC to close.</p>
        </div>
      </Modal>
    </>
  );
};

export const Basic: Story = {
  render: (args) => <BasicModal {...args} />,
};

// Modal with Form
const ModalWithForm = (args: any) => {
  const [isOpen, setIsOpen] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    role: '',
    message: '',
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSelectChange = (value: string) => {
    setFormData(prev => ({
      ...prev,
      role: value,
    }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    alert(JSON.stringify(formData, null, 2));
    setIsOpen(false);
  };

  return (
    <>
      <Button onClick={() => setIsOpen(true)}>Open Form Modal</Button>
      <Modal
        {...args}
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        title="Contact Us"
        description="Fill out the form below and we'll get back to you soon."
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">Name</Label>
            <Input
              id="name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="John Doe"
              required
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="john@example.com"
              required
            />
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="role">Role</Label>
            <Select onValueChange={handleSelectChange} value={formData.role}>
              <SelectTrigger>
                <SelectValue placeholder="Select a role" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="developer">Developer</SelectItem>
                <SelectItem value="designer">Designer</SelectItem>
                <SelectItem value="manager">Manager</SelectItem>
                <SelectItem value="other">Other</SelectItem>
              </SelectContent>
            </Select>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="message">Message</Label>
            <Textarea
              id="message"
              name="message"
              value={formData.message}
              onChange={handleChange}
              placeholder="Your message here..."
              rows={4}
              required
            />
          </div>
          
          <div className="flex justify-end space-x-2 pt-4">
            <Button type="button" variant="outlined" onClick={() => setIsOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Submit</Button>
          </div>
        </form>
      </Modal>
    </>
  );
};

export const WithForm: Story = {
  render: (args) => <ModalWithForm {...args} />,
};

// Modal with Custom Content
const ModalWithCustomContent = (args: any) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <>
      <Button onClick={() => setIsOpen(true)}>Open Custom Modal</Button>
      <Modal
        {...args}
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        className="max-w-2xl"
      >
        <Card className="border-0 p-0">
          <div className="relative aspect-video overflow-hidden rounded-t-lg bg-gradient-to-r from-blue-500 to-purple-600">
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="text-center text-white">
                <h3 className="text-2xl font-bold">Special Offer</h3>
                <p className="mt-2 text-blue-100">Limited time only</p>
              </div>
            </div>
          </div>
          <div className="p-6">
            <h3 className="text-xl font-semibold">Get 50% off your first month</h3>
            <p className="mt-2 text-gray-600 dark:text-gray-400">
              Upgrade to our premium plan and enjoy all the exclusive features with a special discount.
            </p>
            <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="rounded-lg border p-4">
                <h4 className="font-medium">Basic</h4>
                <p className="mt-1 text-2xl font-bold">$9.99<span className="text-sm font-normal text-gray-500">/month</span></p>
                <ul className="mt-3 space-y-2 text-sm">
                  <li className="flex items-center">
                    <svg className="mr-2 h-4 w-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    Feature 1
                  </li>
                  <li className="flex items-center">
                    <svg className="mr-2 h-4 w-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    Feature 2
                  </li>
                </ul>
              </div>
              <div className="rounded-lg border-2 border-blue-500 bg-blue-50 p-4 dark:border-blue-600 dark:bg-blue-900/20">
                <div className="flex items-center justify-between">
                  <h4 className="font-medium">Premium</h4>
                  <span className="rounded-full bg-blue-100 px-2 py-1 text-xs font-medium text-blue-800 dark:bg-blue-900/50 dark:text-blue-200">
                    Popular
                  </span>
                </div>
                <p className="mt-1 text-2xl font-bold">
                  <span className="text-gray-400 line-through">$19.99</span>
                  <span className="ml-2">$9.99</span>
                  <span className="text-sm font-normal text-gray-500">/month</span>
                </p>
                <ul className="mt-3 space-y-2 text-sm">
                  <li className="flex items-center">
                    <svg className="mr-2 h-4 w-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    All Basic features
                  </li>
                  <li className="flex items-center">
                    <svg className="mr-2 h-4 w-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    Advanced analytics
                  </li>
                  <li className="flex items-center">
                    <svg className="mr-2 h-4 w-4 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    Priority support
                  </li>
                </ul>
              </div>
            </div>
            <div className="mt-6 flex justify-end space-x-3">
              <Button variant="outlined" onClick={() => setIsOpen(false)}>
                Maybe later
              </Button>
              <Button>Upgrade now</Button>
            </div>
          </div>
        </Card>
      </Modal>
    </>
  );
};

export const WithCustomContent: Story = {
  render: (args) => <ModalWithCustomContent {...args} />,
  args: {
    showCloseButton: false,
    closeOnOverlayClick: true,
  },
};

// Modal with Different Sizes
const ModalSizes = () => {
  const [isOpen, setIsOpen] = useState({
    sm: false,
    md: false,
    lg: false,
    xl: false,
    '2xl': false,
    '3xl': false,
    full: false,
  });

  const openModal = (size: keyof typeof isOpen) => {
    setIsOpen(prev => ({
      ...prev,
      [size]: true,
    }));
  };

  const closeModal = (size: keyof typeof isOpen) => {
    setIsOpen(prev => ({
      ...prev,
      [size]: false,
    }));
  };

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {(['sm', 'md', 'lg', 'xl', '2xl', '3xl', 'full'] as const).map((size) => (
        <div key={size} className="flex flex-col items-center">
          <Button onClick={() => openModal(size)} className="w-full">
            {size.toUpperCase()} Modal
          </Button>
          <Modal
            isOpen={isOpen[size]}
            onClose={() => closeModal(size)}
            title={`${size.toUpperCase()} Modal`}
            size={size}
          >
            <div className="space-y-4">
              <p>This is a <strong>{size.toUpperCase()}</strong> modal.</p>
              <p>Resize your browser window to see how the modal behaves responsively.</p>
              <div className="flex justify-end">
                <Button onClick={() => closeModal(size)}>Close</Button>
              </div>
            </div>
          </Modal>
        </div>
      ))}
    </div>
  );
};

export const Sizes: Story = {
  render: () => <ModalSizes />,
  parameters: {
    docs: {
      description: {
        story: 'Modal component with different size options. Resize your browser window to see how each size behaves responsively.',
      },
    },
  },
};

// Modal with Custom Close Behavior
const ModalWithCustomCloseBehavior = (args: any) => {
  const [isOpen, setIsOpen] = useState(false);
  const [showWarning, setShowWarning] = useState(false);

  const handleClose = () => {
    setShowWarning(true);
  };

  const confirmClose = () => {
    setShowWarning(false);
    setIsOpen(false);
  };

  const cancelClose = () => {
    setShowWarning(false);
  };

  return (
    <>
      <Button onClick={() => setIsOpen(true)}>Open Modal with Custom Close</Button>
      
      <Modal
        {...args}
        isOpen={isOpen}
        onClose={handleClose}
        title="Important Notice"
        closeOnOverlayClick={false}
      >
        <div className="space-y-4">
          <p>This modal has custom close behavior. Clicking the close button or pressing ESC will show a confirmation dialog.</p>
          <p>You cannot close this modal by clicking outside of it.</p>
          
          {showWarning && (
            <div className="mt-4 rounded-md bg-yellow-50 p-4 dark:bg-yellow-900/20">
              <div className="flex">
                <div className="flex-shrink-0">
                  <svg className="h-5 w-5 text-yellow-400" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                  </svg>
                </div>
                <div className="ml-3">
                  <h3 className="text-sm font-medium text-yellow-800 dark:text-yellow-200">Are you sure you want to close?</h3>
                  <div className="mt-2 text-sm text-yellow-700 dark:text-yellow-300">
                    <p>Your changes will not be saved.</p>
                  </div>
                  <div className="mt-4">
                    <div className="-mx-2 -my-1.5 flex">
                      <button
                        type="button"
                        onClick={confirmClose}
                        className="rounded-md bg-yellow-50 px-2 py-1.5 text-sm font-medium text-yellow-800 hover:bg-yellow-100 focus:outline-none focus:ring-2 focus:ring-yellow-600 focus:ring-offset-2 focus:ring-offset-yellow-50 dark:bg-yellow-900/30 dark:text-yellow-200 dark:hover:bg-yellow-900/50 dark:focus:ring-offset-yellow-900/50"
                      >
                        Yes, close
                      </button>
                      <button
                        type="button"
                        onClick={cancelClose}
                        className="ml-3 rounded-md bg-yellow-50 px-2 py-1.5 text-sm font-medium text-yellow-800 hover:bg-yellow-100 focus:outline-none focus:ring-2 focus:ring-yellow-600 focus:ring-offset-2 focus:ring-offset-yellow-50 dark:bg-yellow-900/30 dark:text-yellow-200 dark:hover:bg-yellow-900/50 dark:focus:ring-offset-yellow-900/50"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
          
          <div className="flex justify-end space-x-2 pt-2">
            <Button variant="outlined" onClick={handleClose}>
              Cancel
            </Button>
            <Button>Save Changes</Button>
          </div>
        </div>
      </Modal>
    </>
  );
};

export const WithCustomCloseBehavior: Story = {
  render: (args) => <ModalWithCustomCloseBehavior {...args} />,
  parameters: {
    docs: {
      description: {
        story: 'Modal with custom close behavior. Clicking the close button or pressing ESC will show a confirmation dialog instead of immediately closing.',
      },
    },
  },
};
