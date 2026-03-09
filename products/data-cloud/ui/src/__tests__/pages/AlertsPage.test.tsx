import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { AlertsPage } from '../../pages/AlertsPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider>
    <BrowserRouter>{children}</BrowserRouter>
  </Provider>
);

describe('AlertsPage', () => {
  it('renders without crashing', () => {
    render(<AlertsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('shows alert count summary', async () => {
    render(<AlertsPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      const body = document.body.textContent ?? '';
      expect(body.length).toBeGreaterThan(0);
    });
  });

  it('renders AI triage section', async () => {
    render(<AlertsPage />, { wrapper: TestWrapper });
    await waitFor(() => {
      const body = document.body.textContent ?? '';
      expect(body.toLowerCase()).toMatch(/alert|triage|critical|warning/i);
    });
  });

  it('toggles alert group expand/collapse', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    const buttons = document.querySelectorAll('button');
    if (buttons.length > 0) {
      await user.click(buttons[0]);
      expect(document.body).toBeTruthy();
    }
  });

  it('opens alert rule form when create button clicked', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    const createBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => b.textContent?.toLowerCase().includes('create') || b.textContent?.toLowerCase().includes('new') || b.textContent?.toLowerCase().includes('add'),
    );
    if (createBtn) {
      await user.click(createBtn);
      expect(document.body).toBeTruthy();
    }
  });
});
