/**
 * Unit tests for ConsentScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { render } from '@testing-library/react-native';
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

function renderedText(rendered: { toJSON: () => unknown }): string {
  return JSON.stringify(rendered.toJSON());
}

describe('ConsentScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
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
});
