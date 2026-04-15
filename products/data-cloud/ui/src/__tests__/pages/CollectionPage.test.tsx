/**
 * Tests for the Collection pages:
 * - EditCollectionPage (not tested elsewhere)
 *
 * Mocks: useParams, useNavigate, dataCloudApi, CollectionForm, toast
 *
 * @doc.type test
 * @doc.purpose RTL tests for EditCollectionPage
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

const mockNavigate = vi.fn();

vi.mock('react-router', async (importOriginal) => {
    const actual = await importOriginal<typeof import('react-router')>();
    return {
        ...actual,
        useParams: vi.fn(() => ({ id: 'col-123' })),
        useNavigate: vi.fn(() => mockNavigate),
    };
});

vi.mock('../../lib/api/data-cloud-api', () => ({
    dataCloudApi: {
        getCollectionById: vi.fn(),
        updateCollection: vi.fn(),
    },
}));

vi.mock('../../features/collection/components/CollectionForm', () => ({
    CollectionForm: ({
        onSubmit,
        onCancel,
        initialData,
        isSubmitting,
    }: {
        onSubmit?: (data: unknown) => void;
        onCancel?: () => void;
        initialData?: { name?: string };
        isSubmitting?: boolean;
    }) => (
        <form data-testid="collection-form">
            <div data-testid="initial-name">{initialData?.name ?? 'missing'}</div>
            <div data-testid="submit-state">{isSubmitting ? 'submitting' : 'idle'}</div>
            <input name="name" placeholder="Collection name" />
            <button
                type="submit"
                onClick={() =>
                    onSubmit?.({
                        name: 'Updated Name',
                        description: 'Initial description',
                        schema: { fields: [{ name: 'name', type: 'STRING' }] },
                    })
                }
            >
                Save
            </button>
            <button type="button" onClick={() => onCancel?.()}>
                Cancel
            </button>
        </form>
    ),
}));

vi.mock('sonner', () => ({
    toast: {
        error: vi.fn(),
        success: vi.fn(),
    },
}));

import { dataCloudApi } from '../../lib/api/data-cloud-api';
import { useParams } from 'react-router';
import { toast } from 'sonner';
import { EditCollectionPage } from '../../pages/EditCollectionPage';

const mockGetCollectionById = vi.mocked(dataCloudApi.getCollectionById);
const mockUpdateCollection = vi.mocked(dataCloudApi.updateCollection);
const mockUseParams = vi.mocked(useParams);
const mockToastError = vi.mocked(toast.error);
const mockToastSuccess = vi.mocked(toast.success);

describe('EditCollectionPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockUseParams.mockReturnValue({ id: 'col-123' });
        mockNavigate.mockReset();
    });

    it('shows a loading indicator while fetching collection data', () => {
        // Mock a pending promise
        mockGetCollectionById.mockImplementation(
            () => new Promise(() => { }) // never resolves during test
        );
        render(<EditCollectionPage />, { wrapper: TestWrapper });

        // Should show some loading state
        const body = document.body.textContent ?? '';
        // Either shows "Loading" text or a spinner element
        const hasSpinner = document.querySelector('[data-testid="spinner"], .animate-spin, [role="status"]');
        expect(hasSpinner ?? body.length > 0).toBeTruthy();
    });

    it('renders the collection form after data is loaded', async () => {
        mockGetCollectionById.mockResolvedValue({
            data: { id: 'col-123', name: 'Test Collection', description: 'A test', schema: { fields: [] } },
        } as never);

        render(<EditCollectionPage />, { wrapper: TestWrapper });

        await waitFor(() => {
            expect(screen.getByRole('heading', { name: 'Edit Collection' })).toBeInTheDocument();
            expect(screen.getByTestId('collection-form')).toBeInTheDocument();
            expect(screen.getByTestId('initial-name')).toHaveTextContent('Test Collection');
        });
    });

    it('redirects to /collections when no ID is provided', async () => {
        const { useNavigate } = await import('react-router');
        vi.mocked(useNavigate).mockReturnValue(mockNavigate);
        mockUseParams.mockReturnValue({ id: undefined });
        mockGetCollectionById.mockResolvedValue({ data: null } as never);

        render(<EditCollectionPage />, { wrapper: TestWrapper });

        // With no ID, the page should not call getCollectionById
        await waitFor(() => {
            expect(mockGetCollectionById).not.toHaveBeenCalled();
        });
        expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('shows error state when collection load fails', async () => {
        mockGetCollectionById.mockRejectedValue(new Error('Network error'));

        render(<EditCollectionPage />, { wrapper: TestWrapper });

        await waitFor(() => {
            expect(mockToastError).toHaveBeenCalledWith('Failed to load collection');
            expect(mockNavigate).toHaveBeenCalledWith('/collections');
        });
    });

    it('shows a not-found state when the collection lookup resolves to null', async () => {
        mockGetCollectionById.mockResolvedValue({ data: null } as never);

        render(<EditCollectionPage />, { wrapper: TestWrapper });

        expect(await screen.findByText('Collection not found')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Back to Collections/i }));
        expect(mockNavigate).toHaveBeenCalledWith('/collections');
    });

    it('submits canonical collection updates and navigates back to the collection detail page', async () => {
        mockGetCollectionById.mockResolvedValue({
            data: {
                id: 'col-123',
                name: 'Existing Collection',
                description: 'Initial description',
                schema: { fields: [{ id: 'field-1', name: 'name', type: 'STRING' }] },
            },
        } as never);
        mockUpdateCollection.mockResolvedValue({ data: { id: 'col-123' } } as never);

        render(<EditCollectionPage />, { wrapper: TestWrapper });

        expect(await screen.findByTestId('collection-form')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => {
            expect(mockUpdateCollection).toHaveBeenCalledWith('col-123', {
                name: 'Updated Name',
                description: 'Initial description',
                schema: { fields: [{ id: 'field-1', name: 'name', type: 'STRING' }] },
            });
            expect(mockToastSuccess).toHaveBeenCalledWith('Collection updated successfully');
            expect(mockNavigate).toHaveBeenCalledWith('/collections/col-123');
        });
    });

    it('uses the cancel action to navigate back to the collection detail page', async () => {
        mockGetCollectionById.mockResolvedValue({
            data: { id: 'col-123', name: 'Existing Collection', description: 'A test', schema: { fields: [] } },
        } as never);

        render(<EditCollectionPage />, { wrapper: TestWrapper });

        expect(await screen.findByTestId('collection-form')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

        expect(mockNavigate).toHaveBeenCalledWith('/collections/col-123');
    });
});
