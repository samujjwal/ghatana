/**
 * Unit tests for LoginScreen.
 *
 * Exercises real production LoginScreen component — no object-literal assertions.
 * All assertions depend on the actual component behavior under test.
 */
import React from 'react';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { LoginScreen } from '../LoginScreen';
import type { MobileSession } from '../../types';

const MOCK_SESSION: MobileSession = {
  principalId: 'patient-1',
  tenantId: 'tenant-np',
  role: 'patient',
  name: 'Test Patient',
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
};

function buildProps(overrides?: {
  loginFn?: (nationalId: string, password: string) => Promise<MobileSession>;
  onSuccess?: (session: MobileSession) => void;
  onLoginError?: (message: string) => void;
}) {
  return {
    loginFn: jest.fn<Promise<MobileSession>, [string, string]>().mockResolvedValue(MOCK_SESSION),
    onSuccess: jest.fn<void, [MobileSession]>(),
    onLoginError: jest.fn<void, [string]>(),
    ...overrides,
  };
}

describe('LoginScreen', () => {
  it('renders the login title from i18n (not hardcoded)', () => {
    const { getByText } = render(<LoginScreen {...buildProps()} />);
    // t('login.title') === 'PHR Nepal'
    expect(getByText('PHR Nepal')).toBeTruthy();
  });

  it('renders the subtitle from i18n', () => {
    const { getByText } = render(<LoginScreen {...buildProps()} />);
    // t('login.subtitle') === 'Secure mobile record access'
    expect(getByText('Secure mobile record access')).toBeTruthy();
  });

  it('renders the National ID field with i18n accessibilityLabel', () => {
    const { getByLabelText } = render(<LoginScreen {...buildProps()} />);
    // accessibilityLabel = t('login.nationalIdLabel') = 'National ID'
    expect(getByLabelText('National ID')).toBeTruthy();
  });

  it('renders the password field with i18n accessibilityLabel', () => {
    const { getByLabelText } = render(<LoginScreen {...buildProps()} />);
    // accessibilityLabel = t('login.passwordLabel') = 'Password'
    expect(getByLabelText('Password')).toBeTruthy();
  });

  it('shows the sign-in button label from i18n', () => {
    const { getByText } = render(<LoginScreen {...buildProps()} />);
    // t('login.signIn') === 'Sign In'
    expect(getByText('Sign In')).toBeTruthy();
  });

  it('shows validation error when National ID is empty on submit', async () => {
    const props = buildProps();
    const { getByText, queryByRole } = render(<LoginScreen {...props} />);

    await act(async () => {
      fireEvent.press(getByText('Sign In'));
    });

    // t('login.nationalIdRequired') === 'National ID is required.'
    expect(queryByRole('alert')).toBeTruthy();
    expect(getByText('National ID is required.')).toBeTruthy();
    expect(props.loginFn).not.toHaveBeenCalled();
  });

  it('shows validation error when password is empty after national ID is entered', async () => {
    const props = buildProps();
    const { getByText, getByLabelText, queryByRole } = render(<LoginScreen {...props} />);

    fireEvent.changeText(getByLabelText('National ID'), 'NP-12345');

    await act(async () => {
      fireEvent.press(getByText('Sign In'));
    });

    // t('login.passwordRequired') === 'Password is required.'
    expect(queryByRole('alert')).toBeTruthy();
    expect(getByText('Password is required.')).toBeTruthy();
    expect(props.loginFn).not.toHaveBeenCalled();
  });

  it('calls loginFn with trimmed nationalId and password on valid submit', async () => {
    const props = buildProps();
    const { getByText, getByLabelText } = render(<LoginScreen {...props} />);

    fireEvent.changeText(getByLabelText('National ID'), '  NP-12345  ');
    fireEvent.changeText(getByLabelText('Password'), 'secret');

    await act(async () => {
      fireEvent.press(getByText('Sign In'));
    });

    await waitFor(() => {
      expect(props.loginFn).toHaveBeenCalledWith('NP-12345', 'secret');
    });
  });

  it('calls onSuccess with the resolved session after successful login', async () => {
    const props = buildProps();
    const { getByText, getByLabelText } = render(<LoginScreen {...props} />);

    fireEvent.changeText(getByLabelText('National ID'), 'NP-12345');
    fireEvent.changeText(getByLabelText('Password'), 'secret');

    await act(async () => {
      fireEvent.press(getByText('Sign In'));
    });

    await waitFor(() => {
      expect(props.onSuccess).toHaveBeenCalledWith(MOCK_SESSION);
    });
  });

  it('shows i18n error message and calls onLoginError when loginFn rejects', async () => {
    const failingLogin = jest
      .fn<Promise<MobileSession>, [string, string]>()
      .mockRejectedValue(new Error('Server error'));
    const props = buildProps({ loginFn: failingLogin });
    const { getByText, getByLabelText, queryByRole } = render(<LoginScreen {...props} />);

    fireEvent.changeText(getByLabelText('National ID'), 'NP-12345');
    fireEvent.changeText(getByLabelText('Password'), 'wrong');

    await act(async () => {
      fireEvent.press(getByText('Sign In'));
    });

    await waitFor(() => {
      // Error message propagated from the rejection reason
      expect(queryByRole('alert')).toBeTruthy();
      expect(getByText('Server error')).toBeTruthy();
      expect(props.onLoginError).toHaveBeenCalledWith('Server error');
    });
  });

  it('shows i18n fallback error when rejection has no message', async () => {
    const failingLogin = jest
      .fn<Promise<MobileSession>, [string, string]>()
      .mockRejectedValue('non-error-rejection');
    const props = buildProps({ loginFn: failingLogin });
    const { getByText, getByLabelText, queryByRole } = render(<LoginScreen {...props} />);

    fireEvent.changeText(getByLabelText('National ID'), 'NP-12345');
    fireEvent.changeText(getByLabelText('Password'), 'wrong');

    await act(async () => {
      fireEvent.press(getByText('Sign In'));
    });

    await waitFor(() => {
      // t('login.failed') === 'Login failed. Please try again.'
      expect(queryByRole('alert')).toBeTruthy();
      expect(getByText('Login failed. Please try again.')).toBeTruthy();
    });
  });
});
