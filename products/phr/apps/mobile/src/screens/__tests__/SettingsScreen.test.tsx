/**
 * Unit tests for SettingsScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { Alert } from 'react-native';
import { render, waitFor } from '@testing-library/react-native';
import { SettingsScreen } from '../SettingsScreen';
import { clearDashboardOffline, loadDashboardOffline, saveDashboardOffline } from '../../services/offlineStore';
import { phiClearAll, phiGet, phiSet, resetPhiStorageAdapter, setPhiStorageAdapter } from '../../services/phiEncryptedStorage';
import type { MobileSession } from '../../types';

const session: MobileSession = {
  principalId: 'patient-1',
  tenantId: 'tenant-np',
  role: 'patient',
  name: 'Ram Bahadur',
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
};

interface TestNodeWithProps {
  props: Record<string, unknown>;
}

function pressNode(node: TestNodeWithProps): void {
  const onPress = node.props.onPress;
  if (typeof onPress !== 'function') {
    throw new Error('Expected rendered node to expose an onPress handler.');
  }
  onPress();
}

describe('SettingsScreen', () => {
  const phiItems = new Map<string, string>();

  beforeEach(async () => {
    phiItems.clear();
    setPhiStorageAdapter({
      async setItem(key: string, value: string): Promise<void> {
        phiItems.set(key, value);
      },
      async getItem(key: string): Promise<string | null> {
        return phiItems.get(key) ?? null;
      },
      async removeItem(key: string): Promise<void> {
        phiItems.delete(key);
      },
      async clearAllPhi(): Promise<void> {
        phiItems.clear();
      },
    });
    await Promise.all([phiClearAll(), clearDashboardOffline()]);
    jest.restoreAllMocks();
  });

  afterEach(() => {
    resetPhiStorageAdapter();
  });

  it('renders sync message', () => {
    const { toJSON } = render(
      <SettingsScreen
        onSyncOffline={() => {}}
        onLogout={() => {}}
        syncMessage="Synced 5 records"
        session={session}
      />,
    );
    expect(JSON.stringify(toJSON())).toContain('Synced 5 records');
  });

  it('renders offline cache description', () => {
    const { toJSON } = render(
      <SettingsScreen
        onSyncOffline={() => {}}
        onLogout={() => {}}
        syncMessage=""
        session={session}
      />,
    );
    expect(JSON.stringify(toJSON())).toContain(
      'This device keeps a scoped local cache for record access during low-connectivity visits.',
    );
  });

  it('renders encrypted storage and biometric policy status', async () => {
    const { toJSON } = render(
      <SettingsScreen
        onSyncOffline={() => {}}
        onLogout={() => {}}
        syncMessage=""
        session={session}
      />,
    );
    await waitFor(() => {
      const serialized = JSON.stringify(toJSON());
      expect(serialized).toContain('AES-GCM encrypted storage enabled');
      expect(serialized).toContain('Biometric policy');
    });
  });

  it('calls onSyncOffline when refresh button pressed', () => {
    const onSyncOffline = jest.fn();
    const { UNSAFE_getByProps } = render(
      <SettingsScreen onSyncOffline={onSyncOffline} onLogout={() => {}} syncMessage="" session={session} />,
    );
    pressNode(UNSAFE_getByProps({ accessibilityLabel: 'Refresh offline cache' }));
    expect(onSyncOffline).toHaveBeenCalledTimes(1);
  });

  it('clears encrypted offline cache when clear cache is pressed', async () => {
    await Promise.all([
      phiSet('dashboard:patient-1', JSON.stringify({ patientId: 'patient-1', restricted: 'diagnosis' })),
      saveDashboardOffline(
        {
          patient: {
            id: 'patient-1',
            name: 'Ram Bahadur',
            age: 44,
            bloodType: 'O+',
            district: 'Kathmandu',
          },
          records: [],
          consents: [],
          notifications: [],
        },
        undefined,
        session,
      ),
    ]);

    const { UNSAFE_getByProps, toJSON } = render(
      <SettingsScreen onSyncOffline={() => {}} onLogout={() => {}} syncMessage="" session={session} />,
    );
    pressNode(UNSAFE_getByProps({ accessibilityLabel: 'Clear offline cache' }));

    await waitFor(() => expect(JSON.stringify(toJSON())).toContain('Offline health cache cleared.'));
    await expect(phiGet('dashboard:patient-1')).resolves.toBeNull();
    await expect(loadDashboardOffline(session)).resolves.toBeNull();
  });

  it('clears PHI caches and calls onLogout when Sign Out is confirmed', async () => {
    const onLogout = jest.fn();
    await Promise.all([
      phiSet('dashboard:patient-1', JSON.stringify({ patientId: 'patient-1', restricted: 'diagnosis' })),
      saveDashboardOffline(
        {
          patient: {
            id: 'patient-1',
            name: 'Ram Bahadur',
            age: 44,
            bloodType: 'O+',
            district: 'Kathmandu',
          },
          records: [],
          consents: [],
          notifications: [],
        },
        undefined,
        session,
      ),
    ]);
    jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => {
      buttons?.find((button) => button.style === 'destructive')?.onPress?.();
    });

    const { UNSAFE_getByProps } = render(
      <SettingsScreen onSyncOffline={() => {}} onLogout={onLogout} syncMessage="" session={session} />,
    );
    pressNode(UNSAFE_getByProps({ accessibilityLabel: 'Sign Out' }));

    await waitFor(() => expect(onLogout).toHaveBeenCalledTimes(1));
    await expect(phiGet('dashboard:patient-1')).resolves.toBeNull();
    await expect(loadDashboardOffline(session)).resolves.toBeNull();
  });

  it('renders sign out button with accessible label', () => {
    const { UNSAFE_getByProps } = render(
      <SettingsScreen onSyncOffline={() => {}} onLogout={() => {}} syncMessage="" session={session} />,
    );
    expect(UNSAFE_getByProps({ accessibilityLabel: 'Sign Out' })).toBeTruthy();
  });
});
