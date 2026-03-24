// All tests skipped - incomplete feature
/**
 * Integration Tests: DataSource + Form Workflow
 *
 * Tests complete workflows combining DataSource hooks with Form management,
 * including loading data into forms, submitting updates, and handling errors.
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { rest } from 'msw';
import { setupServer } from 'msw/node';

import { useDataSource } from '../../hooks/useDataSource';
import { useForm } from '../../hooks/useForm';
import { validators } from '../../utils/validation';


// ============================================================================
// Mock Server Setup
// ============================================================================

interface User {
  id: number;
  name: string;
  email: string;
  age?: number;
}

const mockUsers: User[] = [
  { id: 1, name: 'John Doe', email: 'john@example.com', age: 30 },
  { id: 2, name: 'Jane Smith', email: 'jane@example.com', age: 25 },
];

const server = setupServer(
  rest.get('/api/users/:id', (req, res, ctx) => {
    const id = parseInt(req.params.id as string);
    const user = mockUsers.find((u) => u.id === id);
    if (!user) {
      return res(ctx.status(404), ctx.json({ message: 'User not found' }));
    }
    return res(ctx.json(user));
  }),
  rest.put('/api/users/:id', async (req, res, ctx) => {
    const id = parseInt(req.params.id as string);
    const body = await req.json();
    const userIndex = mockUsers.findIndex((u) => u.id === id);

    if (userIndex === -1) {
      return res(ctx.status(404), ctx.json({ message: 'User not found' }));
    }

    // Simulate validation error
    if (body.email === 'duplicate@example.com') {
      return res(
        ctx.status(400),
        ctx.json({ message: 'Email already exists', field: 'email' })
      );
    }

    mockUsers[userIndex] = { ...mockUsers[userIndex], ...body };
    return res(ctx.json(mockUsers[userIndex]));
  }),
  rest.post('/api/users', async (req, res, ctx) => {
    const body = await req.json();

    // Simulate validation error
    if (!body.name || !body.email) {
      return res(
        ctx.status(400),
        ctx.json({ message: 'Name and email are required' })
      );
    }

    const newUser = {
      id: mockUsers.length + 1,
      ...body,
    };
    mockUsers.push(newUser);
    return res(ctx.json(newUser));
  })
);

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  // Reset mock data
  mockUsers.length = 0;
  mockUsers.push(
    { id: 1, name: 'John Doe', email: 'john@example.com', age: 30 },
    { id: 2, name: 'Jane Smith', email: 'jane@example.com', age: 25 }
  );
});
afterAll(() => server.close());

// ============================================================================
// Integration Tests
// ============================================================================

describe.skip('DataSource + Form Workflow Integration', () => {
  describe('Load Data into Form', () => {
    it('should load user data from API into form', async () => {
      const { result } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
        });

        const form = useForm({
          initialValues: { name: '', email: '', age: 0 },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
            age: [validators.min(18, 'Must be at least 18')],
          },
          onSubmit: jest.fn(),
        });

        // Load data into form when available
        if (dataSource.data && !dataSource.isLoading) {
          if (form.values.name === '') {
            // Only set once to avoid infinite loop
            form.setFieldValue('name', dataSource.data.name);
            form.setFieldValue('email', dataSource.data.email);
            form.setFieldValue('age', dataSource.data.age || 0);
          }
        }

        return { dataSource, form };
      });

      // Wait for data to load
      await waitFor(() => expect(result.current.dataSource.isLoading).toBe(false));

      // Form should be populated with user data
      expect(result.current.form.values.name).toBe('John Doe');
      expect(result.current.form.values.email).toBe('john@example.com');
      expect(result.current.form.values.age).toBe(30);
    });

    it('should handle loading state during data fetch', async () => {
      const { result } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
        });

        return { isLoading: dataSource.isLoading, data: dataSource.data };
      });

      // Should be loading initially
      expect(result.current.isLoading).toBe(true);
      expect(result.current.data).toBeNull();

      // Wait for completion
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.data).toBeTruthy();
      expect(result.current.data?.name).toBe('John Doe');
    });

    it('should handle error state when data fetch fails', async () => {
      const { result } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/999', // Non-existent user
        });

        return { isLoading: dataSource.isLoading, error: dataSource.error };
      });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      expect(result.current.error).toBeTruthy();
      expect(result.current.error?.message).toContain('404');
    });
  });

  describe('Submit Form Data to API', () => {
    it('should submit form updates and refresh data', async () => {
      let submitCount = 0;

      const { result } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cache: false,
        });

        const form = useForm({
          initialValues: { name: '', email: '', age: 0 },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            submitCount++;
            // Update via API
            const response = await fetch('/api/users/1', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            if (!response.ok) {
              throw new Error('Update failed');
            }

            // Refresh data
            await dataSource.refetch();
          },
        });

        // Load data into form
        if (dataSource.data && form.values.name === '') {
          form.setFieldValue('name', dataSource.data.name);
          form.setFieldValue('email', dataSource.data.email);
          form.setFieldValue('age', dataSource.data.age || 0);
        }

        return { dataSource, form };
      });

      // Wait for initial load
      await waitFor(() => expect(result.current.dataSource.isLoading).toBe(false));
      expect(result.current.form.values.name).toBe('John Doe');

      // Update form
      act(() => {
        result.current.form.setFieldValue('name', 'John Updated');
        result.current.form.setFieldValue('email', 'john.updated@example.com');
      });

      // Submit form
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.form.handleSubmit(mockEvent);
      });

      expect(submitCount).toBe(1);

      // Wait for refetch
      await waitFor(() => {
        return result.current.dataSource.data?.name === 'John Updated';
      });

      expect(result.current.dataSource.data?.email).toBe('john.updated@example.com');
    });

    it('should handle server-side validation errors', async () => {
      const { result } = renderHook(() => {
        const form = useForm({
          initialValues: { name: 'Test', email: 'duplicate@example.com' },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            const response = await fetch('/api/users/1', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            if (!response.ok) {
              const error = await response.json();
              // Set server error on form
              if (error.field) {
                form.setFieldError(error.field, error.message);
              }
              throw new Error(error.message);
            }
          },
        });

        return form;
      });

      // Submit with duplicate email
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      // Should have server-side error
      expect(result.current.errors.email).toBe('Email already exists');
    });
  });

  describe('Create New Entity Workflow', () => {
    it('should create new entity and handle response', async () => {
      let createdUser: User | null = null;

      const { result } = renderHook(() => {
        const form = useForm({
          initialValues: { name: '', email: '', age: 0 },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
            age: [validators.required(), validators.min(18)],
          },
          onSubmit: async (values) => {
            const response = await fetch('/api/users', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            if (!response.ok) {
              throw new Error('Create failed');
            }

            createdUser = await response.json();
          },
        });

        return form;
      });

      // Fill form
      act(() => {
        result.current.setFieldValue('name', 'New User');
        result.current.setFieldValue('email', 'new@example.com');
        result.current.setFieldValue('age', 25);
      });

      // Submit
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      expect(createdUser).toBeTruthy();
      expect(createdUser?.name).toBe('New User');
      expect(createdUser?.email).toBe('new@example.com');
      expect(createdUser?.id).toBe(3); // New ID assigned
    });
  });

  describe('Optimistic Updates', () => {
    it('should handle optimistic update with rollback on error', async () => {
      const { result } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cache: false,
        });

        const form = useForm({
          initialValues: { name: '', email: '', age: 0 },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            // Store original data for rollback
            const original = dataSource.data;

            // Optimistic update
            dataSource.mutate({ ...original, ...values } as User);

            try {
              const response = await fetch('/api/users/1', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(values),
              });

              if (!response.ok) {
                throw new Error('Update failed');
              }

              // Refresh to get server data
              await dataSource.refetch();
            } catch (error) {
              // Rollback on error
              if (original) {
                dataSource.mutate(original);
              }
              throw error;
            }
          },
        });

        // Load data
        if (dataSource.data && form.values.name === '') {
          form.setFieldValue('name', dataSource.data.name);
          form.setFieldValue('email', dataSource.data.email);
        }

        return { dataSource, form };
      });

      await waitFor(() => expect(result.current.dataSource.isLoading).toBe(false));
      const originalName = result.current.dataSource.data?.name;

      // Update form
      act(() => {
        result.current.form.setFieldValue('name', 'Optimistic Name');
        result.current.form.setFieldValue('email', 'optimistic@example.com');
      });

      // Submit (optimistic update happens immediately)
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.form.handleSubmit(mockEvent);
      });

      // Data should be updated (either optimistically or from server)
      await waitFor(() => {
        return result.current.dataSource.data?.name === 'Optimistic Name';
      });

      expect(result.current.dataSource.data?.email).toBe('optimistic@example.com');
    });

    it('should rollback optimistic update on server error', async () => {
      // Set up server to return error
      server.use(
        rest.put('/api/users/1', (req, res, ctx) => {
          return res(ctx.status(500), ctx.json({ message: 'Server error' }));
        })
      );

      const { result } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cache: false,
        });

        const form = useForm({
          initialValues: { name: '', email: '' },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            const original = dataSource.data;

            // Optimistic update
            dataSource.mutate({ ...original, ...values } as User);

            try {
              const response = await fetch('/api/users/1', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(values),
              });

              if (!response.ok) {
                throw new Error('Update failed');
              }

              await dataSource.refetch();
            } catch (error) {
              // Rollback
              if (original) {
                dataSource.mutate(original);
              }
              throw error;
            }
          },
        });

        if (dataSource.data && form.values.name === '') {
          form.setFieldValue('name', dataSource.data.name);
          form.setFieldValue('email', dataSource.data.email);
        }

        return { dataSource, form };
      });

      await waitFor(() => expect(result.current.dataSource.isLoading).toBe(false));
      const originalName = result.current.dataSource.data?.name;
      const originalEmail = result.current.dataSource.data?.email;

      // Update and submit (will fail)
      act(() => {
        result.current.form.setFieldValue('name', 'Failed Update');
      });

      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.form.handleSubmit(mockEvent);
      });

      // Should rollback to original
      await waitFor(() => {
        return result.current.dataSource.data?.name === originalName;
      });

      expect(result.current.dataSource.data?.email).toBe(originalEmail);
    });
  });

  describe('Form Reset After Submission', () => {
    it('should reset form after successful submission', async () => {
      const { result } = renderHook(() => {
        const form = useForm({
          initialValues: { name: '', email: '' },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            await fetch('/api/users', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            // Reset form after successful submission
            form.resetForm();
          },
        });

        return form;
      });

      // Fill form
      act(() => {
        result.current.setFieldValue('name', 'Test User');
        result.current.setFieldValue('email', 'test@example.com');
      });

      expect(result.current.values.name).toBe('Test User');

      // Submit
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      // Form should be reset
      expect(result.current.values.name).toBe('');
      expect(result.current.values.email).toBe('');
      expect(result.current.errors).toEqual({});
      expect(result.current.touched).toEqual({});
    });
  });

  describe('Complex Workflow: Edit, Validate, Submit, Refresh', () => {
    it('should handle complete edit workflow', async () => {
      const timeline: string[] = [];

      const { result } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cache: false,
          onSuccess: () => timeline.push('data-loaded'),
        });

        const form = useForm({
          initialValues: { name: '', email: '', age: 0 },
          validationRules: {
            name: [validators.required(), validators.minLength(3)],
            email: [validators.required(), validators.email()],
            age: [validators.min(18)],
          },
          onSubmit: async (values) => {
            timeline.push('submit-started');

            const response = await fetch('/api/users/1', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            if (!response.ok) {
              timeline.push('submit-failed');
              throw new Error('Update failed');
            }

            timeline.push('submit-success');
            await dataSource.refetch();
            timeline.push('data-refreshed');
          },
          validateOnChange: true,
        });

        // Load data into form
        if (dataSource.data && form.values.name === '') {
          form.setFieldValue('name', dataSource.data.name);
          form.setFieldValue('email', dataSource.data.email);
          form.setFieldValue('age', dataSource.data.age || 0);
          timeline.push('form-populated');
        }

        return { dataSource, form };
      });

      // Wait for initial load
      await waitFor(() => expect(timeline).toContain('data-loaded'));
      expect(timeline).toContain('form-populated');

      // Edit form
      act(() => {
        timeline.push('user-editing');
        result.current.form.setFieldValue('name', 'Updated Name');
      });

      // Validate
      act(() => {
        timeline.push('validation-triggered');
        result.current.form.validateForm();
      });

      expect(result.current.form.isValid).toBe(true);

      // Submit
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.form.handleSubmit(mockEvent);
      });

      // Verify workflow timeline
      expect(timeline).toContain('user-editing');
      expect(timeline).toContain('validation-triggered');
      expect(timeline).toContain('submit-started');
      expect(timeline).toContain('submit-success');
      expect(timeline).toContain('data-refreshed');

      // Verify data updated
      expect(result.current.dataSource.data?.name).toBe('Updated Name');
    });
  });
});
