/**
 * Legacy canvas route tests.
 *
 * Canvas design now lives in the canonical Shape phase. The `/canvas` route
 * remains only as a compatibility redirect for existing links.
 *
 * @doc.type test
 * @doc.purpose Verify legacy canvas route redirect behavior
 * @doc.layer routes
 */

import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';

const navigateMock = vi.fn();

vi.mock('react-router', () => ({
  useParams: () => ({ projectId: 'project-1' }),
  useNavigate: () => navigateMock,
}));

import CanvasRedirectRoute from '../canvas';

describe('Legacy canvas route', () => {
  beforeEach(() => {
    navigateMock.mockClear();
  });

  it('redirects to the canonical Shape phase route', () => {
    render(<CanvasRedirectRoute />);

    expect(navigateMock).toHaveBeenCalledWith('/p/project-1/shape', {
      replace: true,
    });
  });

  it('shows an interim redirect status while navigation is scheduled', () => {
    render(<CanvasRedirectRoute />);

    expect(screen.getByText('Redirecting to Shape phase...')).toBeInTheDocument();
  });
});
