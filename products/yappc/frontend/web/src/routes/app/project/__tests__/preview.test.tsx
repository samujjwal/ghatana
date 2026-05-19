import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

const { mockNavigate } = vi.hoisted(() => ({
  mockNavigate: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useParams: () => ({ projectId: 'proj-42' }),
    useNavigate: () => mockNavigate,
  };
});

import PreviewRoute from '../preview';

describe('preview route', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  it('redirects legacy preview links to the observe phase cockpit', async () => {
    render(<PreviewRoute />);

    expect(screen.getByText('Redirecting to Observe phase...')).toBeDefined();
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/p/proj-42/observe', { replace: true });
    });
  });
});
