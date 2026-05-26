/**
 * Unit tests for EmergencyAccessScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { EmergencyAccessScreen } from '../EmergencyAccessScreen';

jest.mock('../../i18n/phrMobileI18n', () => ({
  t: (key: string) => key,
}));

describe('EmergencyAccessScreen', () => {
  it('renders locked state initially', () => {
    const { getByText } = render(
      <EmergencyAccessScreen onAuthenticate={jest.fn()} />,
    );
    // t('emergency.title') === 'emergency.title'
    expect(getByText('emergency.title')).toBeTruthy();
  });

  it('renders reason input and request button', () => {
    const { getByLabelText, getByAccessibilityLabel } = render(
      <EmergencyAccessScreen onAuthenticate={jest.fn()} />,
    );
    // accessibilityLabel = t('emergency.reasonLabel')
    expect(getByLabelText('emergency.reasonLabel')).toBeTruthy();
    // request button accessibilityLabel = t('emergency.requestButton')
    expect(getByAccessibilityLabel('emergency.requestButton')).toBeTruthy();
  });

  it('shows verifying state while authenticating', async () => {
    let resolveAuth!: (value: boolean) => void;
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockReturnValue(
      new Promise<boolean>((resolve) => { resolveAuth = resolve; }),
    );
    const { getByLabelText, getByText } = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} />,
    );

    // Enter a reason to enable the button
    fireEvent.changeText(getByLabelText('emergency.reasonLabel'), 'Patient collapsed');
    fireEvent.press(getByText('emergency.requestButton'));

    await waitFor(() => {
      expect(getByText('emergency.requesting')).toBeTruthy();
    });

    act(() => { resolveAuth(true); });
  });

  it('shows authorized state after successful authentication', async () => {
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockResolvedValue(true);
    const { getByLabelText, getByText } = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} />,
    );

    fireEvent.changeText(getByLabelText('emergency.reasonLabel'), 'Cardiac emergency');
    fireEvent.press(getByText('emergency.requestButton'));

    await waitFor(() => {
      expect(getByText('emergency.authorized')).toBeTruthy();
    });
  });

  it('shows denied state after failed authentication', async () => {
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockResolvedValue(false);
    const { getByLabelText, getByText } = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} />,
    );

    fireEvent.changeText(getByLabelText('emergency.reasonLabel'), 'Emergency reason');
    fireEvent.press(getByText('emergency.requestButton'));

    await waitFor(() => {
      expect(getByText('emergency.denied')).toBeTruthy();
    });
  });

  it('retry button in denied state resets to locked', async () => {
    const onAuthenticate = jest.fn<Promise<boolean>, []>().mockResolvedValue(false);
    const { getByLabelText, getByText } = render(
      <EmergencyAccessScreen onAuthenticate={onAuthenticate} />,
    );

    fireEvent.changeText(getByLabelText('emergency.reasonLabel'), 'Access needed');
    fireEvent.press(getByText('emergency.requestButton'));

    await waitFor(() => expect(getByText('emergency.denied')).toBeTruthy());

    // t('common.retry')
    fireEvent.press(getByText('common.retry'));
    await waitFor(() => expect(getByText('emergency.title')).toBeTruthy());
  });
});
