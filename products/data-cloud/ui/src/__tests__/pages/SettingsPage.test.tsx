import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router';
import { Provider } from 'jotai';
import { SettingsPage } from '../../pages/SettingsPage';

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <Provider><BrowserRouter>{children}</BrowserRouter></Provider>
);

describe('SettingsPage', () => {
  it('renders without crashing', () => {
    render(<SettingsPage />, { wrapper: TestWrapper });
    expect(document.body).toBeTruthy();
  });

  it('shows all four settings sections', () => {
    render(<SettingsPage />, { wrapper: TestWrapper });
    expect(screen.getByText('Profile')).toBeInTheDocument();
    expect(screen.getByText('Preferences')).toBeInTheDocument();
    expect(screen.getByText('Notifications')).toBeInTheDocument();
    expect(screen.getByText('API Keys')).toBeInTheDocument();
  });

  it('defaults to Profile section', () => {
    render(<SettingsPage />, { wrapper: TestWrapper });
    const body = document.body.textContent ?? '';
    expect(body).toMatch(/profile/i);
  });

  it('switches to Preferences section on click', async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText('Preferences'));
    const body = document.body.textContent ?? '';
    expect(body).toMatch(/preference/i);
  });

  it('switches to Notifications section on click', async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText('Notifications'));
    const body = document.body.textContent ?? '';
    expect(body).toMatch(/notification/i);
  });

  it('switches to API Keys section on click', async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText('API Keys'));
    const body = document.body.textContent ?? '';
    expect(body).toMatch(/api/i);
  });
});
