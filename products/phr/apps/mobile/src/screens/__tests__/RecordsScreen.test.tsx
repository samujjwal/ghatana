/**
 * Unit tests for RecordsScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import { RecordsScreen } from '../RecordsScreen';
import type { MobileRecord, MobileSession } from '../../types';

const records: MobileRecord[] = [
  { id: 'rec-1', title: 'CBC Lab Panel', summary: 'All values within normal range.', fhirPreview: '{"resourceType":"Observation"}' },
  { id: 'rec-2', title: 'Chest X-Ray', summary: 'No abnormalities detected.', fhirPreview: '{"resourceType":"DiagnosticReport"}' },
];

const session: MobileSession = {
  principalId: 'patient-1',
  tenantId: 'tenant-1',
  role: 'patient',
  name: 'Test Patient',
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
};

describe('RecordsScreen', () => {
  function renderedText(rendered: { toJSON: () => unknown }): string {
    return JSON.stringify(rendered.toJSON());
  }

  it('renders all record titles', () => {
    const rendered = render(<RecordsScreen records={records} />);
    expect(renderedText(rendered)).toContain('CBC Lab Panel');
    expect(renderedText(rendered)).toContain('Chest X-Ray');
  });

  it('renders record summaries', () => {
    const rendered = render(<RecordsScreen records={records} />);
    expect(renderedText(rendered)).toContain('All values within normal range.');
    expect(renderedText(rendered)).toContain('No abnormalities detected.');
  });

  it('renders FHIR preview text', () => {
    const rendered = render(<RecordsScreen records={records} />);
    expect(renderedText(rendered)).toContain('resourceType');
    expect(renderedText(rendered)).toContain('Observation');
  });

  it('renders empty list without crashing', () => {
    const { toJSON } = render(<RecordsScreen records={[]} />);
    expect(toJSON()).toBeTruthy();
  });

  it('renders a single record correctly', () => {
    const rendered = render(<RecordsScreen records={[records[0]!]} />);
    expect(renderedText(rendered)).toContain('CBC Lab Panel');
  });

  it('opens the mobile record detail screen from a record card when a session is present', () => {
    const rendered = render(<RecordsScreen records={records} session={session} />);

    fireEvent.press(rendered.getByLabelText('CBC Lab Panel. Tap to view'));

    expect(renderedText(rendered)).toContain('Record Detail');
    expect(renderedText(rendered)).toContain('rec-1');
    expect(renderedText(rendered)).toContain('FHIR Resource');

    fireEvent.press(rendered.getByLabelText('Back'));
    expect(renderedText(rendered)).toContain('Chest X-Ray');
  });

  it('requires a session before showing record detail PHI from the mobile route surface', () => {
    const rendered = render(<RecordsScreen records={[records[0]!]} />);

    fireEvent.press(rendered.getByLabelText('CBC Lab Panel. Tap to view'));

    expect(renderedText(rendered)).toContain('Valid session required to view this record');
    expect(renderedText(rendered)).not.toContain('CBC Lab Panel');
    expect(renderedText(rendered)).not.toContain('All values within normal range.');
    expect(renderedText(rendered)).not.toContain('Observation');
  });

  it('renders offline last-sync metadata for record lists', () => {
    const rendered = render(
      <RecordsScreen
        records={records}
        offlineCacheStatus={{
          lastSyncAt: Date.parse('2026-05-31T12:00:00.000Z'),
          isOffline: true,
          isStale: false,
        }}
      />,
    );

    expect(renderedText(rendered)).toContain('Last synced:');
    expect(renderedText(rendered)).toContain('Showing encrypted offline data');
  });
});
