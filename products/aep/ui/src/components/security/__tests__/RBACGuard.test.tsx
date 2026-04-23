/**
 * Tests for RBACGuard component
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RBACGuard, usePermission, usePermissions } from '../RBACGuard';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';

vi.mock('@/lib/http-client', () => ({
  apiClient: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import { apiClient } from '@/lib/http-client';
const apiClientMock = apiClient as unknown as { post: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn>; put: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };

function renderWithProviders(ui: React.ReactElement) {
  return render(ui, { wrapper: createAepTestWrapper() });
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('RBACGuard', () => {

  it('renders children when permission is granted', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: { granted: true },
      status: 200,
      headers: new Headers(),
    });

    renderWithProviders(
      <RBACGuard permission="read:resource">
        <div>Protected Content</div>
      </RBACGuard>
    );

    await screen.findByText('Protected Content');
  });

  it('renders fallback when permission is denied', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: { granted: false },
      status: 200,
      headers: new Headers(),
    });

    renderWithProviders(
      <RBACGuard
        permission="read:resource"
        fallback={<div>Access Denied</div>}
      >
        <div>Protected Content</div>
      </RBACGuard>
    );

    await screen.findByText('Access Denied');
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('renders loading fallback while checking permission', () => {
    apiClientMock.post.mockImplementationOnce(() => new Promise(() => {}));

    renderWithProviders(
      <RBACGuard
        permission="read:resource"
        loadingFallback={<div>Loading...</div>}
        fallback={<div>Access Denied</div>}
      >
        <div>Protected Content</div>
      </RBACGuard>
    );

    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('renders fallback when permission check fails', async () => {
    apiClientMock.post.mockRejectedValueOnce(new Error('Network error'));

    renderWithProviders(
      <RBACGuard
        permission="read:resource"
        fallback={<div>Access Denied</div>}
      >
        <div>Protected Content</div>
      </RBACGuard>
    );

    await screen.findByText('Access Denied');
  });

  it('sends correct request with resource and action', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: { granted: true },
      status: 200,
      headers: new Headers(),
    });

    renderWithProviders(
      <RBACGuard
        permission="write:resource"
        resource="pipeline-123"
        action="write"
      >
        <div>Protected Content</div>
      </RBACGuard>
    );

    await screen.findByText('Protected Content');

    expect(apiClientMock.post).toHaveBeenCalledWith(
      '/api/v1/auth/check-permission',
      expect.objectContaining({
        permission: 'write:resource',
        resource: 'pipeline-123',
        action: 'write',
      })
    );
  });

  it('uses custom endpoint when provided', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: { granted: true },
      status: 200,
      headers: new Headers(),
    });

    renderWithProviders(
      <RBACGuard
        permission="read:resource"
        endpoint="/custom/permission/check"
      >
        <div>Protected Content</div>
      </RBACGuard>
    );

    await screen.findByText('Protected Content');

    expect(apiClientMock.post).toHaveBeenCalledWith(
      '/custom/permission/check',
      expect.any(Object)
    );
  });

  it('renders nothing when permission denied and no fallback provided', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: { granted: false },
      status: 200,
      headers: new Headers(),
    });

    renderWithProviders(
      <RBACGuard permission="read:resource">
        <div>Protected Content</div>
      </RBACGuard>
    );

    await new Promise(resolve => setTimeout(resolve, 100));

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });
});

describe('usePermission hook', () => {
  it('returns permission status', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: { granted: true },
      status: 200,
      headers: new Headers(),
    });

    const TestComponent = () => {
      const { hasPermission, isLoading } = usePermission('read:resource');
      return (
        <div>
          <span>{hasPermission ? 'Granted' : 'Denied'}</span>
          <span>{isLoading ? 'Loading' : 'Loaded'}</span>
        </div>
      );
    };

    renderWithProviders(<TestComponent />);

    await screen.findByText('Granted');
    await screen.findByText('Loaded');
  });

  it('returns false when permission check fails', async () => {
    apiClientMock.post.mockRejectedValueOnce(new Error('Network error'));

    const TestComponent = () => {
      const { hasPermission } = usePermission('read:resource');
      return <div>{hasPermission ? 'Granted' : 'Denied'}</div>;
    };

    renderWithProviders(<TestComponent />);

    await screen.findByText('Denied');
  });

  it('provides refetch function', () => {
    const TestComponent = () => {
      const { refetch } = usePermission('read:resource');
      return (
        <button onClick={() => refetch()}>Refetch</button>
      );
    };

    renderWithProviders(<TestComponent />);

    const button = screen.getByText('Refetch');
    expect(button).toBeInTheDocument();
  });
});

describe('usePermissions hook', () => {
  it('checks multiple permissions at once', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: {
        '[{"permission":"read:resource"}]': true,
        '[{"permission":"write:resource"}]': false,
      },
      status: 200,
      headers: new Headers(),
    });

    const TestComponent = () => {
      const { results, isLoading } = usePermissions([
        { permission: 'read:resource' },
        { permission: 'write:resource' },
      ]);
      return (
        <div>
          <span>{isLoading ? 'Loading' : 'Loaded'}</span>
          <span>{JSON.stringify(results)}</span>
        </div>
      );
    };

    renderWithProviders(<TestComponent />);

    await screen.findByText('Loaded');
  });

  it('provides checkPermission function', async () => {
    apiClientMock.post.mockResolvedValueOnce({
      data: {
        '[{"permission":"read:resource"}]': true,
        '[{"permission":"write:resource"}]': false,
      },
      status: 200,
      headers: new Headers(),
    });

    const TestComponent = () => {
      const { checkPermission } = usePermissions([
        { permission: 'read:resource' },
        { permission: 'write:resource' },
      ]);
      return (
        <div>
          <span>{checkPermission('read:resource') ? 'Read Granted' : 'Read Denied'}</span>
          <span>{checkPermission('write:resource') ? 'Write Granted' : 'Write Denied'}</span>
        </div>
      );
    };

    renderWithProviders(<TestComponent />);

    await screen.findByText('Read Granted');
    await screen.findByText('Write Denied');
  });
});
