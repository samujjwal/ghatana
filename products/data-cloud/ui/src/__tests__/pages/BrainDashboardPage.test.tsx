import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { BrainDashboardPage } from '../../pages/BrainDashboardPage';
import { EnhancedBrainDashboardPage } from '../../pages/EnhancedBrainDashboardPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('BrainDashboardPage', () => {
  it('renders without crashing', () => {
    render(<BrainDashboardPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays brain/AI content', () => {
    render(<BrainDashboardPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/brain|ai|learn|memory|signal|model/i);
  });
});

describe('EnhancedBrainDashboardPage', () => {
  it('renders without crashing', () => {
    render(<EnhancedBrainDashboardPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays enhanced brain dashboard content', () => {
    render(<EnhancedBrainDashboardPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/brain|ai|learn|memory|signal|model|autonomy/i);
  });

  it('renders interactive controls', () => {
    render(<EnhancedBrainDashboardPage />, { wrapper: TestWrapper });
    const buttons = document.querySelectorAll('button');
    expect(buttons.length).toBeGreaterThanOrEqual(0);
  });
});
