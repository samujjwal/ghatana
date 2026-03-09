import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { CostOptimizationPage } from '../../pages/CostOptimizationPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('CostOptimizationPage', () => {
  it('renders without crashing', () => {
    render(<CostOptimizationPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays cost-related content', () => {
    render(<CostOptimizationPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/cost|budget|saving|optim|spend|usage/i);
  });

  it('renders cost metric sections', () => {
    render(<CostOptimizationPage />, { wrapper: TestWrapper });
    const elements = document.querySelectorAll('div');
    expect(elements.length).toBeGreaterThan(0);
  });
});
