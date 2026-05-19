// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import CanvasRedirectRoute from '../../app/project/canvas';

const navigateSpy = vi.fn();

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router');
  return {
    ...actual,
    useNavigate: () => navigateSpy,
  };
});

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="current-location">{location.pathname}</div>;
}

describe('legacy canvas route', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders redirect affordance and sends legacy canvas traffic to Shape', async () => {
    render(
      <MemoryRouter initialEntries={['/project/test-project-1/canvas']}>
        <Routes>
          <Route
            path="/project/:projectId/canvas"
            element={(
              <>
                <CanvasRedirectRoute />
                <LocationProbe />
              </>
            )}
          />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Redirecting to Shape phase...')).toBeInTheDocument();
    await waitFor(() => {
      expect(navigateSpy).toHaveBeenCalledWith('/p/test-project-1/shape', { replace: true });
    });
  });

  it('does not navigate when the project id is absent', async () => {
    render(
      <MemoryRouter initialEntries={['/project/canvas']}>
        <Routes>
          <Route
            path="/project/canvas"
            element={(
              <>
                <CanvasRedirectRoute />
                <LocationProbe />
              </>
            )}
          />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Redirecting to Shape phase...')).toBeInTheDocument();
    await waitFor(() => {
      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });
});
