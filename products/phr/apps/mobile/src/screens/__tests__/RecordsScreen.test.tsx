/**
 * Unit tests for RecordsScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { render } from '@testing-library/react-native';
import { RecordsScreen } from '../RecordsScreen';
import type { MobileRecord } from '../../types';

const records: MobileRecord[] = [
  { id: 'rec-1', title: 'CBC Lab Panel', summary: 'All values within normal range.', fhirPreview: '{"resourceType":"Observation"}' },
  { id: 'rec-2', title: 'Chest X-Ray', summary: 'No abnormalities detected.', fhirPreview: '{"resourceType":"DiagnosticReport"}' },
];

describe('RecordsScreen', () => {
  it('renders all record titles', () => {
    const { getByText } = render(<RecordsScreen records={records} />);
    expect(getByText('CBC Lab Panel')).toBeTruthy();
    expect(getByText('Chest X-Ray')).toBeTruthy();
  });

  it('renders record summaries', () => {
    const { getByText } = render(<RecordsScreen records={records} />);
    expect(getByText('All values within normal range.')).toBeTruthy();
    expect(getByText('No abnormalities detected.')).toBeTruthy();
  });

  it('renders FHIR preview text', () => {
    const { getByText } = render(<RecordsScreen records={records} />);
    expect(getByText('{"resourceType":"Observation"}')).toBeTruthy();
  });

  it('renders empty list without crashing', () => {
    const { toJSON } = render(<RecordsScreen records={[]} />);
    expect(toJSON()).toBeTruthy();
  });

  it('renders a single record correctly', () => {
    const { getByText } = render(<RecordsScreen records={[records[0]!]} />);
    expect(getByText('CBC Lab Panel')).toBeTruthy();
  });
});
