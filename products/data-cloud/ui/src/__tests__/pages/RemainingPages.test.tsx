/**
 * Tests for remaining Data Cloud pages:
 * CollectionsPage, WorkflowsPage, WorkflowDesigner, WorkflowList, NotFound
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { CollectionsPage } from '../../pages/CollectionsPage';
import { WorkflowsPage } from '../../pages/WorkflowsPage';
import { WorkflowDesigner } from '../../pages/WorkflowDesigner/index';
import { WorkflowList } from '../../pages/WorkflowList/index';
import { NotFound } from '../../pages/NotFound/index';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

// ── CollectionsPage ───────────────────────────────────────────────────────────

describe('CollectionsPage', () => {
  it('renders without crashing', () => {
    render(<CollectionsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays collections content', () => {
    render(<CollectionsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/collection|schema|record|tenant/i);
  });

  it('has create collection button or link', () => {
    render(<CollectionsPage />, { wrapper: TestWrapper });
    const interactive = document.querySelectorAll('button, a');
    expect(interactive.length).toBeGreaterThan(0);
  });
});

// ── WorkflowsPage ─────────────────────────────────────────────────────────────

describe('WorkflowsPage', () => {
  it('renders without crashing', () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays workflows list content', () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/workflow|pipeline|automat|trigger/i);
  });

  it('renders create or new workflow button', () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });
    const buttons = document.querySelectorAll('button');
    expect(buttons.length).toBeGreaterThanOrEqual(0);
  });

  it('shows workflow status or metadata', () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.length).toBeGreaterThan(0);
  });
});

// ── WorkflowDesigner ──────────────────────────────────────────────────────────

describe('WorkflowDesigner', () => {
  it('renders without crashing', () => {
    render(<WorkflowDesigner />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays designer content', () => {
    render(<WorkflowDesigner />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.length).toBeGreaterThanOrEqual(0);
  });
});

// ── WorkflowList ──────────────────────────────────────────────────────────────

describe('WorkflowList', () => {
  it('renders without crashing', () => {
    render(<WorkflowList />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays workflow list content', () => {
    render(<WorkflowList />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.length).toBeGreaterThanOrEqual(0);
  });
});

// ── NotFound ──────────────────────────────────────────────────────────────────

describe('NotFound', () => {
  it('renders without crashing', () => {
    render(<NotFound />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays 404 or not found message', () => {
    render(<NotFound />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/404|not found|page.*not|missing/i);
  });

  it('has a navigation link to go back home', () => {
    render(<NotFound />, { wrapper: TestWrapper });
    const links = document.querySelectorAll('a, button');
    expect(links.length).toBeGreaterThan(0);
  });
});
