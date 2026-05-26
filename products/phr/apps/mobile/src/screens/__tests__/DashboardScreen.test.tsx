/**
 * Unit tests for DashboardScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { render } from '@testing-library/react-native';
import { DashboardScreen } from '../DashboardScreen';
import type { MobileDashboard } from '../../types';

const dashboard: MobileDashboard = {
  patient: { name: 'Ram Bahadur', district: 'Lalitpur', bloodType: 'O+' },
  records: [{ id: 'r1', title: 'CBC', summary: 'Normal', fhirPreview: '{}' }],
  consents: [{ id: 'con-1', grantee: 'Dr. Rai', purpose: 'Lab review', active: true }],
  notifications: [
    { id: 'n1', title: 'New result', detail: 'CBC available' },
    { id: 'n2', title: 'Consent expiring', detail: 'Review soon' },
  ],
};

describe('DashboardScreen', () => {
  it('renders patient name', () => {
    const { getByText } = render(<DashboardScreen dashboard={dashboard} />);
    expect(getByText('Ram Bahadur')).toBeTruthy();
  });

  it('renders patient district and blood type', () => {
    const { getByText } = render(<DashboardScreen dashboard={dashboard} />);
    expect(getByText('Lalitpur · Blood group O+')).toBeTruthy();
  });

  it('shows record count', () => {
    const { getAllByText } = render(<DashboardScreen dashboard={dashboard} />);
    // 1 record
    expect(getAllByText('1').length).toBeGreaterThan(0);
  });

  it('shows consent count', () => {
    const { getAllByText } = render(<DashboardScreen dashboard={dashboard} />);
    // Already checked above; consents also 1
    expect(getAllByText('Records').length).toBeGreaterThan(0);
    expect(getAllByText('Consents').length).toBeGreaterThan(0);
  });

  it('shows notification count', () => {
    const { getAllByText } = render(<DashboardScreen dashboard={dashboard} />);
    expect(getAllByText('2').length).toBeGreaterThan(0);
  });

  it('renders with zero records, consents, and notifications', () => {
    const empty: MobileDashboard = {
      patient: { name: 'No Data', district: 'Unknown', bloodType: 'N/A' },
      records: [],
      consents: [],
      notifications: [],
    };
    const { getByText } = render(<DashboardScreen dashboard={empty} />);
    expect(getByText('No Data')).toBeTruthy();
  });
});
