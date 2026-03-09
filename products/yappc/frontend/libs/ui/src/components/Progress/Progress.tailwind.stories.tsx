/**
 * Progress Component Stories (Tailwind CSS + Base UI)
 */

import { useState, useEffect } from 'react';

import { Progress } from './Progress.tailwind';

import type { Meta, StoryObj } from '@storybook/react';

const meta = {
  title: 'Components/Feedback/Progress (Tailwind)',
  component: Progress,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'A progress indicator component built with Base UI Progress primitives and styled with Tailwind CSS. Supports linear and circular variants, determinate and indeterminate states.',
      },
    },
  },
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['linear', 'circular'],
      description: 'Visual style variant',
    },
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
    showValue: {
      control: 'boolean',
      description: 'Show progress percentage',
    },
  },
} satisfies Meta<typeof Progress>;

export default meta;
/**
 *
 */
type Story = StoryObj<typeof meta>;

/**
 * Linear determinate progress
 */
export const LinearDeterminate: Story = {
  render: () => {
    const [progress, setProgress] = useState(0);

    useEffect(() => {
      const timer = setInterval(() => {
        setProgress((prev) => {
          if (prev >= 100) return 0;
          return prev + 10;
        });
      }, 800);
      return () => clearInterval(timer);
    }, []);

    return (
      <div className="w-full max-w-md space-y-2">
        <Progress
          label="Upload Progress"
          variant="linear"
          value={progress}
          max={100}
          showValue
        />
      </div>
    );
  },
};

/**
 * Linear indeterminate (loading)
 */
export const LinearIndeterminate: Story = {
  render: () => (
    <div className="w-full max-w-md space-y-2">
      <Progress label="Loading..." variant="linear" />
    </div>
  ),
};

/**
 * Circular determinate progress
 */
export const CircularDeterminate: Story = {
  render: () => {
    const [progress, setProgress] = useState(0);

    useEffect(() => {
      const timer = setInterval(() => {
        setProgress((prev) => {
          if (prev >= 100) return 0;
          return prev + 5;
        });
      }, 500);
      return () => clearInterval(timer);
    }, []);

    return (
      <div className="flex items-center justify-center">
        <Progress
          label="Progress"
          variant="circular"
          value={progress}
          max={100}
          showValue
        />
      </div>
    );
  },
};

/**
 * Circular indeterminate (spinner)
 */
export const CircularIndeterminate: Story = {
  render: () => (
    <div className="flex items-center justify-center">
      <Progress label="Loading..." variant="circular" />
    </div>
  ),
};

/**
 * All size variants (linear)
 */
export const LinearSizes: Story = {
  render: () => {
    const [progress, setProgress] = useState(60);

    return (
      <div className="space-y-6">
        <div className="w-full max-w-md">
          <Progress
            label="Small"
            variant="linear"
            size="sm"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Medium (default)"
            variant="linear"
            size="md"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Large"
            variant="linear"
            size="lg"
            value={progress}
            max={100}
            showValue
          />
        </div>
      </div>
    );
  },
};

/**
 * All size variants (circular)
 */
export const CircularSizes: Story = {
  render: () => {
    const [progress, setProgress] = useState(75);

    return (
      <div className="flex items-center justify-around">
        <Progress
          label="Small"
          variant="circular"
          size="sm"
          value={progress}
          max={100}
          showValue
        />

        <Progress
          label="Medium"
          variant="circular"
          size="md"
          value={progress}
          max={100}
          showValue
        />

        <Progress
          label="Large"
          variant="circular"
          size="lg"
          value={progress}
          max={100}
          showValue
        />
      </div>
    );
  },
};

/**
 * Color scheme variants (linear)
 */
export const LinearColorSchemes: Story = {
  render: () => {
    const [progress, setProgress] = useState(70);

    return (
      <div className="space-y-4">
        <div className="w-full max-w-md">
          <Progress
            label="Primary"
            variant="linear"
            colorScheme="primary"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Secondary"
            variant="linear"
            colorScheme="secondary"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Success"
            variant="linear"
            colorScheme="success"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Error"
            variant="linear"
            colorScheme="error"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Warning"
            variant="linear"
            colorScheme="warning"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Grey"
            variant="linear"
            colorScheme="grey"
            value={progress}
            max={100}
            showValue
          />
        </div>
      </div>
    );
  },
};

/**
 * Color scheme variants (circular)
 */
export const CircularColorSchemes: Story = {
  render: () => {
    const [progress, setProgress] = useState(65);

    return (
      <div className="grid grid-cols-3 gap-6">
        <div className="flex flex-col items-center gap-2">
          <Progress
            label="Primary"
            variant="circular"
            colorScheme="primary"
            value={progress}
            max={100}
            showValue
          />
          <span className="text-sm text-grey-600">Primary</span>
        </div>

        <div className="flex flex-col items-center gap-2">
          <Progress
            label="Secondary"
            variant="circular"
            colorScheme="secondary"
            value={progress}
            max={100}
            showValue
          />
          <span className="text-sm text-grey-600">Secondary</span>
        </div>

        <div className="flex flex-col items-center gap-2">
          <Progress
            label="Success"
            variant="circular"
            colorScheme="success"
            value={progress}
            max={100}
            showValue
          />
          <span className="text-sm text-grey-600">Success</span>
        </div>

        <div className="flex flex-col items-center gap-2">
          <Progress
            label="Error"
            variant="circular"
            colorScheme="error"
            value={progress}
            max={100}
            showValue
          />
          <span className="text-sm text-grey-600">Error</span>
        </div>

        <div className="flex flex-col items-center gap-2">
          <Progress
            label="Warning"
            variant="circular"
            colorScheme="warning"
            value={progress}
            max={100}
            showValue
          />
          <span className="text-sm text-grey-600">Warning</span>
        </div>

        <div className="flex flex-col items-center gap-2">
          <Progress
            label="Grey"
            variant="circular"
            colorScheme="grey"
            value={progress}
            max={100}
            showValue
          />
          <span className="text-sm text-grey-600">Grey</span>
        </div>
      </div>
    );
  },
};

