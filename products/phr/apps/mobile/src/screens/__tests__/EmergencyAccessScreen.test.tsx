/**
 * Unit tests for EmergencyAccessScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { act, render, waitFor } from '@testing-library/react-native';
import { EmergencyAccessScreen } from '../EmergencyAccessScreen';
import type { MobileSession } from '../../types';

jest.mock('../../i18n/phrMobileI18n', () => ({
  t: (key: string) => key,
}));

jest.mock('../../services/phrMobileApi', () => ({
  requestMobileEmergencyAccess: jest.fn(),
}));

import { requestMobileEmergencyAccess } from '../../services/phrMobileApi';

const session: MobileSession = {
  principalId: 'patient-1',
  tenantId: 'tenant-np',
  role: 'patient',
  name: 'Ram Bahadur',
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
};

const mockRequestMobileEmergencyAccess = requestMobileEmergencyAccess as jest.MockedFunction<typeof requestMobileEmergencyAccess>;

Object.defineProperty(globalThis, 'crypto', {
  value: {
    randomUUID: jest.fn(() => 'correlation-1'),
  },
  configurable: true,
});

function renderedText(rendered: { toJSON: () => unknown }): string {
  return JSON.stringify(rendered.toJSON());
}

async function pressNode(node: { props: Record<string, unknown> }): Promise<void> {
  const onPress = node.props.onPress ?? node.props.onClick;
  if (typeof onPress !== 'function') {
    throw new Error('Expected rendered node to expose a press handler.');
  }
  const result: unknown = onPress();
  if (result && typeof result === 'object' && 'then' in result) {
    await result;
  }
}

function changeTextNode(node: { props: Record<string, unknown> }, value: string): void {
  const onChangeText = node.props.onChangeText;
  if (typeof onChangeText !== 'function') {
    throw new Error('Expected rendered input to expose an onChangeText handler.');
  }
  onChangeText(value);
}

describe('EmergencyAccessScreen', () => {
  beforeEach(() => {
    mockRequestMobileEmergencyAccess.mockReset();
    mockRequestMobileEmergencyAccess.mockResolvedValue({
      patientName: 'Ram Bahadur',
      bloodType: 'O+',
      allergies: [],
      medications: [],
      emergencyContact: 'Family',
    });
  });

  it('renders locked state initially', () => {
    const rendered = render(
      <EmergencyAccessScreen onAuthenticate={jest.fn()} session={session} />,
    );
    expect(renderedText(rendered)).toContain('emergency.title');
  });

  it('renders reason input and request button', () => {
    const { getByLabelText } = render(
      <EmergencyAccessScreen onAuthenticate={jest.fn()} session={session} />,
    );
    expect(getByLabelText('emergency.patientIdLabel')).toBeTruthy();
    // accessibilityLabel = t('emergency.reasonLabel')
    expect(getByLabelText('emergency.reasonLabel')).toBeTruthy();
    // request button accessibilityLabel = t('emergency.requestButton')
    expect(getByLabelText('emergency.requestButton')).toBeTruthy();
  });

  it('shows verifying state while authenticating', async () => {
    let resolveAuth!: (value: boolean) => void;
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockReturnValue(
      new Promise<boolean>((resolve) => { resolveAuth = resolve; }),
    );
    const rendered = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} session={session} />,
    );

    act(() => changeTextNode(rendered.getByLabelText('emergency.patientIdLabel'), 'patient-1'));
    act(() => changeTextNode(rendered.getByLabelText('emergency.reasonLabel'), 'Patient collapsed'));
    await waitFor(() => expect(renderedText(rendered)).toContain('Patient collapsed'));
    await act(async () => {
      await pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'emergency.requestButton' }));
    });

    await waitFor(() => {
      expect(renderedText(rendered)).toContain('emergency.requesting');
    });

    act(() => { resolveAuth(true); });
  });

  it('shows authorized state after successful authentication', async () => {
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockResolvedValue(true);
    const rendered = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} session={session} />,
    );

    act(() => changeTextNode(rendered.getByLabelText('emergency.patientIdLabel'), 'patient-1'));
    act(() => changeTextNode(rendered.getByLabelText('emergency.reasonLabel'), 'Cardiac emergency'));
    await waitFor(() => expect(renderedText(rendered)).toContain('Cardiac emergency'));
    await act(async () => {
      await pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'emergency.requestButton' }));
    });

    await waitFor(() => {
      expect(renderedText(rendered)).toContain('emergency.authorized');
    });
    expect(mockRequestMobileEmergencyAccess).toHaveBeenCalledWith(
      'patient-1',
      'Cardiac emergency',
      session,
    );
  });

  it('shows denied state after failed authentication', async () => {
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockResolvedValue(false);
    const rendered = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} session={session} />,
    );

    act(() => changeTextNode(rendered.getByLabelText('emergency.patientIdLabel'), 'patient-1'));
    act(() => changeTextNode(rendered.getByLabelText('emergency.reasonLabel'), 'Emergency reason'));
    await waitFor(() => expect(renderedText(rendered)).toContain('Emergency reason'));
    await act(async () => {
      await pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'emergency.requestButton' }));
    });

    await waitFor(() => {
      expect(renderedText(rendered)).toContain('emergency.denied');
    });
  });

  it('retry button in denied state resets to locked', async () => {
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockResolvedValue(false);
    const rendered = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} session={session} />,
    );

    act(() => changeTextNode(rendered.getByLabelText('emergency.patientIdLabel'), 'patient-1'));
    act(() => changeTextNode(rendered.getByLabelText('emergency.reasonLabel'), 'Access needed'));
    await waitFor(() => expect(renderedText(rendered)).toContain('Access needed'));
    await act(async () => {
      await pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'emergency.requestButton' }));
    });

    await waitFor(() => expect(renderedText(rendered)).toContain('emergency.denied'));

    await act(async () => {
      await pressNode(rendered.UNSAFE_getByProps({ accessibilityLabel: 'common.retry' }));
    });
    await waitFor(() => expect(renderedText(rendered)).toContain('emergency.title'));
  });
});
