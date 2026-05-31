/**
 * Unit tests for ConsentScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { Alert } from 'react-native';
import { act, render, waitFor } from '@testing-library/react-native';
import { ConsentScreen } from '../ConsentScreen';
import type { MobileConsent, MobileSession } from '../../types';

jest.mock('../../services/phrMobileApi', () => ({
  revokeConsentGrant: jest.fn(),
}));

// phrMobileI18n t() returns the key — makes assertions key-stable.
jest.mock('../../i18n/phrMobileI18n', () => ({
  t: (key: string) => key,
}));

import { revokeConsentGrant } from '../../services/phrMobileApi';
const mockRevokeConsentGrant = revokeConsentGrant as jest.MockedFunction<typeof revokeConsentGrant>;

const session: MobileSession = {
  principalId: 'patient-1',
  tenantId: 'tenant-np',
  role: 'patient',
  name: 'Ram Bahadur',
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
};

const consents: MobileConsent[] = [
  { id: 'con-1', grantee: 'Dr. Sharma', purpose: 'Annual review', active: true },
  { id: 'con-2', grantee: 'Clinic ABCD', purpose: 'Lab panel', active: false },
];

function renderedText(rendered: { toJSON: () => unknown }): string {
  return JSON.stringify(rendered.toJSON());
}

async function pressNode(node: { props: Record<string, unknown> }): Promise<void> {
  const onPress = node.props.onPress;
  if (typeof onPress !== 'function') {
    throw new Error('Expected rendered node to expose an onPress handler.');
  }
  const result: unknown = onPress();
  if (result && typeof result === 'object' && 'then' in result) {
    await result;
  }
}

describe('ConsentScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRevokeConsentGrant.mockResolvedValue();
  });

  it('renders empty state when no consents', () => {
    const rendered = render(
      <ConsentScreen consents={[]} session={session} onConsentRevoked={() => {}} />,
    );
    expect(renderedText(rendered)).toContain('consents.empty');
  });

  it('renders grantee names', () => {
    const rendered = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    const text = renderedText(rendered);
    expect(text).toContain('Dr. Sharma');
    expect(text).toContain('Clinic ABCD');
  });

  it('renders consent purpose', () => {
    const rendered = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    expect(renderedText(rendered)).toContain('Annual review');
  });

  it('shows active badge for active consent', () => {
    const rendered = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    expect(renderedText(rendered)).toContain('consents.active');
  });

  it('shows revoke button only for active consents', () => {
    const { getAllByLabelText } = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    const revokeButtons = getAllByLabelText('consents.revoke');
    expect(revokeButtons.length).toBe(1);
  });

  it('delegates consent revocation cleanup to the API service once', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(
      (_title, _message, buttons) => {
        buttons?.find((button) => button.style === 'destructive')?.onPress?.();
      },
    );
    const onConsentRevoked = jest.fn();
    const rendered = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={onConsentRevoked} />,
    );

    const revokeButton = rendered.UNSAFE_getAllByProps({ accessibilityLabel: 'consents.revoke' })[0]!;
    await act(async () => {
      await pressNode(revokeButton);
    });

    await waitFor(() => {
      expect(mockRevokeConsentGrant).toHaveBeenCalledWith('con-1', 'patient-1', session);
    });
    await waitFor(() => {
      expect(onConsentRevoked).toHaveBeenCalledWith('con-1');
    });
    expect(mockRevokeConsentGrant).toHaveBeenCalledTimes(1);
    alertSpy.mockRestore();
  });
});
