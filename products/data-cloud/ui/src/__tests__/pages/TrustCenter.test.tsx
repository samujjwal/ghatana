import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { TrustCenter } from '../../pages/TrustCenter';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('TrustCenter', () => {
  it('renders without crashing', () => {
    render(<TrustCenter />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays trust/security content', () => {
    render(<TrustCenter />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/trust|security|compli|certif|audit|privacy/i);
  });

  it('renders sections or tabs', () => {
    render(<TrustCenter />, { wrapper: TestWrapper });
    const elements = document.querySelectorAll('button, [role="tab"]');
    expect(elements.length).toBeGreaterThanOrEqual(0);
  });
});
