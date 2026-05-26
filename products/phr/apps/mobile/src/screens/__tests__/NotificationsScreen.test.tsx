/**
 * Unit tests for NotificationsScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import { NotificationsScreen } from '../NotificationsScreen';
import type { MobileNotificationItem } from '../../types';

const notifications: MobileNotificationItem[] = [
  { id: 'n1', title: 'CBC result available', detail: 'Your latest lab result is ready.' },
  { id: 'n2', title: 'Consent expiring', detail: 'Consent for Dr. Rai expires in 3 days.' },
];

describe('NotificationsScreen', () => {
  it('renders notification titles', () => {
    const { getByText } = render(
      <NotificationsScreen notifications={notifications} onEnablePush={() => {}} />,
    );
    expect(getByText('CBC result available')).toBeTruthy();
    expect(getByText('Consent expiring')).toBeTruthy();
  });

  it('renders notification detail text', () => {
    const { getByText } = render(
      <NotificationsScreen notifications={notifications} onEnablePush={() => {}} />,
    );
    expect(getByText('Your latest lab result is ready.')).toBeTruthy();
  });

  it('calls onEnablePush when enable button is pressed', () => {
    const onEnablePush = jest.fn();
    const { getByText } = render(
      <NotificationsScreen notifications={notifications} onEnablePush={onEnablePush} />,
    );
    fireEvent.press(getByText('Enable push notifications'));
    expect(onEnablePush).toHaveBeenCalledTimes(1);
  });

  it('renders empty list without crashing', () => {
    const { toJSON } = render(
      <NotificationsScreen notifications={[]} onEnablePush={() => {}} />,
    );
    expect(toJSON()).toBeTruthy();
  });

  it('renders enable push notifications button', () => {
    const { getByText } = render(
      <NotificationsScreen notifications={[]} onEnablePush={() => {}} />,
    );
    expect(getByText('Enable push notifications')).toBeTruthy();
  });
});
