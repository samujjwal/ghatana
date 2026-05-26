/**
 * Unit tests for SettingsScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import { SettingsScreen } from '../SettingsScreen';

describe('SettingsScreen', () => {
  it('renders sync message', () => {
    const { getByText } = render(
      <SettingsScreen
        onSyncOffline={() => {}}
        onLogout={() => {}}
        syncMessage="Synced 5 records"
      />,
    );
    expect(getByText('Synced 5 records')).toBeTruthy();
  });

  it('renders offline cache description', () => {
    const { getByText } = render(
      <SettingsScreen
        onSyncOffline={() => {}}
        onLogout={() => {}}
        syncMessage=""
      />,
    );
    expect(
      getByText(
        'This device keeps a scoped local cache for record access during low-connectivity visits.',
      ),
    ).toBeTruthy();
  });

  it('calls onSyncOffline when refresh button pressed', () => {
    const onSyncOffline = jest.fn();
    const { getByText } = render(
      <SettingsScreen onSyncOffline={onSyncOffline} onLogout={() => {}} syncMessage="" />,
    );
    fireEvent.press(getByText('Refresh offline cache'));
    expect(onSyncOffline).toHaveBeenCalledTimes(1);
  });

  it('calls onLogout when Sign out button pressed', () => {
    const onLogout = jest.fn();
    const { getByAccessibilityLabel } = render(
      <SettingsScreen onSyncOffline={() => {}} onLogout={onLogout} syncMessage="" />,
    );
    fireEvent.press(getByAccessibilityLabel('Sign out'));
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('renders sign out button with accessible label', () => {
    const { getByAccessibilityLabel } = render(
      <SettingsScreen onSyncOffline={() => {}} onLogout={() => {}} syncMessage="" />,
    );
    expect(getByAccessibilityLabel('Sign out')).toBeTruthy();
  });
});
