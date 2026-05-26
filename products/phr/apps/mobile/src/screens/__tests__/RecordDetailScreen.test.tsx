/**
 * Unit tests for RecordDetailScreen.
 * Exercises the real production component — no object-literal assertions.
 */
import React from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import { RecordDetailScreen } from '../RecordDetailScreen';
import type { MobileRecord } from '../../types';

const record: MobileRecord = {
  id: 'rec-42',
  title: 'Hemoglobin A1c',
  summary: 'HbA1c: 6.1% — within target range.',
  fhirPreview: '{"resourceType":"Observation","status":"final"}',
};

describe('RecordDetailScreen', () => {
  it('renders record title (type field)', () => {
    const { getByText } = render(<RecordDetailScreen record={record} onBack={() => {}} />);
    expect(getByText('Hemoglobin A1c')).toBeTruthy();
  });

  it('renders record summary', () => {
    const { getByText } = render(<RecordDetailScreen record={record} onBack={() => {}} />);
    expect(getByText('HbA1c: 6.1% — within target range.')).toBeTruthy();
  });

  it('renders FHIR preview code', () => {
    const { getByText } = render(<RecordDetailScreen record={record} onBack={() => {}} />);
    expect(getByText('{"resourceType":"Observation","status":"final"}')).toBeTruthy();
  });

  it('renders the record ID', () => {
    const { getByText } = render(<RecordDetailScreen record={record} onBack={() => {}} />);
    expect(getByText('rec-42')).toBeTruthy();
  });

  it('calls onBack when back button is pressed', () => {
    const onBack = jest.fn();
    const { getByLabelText } = render(<RecordDetailScreen record={record} onBack={onBack} />);
    fireEvent.press(getByLabelText('Back'));
    expect(onBack).toHaveBeenCalledTimes(1);
  });
});
