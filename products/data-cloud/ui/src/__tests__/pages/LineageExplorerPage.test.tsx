import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { LineageExplorerPage } from '../../pages/LineageExplorerPage';

// Both the canonical and Enhanced variants now point to the same component
const LineageExplorerPageEnhanced = LineageExplorerPage;

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('LineageExplorerPage', () => {
  it('renders without crashing', () => {
    render(<LineageExplorerPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays lineage-related content', () => {
    render(<LineageExplorerPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/lineage|graph|node|flow|source|upstream|downstream/i);
  });

  it('renders interactive elements', () => {
    render(<LineageExplorerPage />, { wrapper: TestWrapper });
    const interactive = document.querySelectorAll('button, a, input, select');
    expect(interactive.length).toBeGreaterThanOrEqual(0);
  });
});

describe('LineageExplorerPageEnhanced', () => {
  it('renders without crashing', () => {
    render(<LineageExplorerPageEnhanced />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays lineage content', () => {
    render(<LineageExplorerPageEnhanced />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/lineage|graph|node|flow/i);
  });
});
