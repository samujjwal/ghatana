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
});
