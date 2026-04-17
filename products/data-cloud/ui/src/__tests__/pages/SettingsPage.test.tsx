import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestWrapper } from '../test-utils/wrapper';
import { SettingsPage } from '../../pages/SettingsPage';


describe('SettingsPage', () => {
  it('renders the settings shell with sidebar navigation and profile defaults', () => {
    render(<SettingsPage />, { wrapper: TestWrapper });

    expect(screen.getByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Profile Settings' })).toBeInTheDocument();
    expect(screen.getByText(/unavailable in current deployment/i)).toBeInTheDocument();
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
    expect(screen.getByRole('heading', { name: 'Profile Settings' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /profile/i })).toHaveClass('bg-blue-50');
  });

  it('switches to Preferences section on click', async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText('Preferences'));

    expect(screen.getByRole('heading', { name: 'Preferences' })).toBeInTheDocument();
    expect(screen.getByText(/preference persistence is not wired/i)).toBeInTheDocument();
  });

  it('switches to Notifications section on click', async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText('Notifications'));

    expect(screen.getByRole('heading', { name: 'Notification Settings' })).toBeInTheDocument();
    expect(screen.getByText(/notification channel preferences are not backed/i)).toBeInTheDocument();
  });

  it('switches to API Keys section on click', async () => {
    const user = userEvent.setup();
    render(<SettingsPage />, { wrapper: TestWrapper });
    await user.click(screen.getByText('API Keys'));

    expect(screen.getByRole('heading', { name: 'API Keys' })).toBeInTheDocument();
    expect(screen.getByText(/launcher bootstrap/i)).toBeInTheDocument();
  });
});