/**
 * With custom formatting
 */
export const CustomFormatting: Story = {
  render: () => {
    const [progress, setProgress] = useState(42);

    return (
      <div className="space-y-6">
        <div className="w-full max-w-md">
          <Progress
            label="Download Progress"
            variant="linear"
            value={progress}
            max={100}
            showValue
            formatValue={(value, max) => `${value} MB / ${max} MB`}
          />
        </div>

        <div className="flex items-center justify-center">
          <Progress
            label="Time Remaining"
            variant="circular"
            value={progress}
            max={60}
            showValue
            formatValue={(value, max) => `${max - value}s`}
          />
        </div>
      </div>
    );
  },
};

/**
 * Multi-step upload example
 */
export const LoadingExample: Story = {
  render: () => {
    const [step, setStep] = useState(0);
    const [progress, setProgress] = useState(0);

    const steps = ['Uploading...', 'Processing...', 'Finalizing...', 'Complete!'];

    useEffect(() => {
      const timer = setInterval(() => {
        setProgress((prev) => {
          const newProgress = prev + 2;
          if (newProgress >= 100) {
            if (step < steps.length - 1) {
              setStep((s) => s + 1);
              return 0;
            }
            clearInterval(timer);
            return 100;
          }
          return newProgress;
        });
      }, 100);
      return () => clearInterval(timer);
    }, [step]);

    const isComplete = step === steps.length - 1 && progress === 100;

    return (
      <div className="mx-auto max-w-md space-y-4 rounded-lg border border-grey-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-semibold text-grey-900">File Upload</h3>

        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium text-grey-700">{steps[step]}</span>
            <span className="text-grey-500">
              Step {step + 1} of {steps.length}
            </span>
          </div>

          <Progress
            label={steps[step]}
            variant="linear"
            value={progress}
            max={100}
            colorScheme={isComplete ? 'success' : 'primary'}
            showValue
          />
        </div>

        {isComplete && (
          <div className="rounded-md bg-success-50 p-3 text-sm text-success-700">
            ✓ Upload completed successfully!
          </div>
        )}
      </div>
    );
  },
};

/**
 * Loading states example
 */
export const LoadingStates: Story = {
  render: () => {
    const [isLoading, setIsLoading] = useState(false);
    const [progress, setProgress] = useState(0);

    const startLoading = () => {
      setIsLoading(true);
      setProgress(0);
      const timer = setInterval(() => {
        setProgress((prev) => {
          if (prev >= 100) {
            clearInterval(timer);
            setIsLoading(false);
            return 100;
          }
          return prev + 5;
        });
      }, 200);
    };

    return (
      <div className="mx-auto max-w-md space-y-6 rounded-lg border border-grey-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-semibold text-grey-900">Loading States</h3>

        <div className="space-y-4">
          <div>
            <p className="mb-2 text-sm font-medium text-grey-700">Determinate (with progress)</p>
            <Progress
              label="Determinate progress"
              variant="linear"
              value={progress}
              max={100}
              showValue
            />
          </div>

          <div>
            <p className="mb-2 text-sm font-medium text-grey-700">Indeterminate (unknown duration)</p>
            {isLoading && <Progress label="Indeterminate loading" variant="linear" />}
            {!isLoading && (
              <div className="rounded-md border border-grey-200 bg-grey-50 p-3 text-center text-sm text-grey-500">
                Not loading
              </div>
            )}
          </div>
        </div>

        <button
          onClick={startLoading}
          disabled={isLoading}
          className="w-full rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isLoading ? 'Loading...' : 'Start Loading'}
        </button>
      </div>
    );
  },
};

/**
 * Accessibility demonstration
 */
export const Accessibility: Story = {
  render: () => {
    const [progress, setProgress] = useState(50);

    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-grey-200 bg-grey-50 p-4">
          <h3 className="mb-2 font-semibold text-grey-900">Accessibility Features:</h3>
          <ul className="space-y-1 text-sm text-grey-700">
            <li>• ARIA role="progressbar" for screen readers</li>
            <li>• aria-valuenow, aria-valuemin, aria-valuemax attributes</li>
            <li>• Proper labeling with aria-label or aria-labelledby</li>
            <li>• Visual progress indication (color and percentage)</li>
            <li>• Indeterminate state communicated to assistive tech</li>
          </ul>
        </div>

        <div className="w-full max-w-md">
          <Progress
            label="Accessible progress indicator"
            variant="linear"
            value={progress}
            max={100}
            showValue
          />
        </div>

        <input
          type="range"
          min={0}
          max={100}
          value={progress}
          onChange={(e) => setProgress(Number(e.target.value))}
          className="w-full max-w-md"
          aria-label="Adjust progress value"
        />
      </div>
    );
  },
};

/**
 * Interactive playground
 */
export const Playground: Story = {
  args: {
    label: 'Playground Progress',
    variant: 'linear',
    size: 'md',
    colorScheme: 'primary',
    value: 50,
    max: 100,
    showValue: true,
  },
  render: (args) => {
    return (
      <div className={args.variant === 'circular' ? 'flex items-center justify-center' : 'w-full max-w-md'}>
        <Progress {...args} />
      </div>
    );
  },
};
