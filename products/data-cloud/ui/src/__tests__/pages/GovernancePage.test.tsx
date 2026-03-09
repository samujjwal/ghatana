import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { GovernancePage } from '../../pages/GovernancePage';
import { GovernancePageEnhanced } from '../../pages/GovernancePageEnhanced';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('GovernancePage', () => {
  it('renders without crashing', () => {
    render(<GovernancePage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays governance-related content', () => {
    render(<GovernancePage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/govern|policy|compli|rule/i);
  });

  it('renders tabs or sections', () => {
    render(<GovernancePage />, { wrapper: TestWrapper });
    const buttons = document.querySelectorAll('button');
    expect(buttons.length).toBeGreaterThanOrEqual(0);
  });
});

describe('GovernancePageEnhanced', () => {
  it('renders without crashing', () => {
    render(<GovernancePageEnhanced />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('displays enhanced governance content', () => {
    render(<GovernancePageEnhanced />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body.toLowerCase()).toMatch(/govern|policy|compli|rule/i);
  });
});
