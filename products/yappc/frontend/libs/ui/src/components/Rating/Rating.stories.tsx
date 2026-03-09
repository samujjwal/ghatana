import React from 'react';

import { Rating } from './Rating';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof Rating> = {
  title: 'Components/Rating',
  component: Rating,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof Rating>;

/**
 * Default rating component
 */
export const Default: Story = {
  args: {
    value: 3,
  },
};

/**
 * Different sizes
 */
export const Sizes: Story = {
  render: () => (
    <div className="flex flex-col items-start gap-4">
      <div className="flex items-center gap-3">
        <span className="text-sm w-20">Small:</span>
        <Rating value={4} size="small" readOnly />
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm w-20">Medium:</span>
        <Rating value={4} size="medium" readOnly />
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm w-20">Large:</span>
        <Rating value={4} size="large" readOnly />
      </div>
    </div>
  ),
};

/**
 * Interactive rating (click to rate)
 */
export const Interactive: Story = {
  render: () => {
    const [value, setValue] = React.useState(0);
    
    return (
      <div className="flex flex-col gap-4">
        <Rating value={value} onChange={setValue} />
        <p className="text-sm text-grey-600">Selected: {value} stars</p>
      </div>
    );
  },
};

/**
 * Half-star precision
 */
export const HalfStar: Story = {
  render: () => {
    const [value, setValue] = React.useState(3.5);
    
    return (
      <div className="flex flex-col gap-4">
        <Rating value={value} precision={0.5} onChange={setValue} />
        <p className="text-sm text-grey-600">Selected: {value} stars</p>
      </div>
    );
  },
};

/**
 * Read-only rating
 */
export const ReadOnly: Story = {
  args: {
    value: 4.5,
    precision: 0.5,
    readOnly: true,
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  args: {
    value: 3,
    disabled: true,
  },
};

/**
 * Different maximum values
 */
export const DifferentMax: Story = {
  render: () => (
    <div className="flex flex-col items-start gap-4">
      <div className="flex items-center gap-3">
        <span className="text-sm w-20">Max 3:</span>
        <Rating value={2} max={3} readOnly />
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm w-20">Max 5:</span>
        <Rating value={4} max={5} readOnly />
      </div>
      <div className="flex items-center gap-3">
        <span className="text-sm w-20">Max 10:</span>
        <Rating value={7} max={10} readOnly size="small" />
      </div>
    </div>
  ),
};

/**
 * Without empty stars
 */
export const WithoutEmpty: Story = {
  args: {
    value: 3,
    showEmpty: false,
    readOnly: true,
  },
};

/**
 * Custom icons (using emoji)
 */
export const CustomIcons: Story = {
  render: () => (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <p className="text-sm font-medium">Hearts</p>
        <Rating
          value={4}
          icon={<span className="text-2xl">❤️</span>}
          emptyIcon={<span className="text-2xl">🤍</span>}
          readOnly
        />
      </div>
      <div className="flex flex-col gap-2">
        <p className="text-sm font-medium">Thumbs up</p>
        <Rating
          value={3}
          icon={<span className="text-2xl">👍</span>}
          emptyIcon={<span className="text-2xl opacity-30">👍</span>}
          readOnly
        />
      </div>
      <div className="flex flex-col gap-2">
        <p className="text-sm font-medium">Faces</p>
        <Rating
          value={4}
          max={5}
          icon={<span className="text-2xl">😊</span>}
          emptyIcon={<span className="text-2xl">😐</span>}
          readOnly
        />
      </div>
    </div>
  ),
};

/**
 * Product review example
 */
export const ProductReview: Story = {
  render: () => {
    const [rating, setRating] = React.useState(0);
    
    return (
      <div className="max-w-md p-6 bg-white rounded-lg shadow-sm border border-grey-200">
        <h3 className="text-lg font-semibold mb-4">Rate this product</h3>
        <div className="flex items-center gap-3 mb-4">
          <Rating
            value={rating}
            precision={0.5}
            onChange={setRating}
            size="large"
          />
          {rating > 0 && (
            <span className="text-sm text-grey-600">({rating} out of 5)</span>
          )}
        </div>
        {rating > 0 && (
          <textarea
            className="w-full p-3 border border-grey-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            rows={4}
            placeholder="Tell us more about your experience..."
          />
        )}
      </div>
    );
  },
};

/**
 * Average rating display
 */
export const AverageRating: Story = {
  render: () => {
    const reviews = [
      { rating: 5, count: 120 },
      { rating: 4, count: 45 },
      { rating: 3, count: 12 },
      { rating: 2, count: 3 },
      { rating: 1, count: 2 },
    ];

    const totalReviews = reviews.reduce((sum, r) => sum + r.count, 0);
    const averageRating = reviews.reduce((sum, r) => sum + r.rating * r.count, 0) / totalReviews;

    return (
      <div className="max-w-md p-6 bg-white rounded-lg shadow-sm border border-grey-200">
        <h3 className="text-lg font-semibold mb-4">Customer Reviews</h3>
        
        <div className="flex items-center gap-4 mb-6">
          <div className="text-center">
            <div className="text-4xl font-bold">{averageRating.toFixed(1)}</div>
            <Rating value={averageRating} precision={0.5} readOnly size="small" />
            <div className="text-sm text-grey-600 mt-1">{totalReviews} reviews</div>
          </div>
        </div>

        <div className="space-y-2">
          {reviews.map((review) => {
            const percentage = (review.count / totalReviews) * 100;
            
            return (
              <div key={review.rating} className="flex items-center gap-3">
                <span className="text-sm w-8">{review.rating}★</span>
                <div className="flex-1 h-2 bg-grey-200 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-warning-500"
                    style={{ width: `${percentage}%` }}
                  />
                </div>
                <span className="text-sm text-grey-600 w-12 text-right">
                  {review.count}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    );
  },
};

/**
 * Form integration
 */
export const FormIntegration: Story = {
  render: () => {
    const [rating, setRating] = React.useState(0);
    const [submitted, setSubmitted] = React.useState(false);

    const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      setSubmitted(true);
      setTimeout(() => setSubmitted(false), 2000);
    };

    return (
      <form onSubmit={handleSubmit} className="max-w-md p-6 bg-white rounded-lg shadow-sm border border-grey-200">
        <div className="mb-4">
          <label className="block text-sm font-medium mb-2">
            How would you rate our service? *
          </label>
          <Rating
            value={rating}
            onChange={setRating}
            name="service-rating"
          />
          {rating === 0 && (
            <p className="text-sm text-error-600 mt-1">Please select a rating</p>
          )}
        </div>

        <button
          type="submit"
          disabled={rating === 0}
          className="px-4 py-2 bg-primary-500 text-white rounded-md hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Submit Rating
        </button>

        {submitted && (
          <p className="text-sm text-success-600 mt-2">Thank you for your feedback!</p>
        )}
      </form>
    );
  },
};

/**
 * Dark mode
 */
export const DarkMode: Story = {
  render: () => (
    <div className="bg-grey-900 p-8 rounded-lg">
      <div className="flex flex-col gap-6">
        <div>
          <p className="text-white text-sm mb-2">Default</p>
          <Rating value={4} readOnly />
        </div>
        <div>
          <p className="text-white text-sm mb-2">Half stars</p>
          <Rating value={3.5} precision={0.5} readOnly />
        </div>
        <div>
          <p className="text-white text-sm mb-2">Interactive</p>
          <Rating value={2} onChange={() => {}} />
        </div>
      </div>
    </div>
  ),
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  args: {
    value: 3,
    max: 5,
    size: 'medium',
    precision: 1,
    readOnly: false,
    disabled: false,
    showEmpty: true,
    highlightSelectedOnly: true,
  },
  render: (args) => {
    const [value, setValue] = React.useState(args.value || 0);
    
    return (
      <div className="flex flex-col gap-4">
        <Rating {...args} value={value} onChange={setValue} />
        <p className="text-sm text-grey-600">Selected: {value} stars</p>
      </div>
    );
  },
};
