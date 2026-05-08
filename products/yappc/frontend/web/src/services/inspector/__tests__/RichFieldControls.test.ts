import { describe, expect, it } from 'vitest';
import { createNumberField, createTextField, getFieldControlForProp } from '../RichFieldControls';

describe('RichFieldControls', () => {
  it('creates typed fallback field controls when callers omit a name', () => {
    expect(createTextField()).toMatchObject({
      name: '',
      type: 'text',
    });
  });

  it('preserves caller-provided field metadata', () => {
    expect(createNumberField({ name: 'priority', step: 5, min: 0 })).toMatchObject({
      name: 'priority',
      type: 'number',
      step: 5,
      min: 0,
    });
  });

  it('maps enum prop metadata to an enum field with stable labels', () => {
    const field = getFieldControlForProp({
      name: 'variant',
      type: 'string',
      enumValues: ['primary', 'secondary'],
    });

    expect(field).toMatchObject({
      name: 'variant',
      label: 'Variant',
      type: 'enum',
      options: [
        { value: 'primary', label: 'primary' },
        { value: 'secondary', label: 'secondary' },
      ],
    });
  });
});
