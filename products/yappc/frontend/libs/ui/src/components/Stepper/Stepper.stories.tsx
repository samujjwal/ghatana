import React from 'react';

import { Stepper } from './Stepper';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Stepper> = {
  title: 'Components/Stepper',
  component: Stepper,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Stepper>;

const steps = [
  { label: 'Select campaign settings', description: 'Choose your campaign type' },
  { label: 'Create an ad group', description: 'Set up your ad group' },
  { label: 'Create an ad', description: 'Design your advertisement' },
];

/**
 * Default horizontal stepper
 */
export const Default: Story = {
  args: {
    steps,
    activeStep: 1,
    completed: [0],
  },
};

/**
 * Vertical orientation
 */
export const Vertical: Story = {
  args: {
    steps,
    activeStep: 1,
    completed: [0],
    orientation: 'vertical',
  },
};

/**
 * All steps completed
 */
export const AllCompleted: Story = {
  args: {
    steps,
    activeStep: 3,
    completed: [0, 1, 2],
  },
};

/**
 * With error state
 */
export const WithError: Story = {
  args: {
    steps,
    activeStep: 2,
    completed: [0],
    error: [1],
  },
};

/**
 * Alternative label (labels below icons)
 */
export const AlternativeLabel: Story = {
  args: {
    steps,
    activeStep: 1,
    completed: [0],
    alternativeLabel: true,
  },
};

/**
 * Optional step
 */
export const OptionalStep: Story = {
  args: {
    steps: [
      { label: 'Select campaign settings' },
      { label: 'Create an ad group', optional: true },
      { label: 'Create an ad' },
    ],
    activeStep: 1,
    completed: [0],
  },
};

/**
 * Non-linear navigation (clickable steps)
 */
export const NonLinear: Story = {
  render: () => {
    const [activeStep, setActiveStep] = React.useState(0);
    const [completed, setCompleted] = React.useState<number[]>([]);

    const handleStepClick = (step: number) => {
      setActiveStep(step);
    };

    const handleComplete = () => {
      setCompleted([...completed, activeStep]);
      setActiveStep(activeStep + 1);
    };

    return (
      <div className="w-full">
        <Stepper
          steps={steps}
          activeStep={activeStep}
          completed={completed}
          nonLinear
          onStepClick={handleStepClick}
        />
        <div className="mt-8 flex gap-3">
          <button
            onClick={() => setActiveStep(Math.max(0, activeStep - 1))}
            disabled={activeStep === 0}
            className="px-4 py-2 text-sm bg-grey-200 text-grey-700 rounded hover:bg-grey-300 disabled:opacity-50"
          >
            Back
          </button>
          <button
            onClick={handleComplete}
            disabled={activeStep === steps.length}
            className="px-4 py-2 text-sm bg-primary-500 text-white rounded hover:bg-primary-600 disabled:opacity-50"
          >
            {activeStep === steps.length - 1 ? 'Finish' : 'Complete Step'}
          </button>
        </div>
      </div>
    );
  },
};

/**
 * Many steps (5+)
 */
export const ManySteps: Story = {
  args: {
    steps: [
      { label: 'Personal Info' },
      { label: 'Contact Details' },
      { label: 'Address' },
      { label: 'Preferences' },
      { label: 'Review' },
      { label: 'Confirm' },
    ],
    activeStep: 3,
    completed: [0, 1, 2],
    alternativeLabel: true,
  },
};

/**
 * Disabled steps
 */
export const DisabledSteps: Story = {
  args: {
    steps: [
      { label: 'Account Setup' },
      { label: 'Email Verification', disabled: true },
      { label: 'Profile Completion', disabled: true },
    ],
    activeStep: 0,
  },
};

/**
 * Form wizard example
 */
export const FormWizard: Story = {
  render: () => {
    const [activeStep, setActiveStep] = React.useState(0);
    const [completed, setCompleted] = React.useState<number[]>([]);

    const formSteps = [
      { label: 'Account Information', description: 'Enter your account details' },
      { label: 'Personal Details', description: 'Tell us about yourself' },
      { label: 'Preferences', description: 'Customize your experience', optional: true },
      { label: 'Review & Submit', description: 'Confirm your information' },
    ];

    const handleNext = () => {
      setCompleted([...completed, activeStep]);
      setActiveStep(activeStep + 1);
    };

    const handleBack = () => {
      setActiveStep(activeStep - 1);
    };

    const handleReset = () => {
      setActiveStep(0);
      setCompleted([]);
    };

    return (
      <div className="max-w-3xl p-6 bg-white rounded-lg shadow-sm border border-grey-200">
        <h2 className="text-xl font-bold mb-6">Create Account</h2>

        <Stepper
          steps={formSteps}
          activeStep={activeStep}
          completed={completed}
        />

        <div className="mt-8 p-6 bg-grey-50 rounded-lg min-h-[200px]">
          {activeStep < formSteps.length ? (
            <>
              <h3 className="text-lg font-semibold mb-2">{formSteps[activeStep].label}</h3>
              <p className="text-sm text-grey-600 mb-4">{formSteps[activeStep].description}</p>
              <div className="text-sm text-grey-500">
                Form content for step {activeStep + 1} would go here...
              </div>
            </>
          ) : (
            <div className="text-center py-8">
              <div className="text-5xl mb-4">✅</div>
              <h3 className="text-lg font-semibold mb-2">All steps completed!</h3>
              <p className="text-sm text-grey-600">Your account has been created successfully.</p>
            </div>
          )}
        </div>

        <div className="mt-6 flex justify-between">
          <button
            onClick={handleBack}
            disabled={activeStep === 0}
            className="px-4 py-2 text-sm bg-grey-200 text-grey-700 rounded hover:bg-grey-300 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Back
          </button>

          {activeStep < formSteps.length ? (
            <button
              onClick={handleNext}
              className="px-4 py-2 text-sm bg-primary-500 text-white rounded hover:bg-primary-600"
            >
              {activeStep === formSteps.length - 1 ? 'Finish' : 'Next'}
            </button>
          ) : (
            <button
              onClick={handleReset}
              className="px-4 py-2 text-sm bg-primary-500 text-white rounded hover:bg-primary-600"
            >
              Reset
            </button>
          )}
        </div>
      </div>
    );
  },
};

/**
 * Checkout process example
 */
export const CheckoutProcess: Story = {
  render: () => {
    const [activeStep, setActiveStep] = React.useState(0);
    const [completed, setCompleted] = React.useState<number[]>([]);

    const checkoutSteps = [
      { label: 'Cart', description: '3 items' },
      { label: 'Shipping', description: 'Delivery address' },
      { label: 'Payment', description: 'Payment method' },
      { label: 'Review', description: 'Confirm order' },
    ];

    return (
      <div className="max-w-3xl p-6 bg-white rounded-lg shadow-sm border border-grey-200">
        <h2 className="text-xl font-bold mb-6">Checkout</h2>

        <Stepper
          steps={checkoutSteps}
          activeStep={activeStep}
          completed={completed}
          orientation="vertical"
        />

        <div className="mt-6 flex gap-3">
          <button
            onClick={() => {
              setCompleted([...completed, activeStep]);
              setActiveStep(activeStep + 1);
            }}
            disabled={activeStep === checkoutSteps.length}
            className="px-4 py-2 text-sm bg-primary-500 text-white rounded hover:bg-primary-600 disabled:opacity-50"
          >
            Continue
          </button>
        </div>
      </div>
    );
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  render: () => (
    <div className="bg-grey-900 p-8 rounded-lg">
      <div className="mb-8">
        <p className="text-white text-sm mb-4">Horizontal</p>
        <Stepper
          steps={steps}
          activeStep={1}
          completed={[0]}
        />
      </div>
      <div>
        <p className="text-white text-sm mb-4">Vertical</p>
        <Stepper
          steps={steps}
          activeStep={1}
          completed={[0]}
          orientation="vertical"
        />
      </div>
    </div>
  ),
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  args: {
    steps,
    activeStep: 1,
    completed: [0],
    error: [],
    orientation: 'horizontal',
    nonLinear: false,
    alternativeLabel: false,
  },
};
