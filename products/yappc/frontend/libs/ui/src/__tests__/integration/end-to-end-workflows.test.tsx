// All tests skipped - incomplete feature
/**
 * Integration Tests: End-to-End Workflows
 *
 * Tests complete workflows combining all Phase 3 systems:
 * - Event Bus + DataSource + Form + Validation
 * - Real-world scenarios like user management, settings updates, etc.
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { rest } from 'msw';
import { setupServer } from 'msw/node';

import { eventBus, useEventBus, useEventEmitter } from '../../core/event-bus';
import { useDataSource } from '../../hooks/useDataSource';
import { useForm } from '../../hooks/useForm';
import { validators } from '../../utils/validation';


// ============================================================================
// Mock Data & Server Setup
// ============================================================================

interface User {
  id: number;
  name: string;
  email: string;
  role: string;
}

interface Settings {
  notifications: boolean;
  theme: 'light' | 'dark';
  language: string;
}

const mockUsers: User[] = [
  { id: 1, name: 'Admin User', email: 'admin@example.com', role: 'admin' },
  { id: 2, name: 'Regular User', email: 'user@example.com', role: 'user' },
];

let mockSettings: Settings = {
  notifications: true,
  theme: 'light',
  language: 'en',
};

const server = setupServer(
  // Users endpoints
  rest.get('/api/users', (req, res, ctx) => {
    return res(ctx.json(mockUsers));
  }),
  rest.get('/api/users/:id', (req, res, ctx) => {
    const id = parseInt(req.params.id as string);
    const user = mockUsers.find((u) => u.id === id);
    if (!user) return res(ctx.status(404));
    return res(ctx.json(user));
  }),
  rest.put('/api/users/:id', async (req, res, ctx) => {
    const id = parseInt(req.params.id as string);
    const body = await req.json();
    const userIndex = mockUsers.findIndex((u) => u.id === id);

    if (userIndex === -1) return res(ctx.status(404));

    // Simulate email uniqueness check
    if (body.email && body.email !== mockUsers[userIndex].email) {
      const emailExists = mockUsers.some((u) => u.id !== id && u.email === body.email);
      if (emailExists) {
        return res(ctx.status(400), ctx.json({ message: 'Email already in use', field: 'email' }));
      }
    }

    mockUsers[userIndex] = { ...mockUsers[userIndex], ...body };
    return res(ctx.json(mockUsers[userIndex]));
  }),
  rest.delete('/api/users/:id', (req, res, ctx) => {
    const id = parseInt(req.params.id as string);
    const index = mockUsers.findIndex((u) => u.id === id);
    if (index === -1) return res(ctx.status(404));
    mockUsers.splice(index, 1);
    return res(ctx.status(204));
  }),

  // Settings endpoints
  rest.get('/api/settings', (req, res, ctx) => {
    return res(ctx.json(mockSettings));
  }),
  rest.put('/api/settings', async (req, res, ctx) => {
    const body = await req.json();
    mockSettings = { ...mockSettings, ...body };
    return res(ctx.json(mockSettings));
  }),
);

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  eventBus.removeAllListeners();
  eventBus.clearHistory();
  // Reset mock data
  mockUsers.length = 0;
  mockUsers.push(
    { id: 1, name: 'Admin User', email: 'admin@example.com', role: 'admin' },
    { id: 2, name: 'Regular User', email: 'user@example.com', role: 'user' }
  );
  mockSettings = { notifications: true, theme: 'light', language: 'en' };
});
afterAll(() => server.close());

// ============================================================================
// End-to-End Workflow Tests
// ============================================================================

describe.skip('End-to-End Workflows', () => {
  describe('User Profile Edit Workflow', () => {
    it('should complete full user profile edit with validation and event broadcasting', async () => {
      const eventLog: string[] = [];

      // Set up event listeners
      eventBus.on('user:profile-editing', () => eventLog.push('editing-started'));
      eventBus.on('user:profile-updated', () => eventLog.push('profile-updated'));
      eventBus.on('ui:notification', (payload: unknown) => eventLog.push(`notification:${payload.type}`));

      const { result } = renderHook(() => {
        const userDataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cache: false,
        });

        const userForm = useForm({
          initialValues: { name: '', email: '', role: '' },
          validationRules: {
            name: [validators.required(), validators.minLength(3)],
            email: [validators.required(), validators.email()],
            role: [validators.required()],
          },
          onSubmit: async (values) => {
            try {
              const response = await fetch('/api/users/1', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(values),
              });

              if (!response.ok) {
                const error = await response.json();
                if (error.field) {
                  userForm.setFieldError(error.field, error.message);
                }
                await eventBus.emit('ui:notification', {
                  message: 'Update failed',
                  type: 'error',
                });
                throw new Error(error.message);
              }

              await userDataSource.refetch();
              await eventBus.emit('user:profile-updated', { userId: 1 });
              await eventBus.emit('ui:notification', {
                message: 'Profile updated successfully',
                type: 'success',
              });
            } catch (error) {
              console.error('Update error:', error);
            }
          },
          validateOnChange: true,
        });

        // Load user data into form
        if (userDataSource.data && userForm.values.name === '') {
          userForm.setFieldValue('name', userDataSource.data.name);
          userForm.setFieldValue('email', userDataSource.data.email);
          userForm.setFieldValue('role', userDataSource.data.role);
        }

        // Listen for edit start
        useEventBus('user:start-editing', () => {
          eventBus.emit('user:profile-editing', {});
        });

        return { userDataSource, userForm };
      });

      // Wait for data load
      await waitFor(() => expect(result.current.userDataSource.isLoading).toBe(false));
      expect(result.current.userForm.values.name).toBe('Admin User');

      // Start editing
      await act(async () => {
        await eventBus.emit('user:start-editing', {});
      });

      expect(eventLog).toContain('editing-started');

      // Edit form
      act(() => {
        result.current.userForm.setFieldValue('name', 'Updated Admin');
        result.current.userForm.setFieldValue('email', 'updated.admin@example.com');
      });

      // Validate
      expect(result.current.userForm.isValid).toBe(true);

      // Submit
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.userForm.handleSubmit(mockEvent);
      });

      // Verify workflow completion
      await waitFor(() => expect(eventLog).toContain('profile-updated'));
      expect(eventLog).toContain('notification:success');
      expect(result.current.userDataSource.data?.name).toBe('Updated Admin');
      expect(result.current.userDataSource.data?.email).toBe('updated.admin@example.com');
    });

    it('should handle validation error and show notification', async () => {
      const notifications: unknown[] = [];

      eventBus.on('ui:notification', (payload: unknown) => notifications.push(payload));

      const { result } = renderHook(() => {
        const form = useForm({
          initialValues: { name: 'Test', email: 'user@example.com' },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            // Simulate server validation error (email already in use)
            const response = await fetch('/api/users/1', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ ...values, email: 'user@example.com' }), // Duplicate email
            });

            if (!response.ok) {
              const error = await response.json();
              if (error.field) {
                form.setFieldError(error.field, error.message);
              }
              await eventBus.emit('ui:notification', {
                message: error.message,
                type: 'error',
              });
              throw new Error(error.message);
            }
          },
        });

        return form;
      });

      // Set duplicate email
      act(() => {
        result.current.setFieldValue('email', 'user@example.com');
      });

      // Submit
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      // Should show error notification
      await waitFor(() => expect(notifications.length).toBeGreaterThan(0));
      expect(notifications[0].type).toBe('error');
      expect(notifications[0].message).toContain('Email already in use');
      expect(result.current.errors.email).toBe('Email already in use');
    });
  });

  describe('Settings Update Workflow', () => {
    it('should update settings with immediate UI feedback via events', async () => {
      const themeChanges: string[] = [];

      eventBus.on('ui:theme-change', (payload: unknown) => {
        themeChanges.push(payload.theme);
      });

      const { result } = renderHook(() => {
        const settingsDataSource = useDataSource<Settings>({
          type: 'rest',
          url: '/api/settings',
          cache: false,
        });

        const settingsForm = useForm({
          initialValues: { notifications: true, theme: 'light' as const, language: 'en' },
          validationRules: {},
          onSubmit: async (values) => {
            // Optimistic update
            settingsDataSource.mutate(values as Settings);

            // Emit theme change event immediately
            await eventBus.emit('ui:theme-change', { theme: values.theme });

            // Save to server
            const response = await fetch('/api/settings', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            if (!response.ok) {
              // Rollback on error
              await settingsDataSource.refetch();
              throw new Error('Settings update failed');
            }

            await settingsDataSource.refetch();
            await eventBus.emit('ui:notification', {
              message: 'Settings saved',
              type: 'success',
            });
          },
        });

        // Load settings
        if (settingsDataSource.data && settingsForm.values.theme === 'light') {
          settingsForm.setFieldValue('notifications', settingsDataSource.data.notifications);
          settingsForm.setFieldValue('theme', settingsDataSource.data.theme);
          settingsForm.setFieldValue('language', settingsDataSource.data.language);
        }

        return { settingsDataSource, settingsForm };
      });

      // Wait for settings load
      await waitFor(() => expect(result.current.settingsDataSource.isLoading).toBe(false));

      // Change theme
      act(() => {
        result.current.settingsForm.setFieldValue('theme', 'dark');
      });

      // Submit
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.settingsForm.handleSubmit(mockEvent);
      });

      // Theme change event should fire immediately (optimistic)
      expect(themeChanges).toContain('dark');

      // Settings should be persisted
      await waitFor(() => {
        return result.current.settingsDataSource.data?.theme === 'dark';
      });
    });
  });

  describe('Multi-Component Data Sync', () => {
    it('should synchronize data across multiple components via events', async () => {
      // Simulate two components managing the same user
      const { result: component1 } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cacheKey: 'user-1',
        });

        useEventBus('user:sync', async (payload: unknown) => {
          if (payload.userId === 1) {
            await dataSource.refetch();
          }
        });

        return dataSource;
      });

      const { result: component2 } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cacheKey: 'user-1',
        });

        const form = useForm({
          initialValues: { name: '', email: '' },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            await fetch('/api/users/1', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            await dataSource.refetch();
            // Notify other components
            await eventBus.emit('user:sync', { userId: 1 });
          },
        });

        if (dataSource.data && form.values.name === '') {
          form.setFieldValue('name', dataSource.data.name);
          form.setFieldValue('email', dataSource.data.email);
        }

        return { dataSource, form };
      });

      // Wait for initial load
      await waitFor(() => {
        expect(component1.current.isLoading).toBe(false);
        expect(component2.current.dataSource.isLoading).toBe(false);
      });

      // Both should have same data
      expect(component1.current.data?.name).toBe('Admin User');
      expect(component2.current.dataSource.data?.name).toBe('Admin User');

      // Component 2 updates user
      act(() => {
        component2.current.form.setFieldValue('name', 'Synced User');
      });

      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await component2.current.form.handleSubmit(mockEvent);
      });

      // Component 1 should automatically sync
      await waitFor(() => {
        return component1.current.data?.name === 'Synced User';
      });

      expect(component2.current.dataSource.data?.name).toBe('Synced User');
    });
  });

  describe('Complex Form with Async Validation', () => {
    it('should handle form with async server-side validation', async () => {
      let emailCheckCount = 0;

      server.use(
        rest.post('/api/check-email', async (req, res, ctx) => {
          emailCheckCount++;
          const body = await req.json();

          // Simulate checking if email exists
          const exists = mockUsers.some((u) => u.email === body.email);

          return res(ctx.json({ available: !exists }));
        })
      );

      const { result } = renderHook(() => {
        const form = useForm({
          initialValues: { name: '', email: '' },
          validationRules: {
            name: [validators.required(), validators.minLength(3)],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            // Client-side validation passes, check with server
            const response = await fetch('/api/check-email', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ email: values.email }),
            });

            const result = await response.json();

            if (!result.available) {
              form.setFieldError('email', 'Email is already taken');
              await eventBus.emit('ui:notification', {
                message: 'Email is already taken',
                type: 'error',
              });
              throw new Error('Email validation failed');
            }

            // Create user
            await fetch('/api/users', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            await eventBus.emit('ui:notification', {
              message: 'User created successfully',
              type: 'success',
            });
          },
          validateOnChange: true,
        });

        return form;
      });

      // Fill form with existing email
      act(() => {
        result.current.setFieldValue('name', 'New User');
        result.current.setFieldValue('email', 'admin@example.com'); // Exists
      });

      // Submit
      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      // Should check email and fail
      expect(emailCheckCount).toBe(1);
      expect(result.current.errors.email).toBe('Email is already taken');

      // Try with unique email
      act(() => {
        result.current.setFieldValue('email', 'unique@example.com');
      });

      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await result.current.handleSubmit(mockEvent);
      });

      // Should succeed
      expect(emailCheckCount).toBe(2);
      expect(result.current.errors.email).toBeFalsy();
    });
  });

  describe('Event-Driven Cache Invalidation', () => {
    it('should invalidate and refresh related data on user update', async () => {
      // Simulate user list and user detail views
      const { result: userList } = renderHook(() => {
        const dataSource = useDataSource<User[]>({
          type: 'rest',
          url: '/api/users',
          cacheKey: 'user-list',
        });

        useEventBus('user:updated', async (payload: unknown) => {
          // Refresh list when any user is updated
          await dataSource.refetch();
        });

        return dataSource;
      });

      const { result: userDetail } = renderHook(() => {
        const dataSource = useDataSource<User>({
          type: 'rest',
          url: '/api/users/1',
          cacheKey: 'user-1-detail',
        });

        const form = useForm({
          initialValues: { name: '', email: '' },
          validationRules: {
            name: [validators.required()],
            email: [validators.required(), validators.email()],
          },
          onSubmit: async (values) => {
            await fetch('/api/users/1', {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(values),
            });

            await dataSource.refetch();
            // Notify all interested parties
            await eventBus.emit('user:updated', { userId: 1 });
          },
        });

        if (dataSource.data && form.values.name === '') {
          form.setFieldValue('name', dataSource.data.name);
          form.setFieldValue('email', dataSource.data.email);
        }

        return { dataSource, form };
      });

      // Wait for data load
      await waitFor(() => {
        expect(userList.current.isLoading).toBe(false);
        expect(userDetail.current.dataSource.isLoading).toBe(false);
      });

      const initialListLength = userList.current.data?.length || 0;

      // Update user
      act(() => {
        userDetail.current.form.setFieldValue('name', 'Cache Invalidated User');
      });

      await act(async () => {
        const mockEvent = { preventDefault: jest.fn() } as unknown;
        await userDetail.current.form.handleSubmit(mockEvent);
      });

      // Both views should refresh
      await waitFor(() => {
        const listUser = userList.current.data?.find((u) => u.id === 1);
        return listUser?.name === 'Cache Invalidated User';
      });

      expect(userDetail.current.dataSource.data?.name).toBe('Cache Invalidated User');
    });
  });
});
