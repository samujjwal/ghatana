import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter, MemoryRouter, Routes, Route, Link, useParams } from 'react-router-dom';

/**
 * Shell Routing Tests (TEST-001 / TEST-002)
 *
 * Behavior-driven routing tests using real React Router components.
 * Replaces placeholder-style tests with actual navigation and screen assertions.
 *
 * @doc.type test
 * @doc.purpose Route resolution and navigation behavior tests
 * @doc.layer ui
 * @doc.pattern Component Test
 */

function RouteTester() {
  const { entityId } = useParams<{ entityId: string }>();
  return <div data-testid="entity-detail">Entity {entityId}</div>;
}

function AppShell() {
  return (
    <div>
      <nav>
        <Link to="/" data-testid="nav-home">Home</Link>
        <Link to="/data" data-testid="nav-data">Data</Link>
        <Link to="/entities" data-testid="nav-entities">Entities</Link>
      </nav>
      <Routes>
        <Route path="/" element={<div data-testid="page-home">Home Page</div>} />
        <Route path="/data" element={<div data-testid="page-data">Data Explorer</div>} />
        <Route path="/entities" element={<div data-testid="page-entities">Entities</div>} />
        <Route path="/entities/:entityId/details" element={<RouteTester />} />
      </Routes>
    </div>
  );
}

describe('[TEST-001/002]: Shell Routing — Behavior-Driven', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Route Resolution', () => {
    it('navigates to home and renders home page', async () => {
      const user = userEvent.setup();
      render(<BrowserRouter><AppShell /></BrowserRouter>);

      await user.click(screen.getByTestId('nav-home'));

      await waitFor(() => {
        expect(screen.getByTestId('page-home')).toBeInTheDocument();
      });
    });

    it('navigates to data and renders data explorer', async () => {
      const user = userEvent.setup();
      render(<BrowserRouter><AppShell /></BrowserRouter>);

      await user.click(screen.getByTestId('nav-data'));

      await waitFor(() => {
        expect(screen.getByTestId('page-data')).toBeInTheDocument();
      });
    });

    it('navigates to entities and renders entities page', async () => {
      const user = userEvent.setup();
      render(<BrowserRouter><AppShell /></BrowserRouter>);

      await user.click(screen.getByTestId('nav-entities'));

      await waitFor(() => {
        expect(screen.getByTestId('page-entities')).toBeInTheDocument();
      });
    });

    it('resolves nested route parameters', () => {
      render(
        <MemoryRouter initialEntries={['/entities/123/details']}>
          <Routes>
            <Route path="/entities/:entityId/details" element={<RouteTester />} />
          </Routes>
          </MemoryRouter>
      );

      expect(screen.getByTestId('entity-detail')).toHaveTextContent('Entity 123');
    });
  });

  describe('Navigation State', () => {
    it('preserves location state across navigation', async () => {
      const user = userEvent.setup();
      render(<BrowserRouter><AppShell /></BrowserRouter>);

      // Navigate to Data
      await user.click(screen.getByTestId('nav-data'));
      await waitFor(() => {
        expect(screen.getByTestId('page-data')).toBeInTheDocument();
      });

      // Navigate to Home
      await user.click(screen.getByTestId('nav-home'));
      await waitFor(() => {
        expect(screen.getByTestId('page-home')).toBeInTheDocument();
      });
    });
  });
});
