/**
 * Unit tests for DashboardScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { render } from '@testing-library/react-native';
import { DashboardScreen } from '../DashboardScreen';
import type { MobileDashboard } from '../../types';

const dashboard: MobileDashboard = {
  patient: { id: 'p1', name: 'Ram Bahadur', age: 35, district: 'Lalitpur', bloodType: 'O+' },
  records: [{ id: 'r1', title: 'CBC', summary: 'Normal', fhirPreview: '{}' }],
  consents: [{ id: 'con-1', grantee: 'Dr. Rai', purpose: 'Lab review', active: true }],
  notifications: [
    { id: 'n1', title: 'New result', detail: 'CBC available' },
    { id: 'n2', title: 'Consent expiring', detail: 'Review soon' },
  ],
};

describe('DashboardScreen', () => {
  function renderedText(rendered: { toJSON: () => unknown }): string {
    return JSON.stringify(rendered.toJSON());
  }

  it('renders patient name', () => {
    const rendered = render(<DashboardScreen dashboard={dashboard} />);
    expect(renderedText(rendered)).toContain('Ram Bahadur');
  });

  it('renders patient district and blood type', () => {
    const rendered = render(<DashboardScreen dashboard={dashboard} />);
    expect(renderedText(rendered)).toContain('Lalitpur');
    expect(renderedText(rendered)).toContain('Blood group');
    expect(renderedText(rendered)).toContain('O+');
  });

  it('shows record count', () => {
    const rendered = render(<DashboardScreen dashboard={dashboard} />);
    expect(renderedText(rendered)).toContain('Records: 1');
  });

  it('shows consent count', () => {
    const rendered = render(<DashboardScreen dashboard={dashboard} />);
    expect(renderedText(rendered)).toContain('Consents: 1');
  });

  it('shows notification count', () => {
    const rendered = render(<DashboardScreen dashboard={dashboard} />);
    expect(renderedText(rendered)).toContain('Alerts: 2');
  });

  it('renders with zero records, consents, and notifications', () => {
    const empty: MobileDashboard = {
      patient: { id: 'p2', name: 'No Data', age: 0, district: 'Unknown', bloodType: 'N/A' },
      records: [],
      consents: [],
      notifications: [],
    };
    const rendered = render(<DashboardScreen dashboard={empty} />);
    expect(renderedText(rendered)).toContain('No Data');
  });
});
