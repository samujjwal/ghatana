/**
 * Unit tests for LoginScreen.
 *
 * Exercises real production LoginScreen component — no object-literal assertions.
 * All assertions depend on the actual component behavior under test.
 */
import React from 'react';
import { act, render, waitFor } from '@testing-library/react-native';
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

interface TestNodeWithProps {
  props: Record<string, unknown>;
}

function renderedText(rendered: { toJSON: () => unknown }): string {
  return JSON.stringify(rendered.toJSON());
}

function renderLoginScreen(props = buildProps()): ReturnType<typeof render> {
  return render(<LoginScreen {...props} />);
}

function pressNode(node: TestNodeWithProps): void {
  const onPress = node.props.onPress;
  if (typeof onPress !== 'function') {
    throw new Error('Expected rendered node to expose an onPress handler.');
  }
  onPress();
}

function changeTextNode(node: TestNodeWithProps, value: string): void {
  const onChangeText = node.props.onChangeText;
  if (typeof onChangeText !== 'function') {
    throw new Error('Expected rendered input to expose an onChangeText handler.');
  }
  onChangeText(value);
}

describe('LoginScreen', () => {
  it('renders the login title from i18n (not hardcoded)', () => {
    const rendered = renderLoginScreen();
    expect(renderedText(rendered)).toContain('PHR Nepal');
  });

  it('renders the subtitle from i18n', () => {
    const rendered = render(<LoginScreen {...buildProps()} />);
    expect(renderedText(rendered)).toContain('Secure mobile record access');
  });

  it('renders the National ID field with i18n accessibilityLabel', () => {
    const { UNSAFE_getByProps } = render(<LoginScreen {...buildProps()} />);
    expect(UNSAFE_getByProps({ accessibilityLabel: 'National ID' })).toBeTruthy();
  });

  it('renders the password field with i18n accessibilityLabel', () => {
    const { UNSAFE_getByProps } = render(<LoginScreen {...buildProps()} />);
    expect(UNSAFE_getByProps({ accessibilityLabel: 'Password' })).toBeTruthy();
  });

  it('shows the sign-in button label from i18n', () => {
    const rendered = render(<LoginScreen {...buildProps()} />);
    expect(renderedText(rendered)).toContain('Sign In');
  });

  it('shows validation error when National ID is empty on submit', async () => {
    const props = buildProps();
    const rendered = render(<LoginScreen {...props} />);

    await act(async () => {
      pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Sign In' }));
    });

    expect(renderedText(rendered)).toContain('National ID is required.');
    expect(props.loginFn).not.toHaveBeenCalled();
  });

  it('shows validation error when password is empty after national ID is entered', async () => {
    const props = buildProps();
    const rendered = render(<LoginScreen {...props} />);

    act(() => {
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'National ID' }), 'NP-12345');
    });

    await act(async () => {
      pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Sign In' }));
    });

    expect(renderedText(rendered)).toContain('Password is required.');
    expect(props.loginFn).not.toHaveBeenCalled();
  });

  it('calls loginFn with trimmed nationalId and password on valid submit', async () => {
    const props = buildProps();
    const rendered = render(<LoginScreen {...props} />);

    act(() => {
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'National ID' }), '  NP-12345  ');
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Password' }), 'secret');
    });

    await act(async () => {
      pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Sign In' }));
    });

    await waitFor(() => {
      expect(props.loginFn).toHaveBeenCalledWith('NP-12345', 'secret');
    });
  });

  it('calls onSuccess with the resolved session after successful login', async () => {
    const props = buildProps();
    const rendered = render(<LoginScreen {...props} />);

    act(() => {
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'National ID' }), 'NP-12345');
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Password' }), 'secret');
    });

    await act(async () => {
      pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Sign In' }));
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
    const rendered = render(<LoginScreen {...props} />);

    act(() => {
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'National ID' }), 'NP-12345');
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Password' }), 'wrong');
    });

    await act(async () => {
      pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Sign In' }));
    });

    await waitFor(() => {
      expect(renderedText(rendered)).toContain('Server error');
      expect(props.onLoginError).toHaveBeenCalledWith('Server error');
    });
  });

  it('shows i18n fallback error when rejection has no message', async () => {
    const failingLogin = jest
      .fn<Promise<MobileSession>, [string, string]>()
      .mockRejectedValue('non-error-rejection');
    const props = buildProps({ loginFn: failingLogin });
    const rendered = render(<LoginScreen {...props} />);

    act(() => {
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'National ID' }), 'NP-12345');
      changeTextNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Password' }), 'wrong');
    });

    await act(async () => {
      pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'Sign In' }));
    });

    await waitFor(() => {
      expect(renderedText(rendered)).toContain('Login failed. Please try again.');
    });
  });
});
