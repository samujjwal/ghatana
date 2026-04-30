import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { CreateCollectionPage } from '../pages/CreateCollectionPage';

const mockNavigate = vi.fn();
const mockCreate = vi.fn();
const mockToastSuccess = vi.fn();
const mockToastError = vi.fn();

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('../lib/api/collections', () => ({
  collectionsApi: {
    create: (...args: unknown[]) => mockCreate(...args),
  },
}));

vi.mock('sonner', () => ({
  toast: {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: (...args: unknown[]) => mockToastError(...args),
  },
}));

vi.mock('../features/collection/components/CollectionForm', () => ({
  CollectionForm: ({
    onSubmit,
    onCancel,
    isSubmitting,
  }: {
    onSubmit: (data: {
      name: string;
      description?: string;
      schema: Record<string, unknown>;
    }) => Promise<void>;
    onCancel: () => void;
    isSubmitting: boolean;
  }) => (
    <div>
      <div data-testid="collection-form-submitting">{String(isSubmitting)}</div>
      <button
        type="button"
        data-testid="collection-form-submit"
        onClick={() =>
          void onSubmit({
            name: 'Customers',
            description: 'Customer records',
            schema: { fields: [{ name: 'id', type: 'string' }] },
          })
        }
      >
        Submit
      </button>
      <button type="button" data-testid="collection-form-cancel" onClick={onCancel}>
        Cancel
      </button>
    </div>
  ),
}));

describe('Collections UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders create collection page shell', () => {
    render(<CreateCollectionPage />);

    expect(screen.getByText('Create New Collection')).toBeInTheDocument();
    expect(screen.getByText('Define a new collection and its schema')).toBeInTheDocument();
  });

  it('submits create collection and navigates to data explorer', async () => {
    const user = userEvent.setup();
    mockCreate.mockResolvedValue({ id: 'col-1' });

    render(<CreateCollectionPage />);
    await user.click(screen.getByTestId('collection-form-submit'));

    expect(mockCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Customers',
        description: 'Customer records',
        schemaType: 'entity',
      }),
    );
    expect(mockToastSuccess).toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith('/data');
  });

  it('handles cancel navigation', async () => {
    const user = userEvent.setup();

    render(<CreateCollectionPage />);
    await user.click(screen.getByTestId('collection-form-cancel'));

    expect(mockNavigate).toHaveBeenCalledWith('/data');
  });
});
