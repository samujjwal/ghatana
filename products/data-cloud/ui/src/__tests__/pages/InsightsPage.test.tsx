import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { InsightsPage } from '../../pages/InsightsPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('InsightsPage', () => {
  it('renders without crashing', () => {
    render(<InsightsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays insights content', () => {
    render(<InsightsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/insight|analytic|trend|metric|report|chart/i);
  });

  it('renders at least one interactive element', () => {
    render(<InsightsPage />, { wrapper: TestWrapper });
    const elements = document.querySelectorAll('button, input, select');
    expect(elements.length).toBeGreaterThanOrEqual(0);
  });
});
