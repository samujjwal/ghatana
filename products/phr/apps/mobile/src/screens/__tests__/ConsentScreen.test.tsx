/**
 * Unit tests for ConsentScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
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

describe('ConsentScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('renders empty state when no consents', () => {
    const { getByText } = render(
      <ConsentScreen consents={[]} session={session} onConsentRevoked={() => {}} />,
    );
    // t('consents.empty') === 'consents.empty'
    expect(getByText('consents.empty')).toBeTruthy();
  });

  it('renders grantee names', () => {
    const { getByText } = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    expect(getByText('Dr. Sharma')).toBeTruthy();
    expect(getByText('Clinic ABCD')).toBeTruthy();
  });

  it('renders consent purpose', () => {
    const { getByText } = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    expect(getByText('Annual review')).toBeTruthy();
  });

  it('shows active badge for active consent', () => {
    const { getAllByText } = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    // t('consents.active') === 'consents.active'
    expect(getAllByText('consents.active').length).toBeGreaterThan(0);
  });

  it('shows revoke button only for active consents', () => {
    const { getAllByAccessibilityLabel } = render(
      <ConsentScreen consents={consents} session={session} onConsentRevoked={() => {}} />,
    );
    // Only con-1 is active and should have revoke button
    // t('consents.revoke') === 'consents.revoke'
    const revokeButtons = getAllByAccessibilityLabel('consents.revoke');
    expect(revokeButtons.length).toBe(1);
  });
});
