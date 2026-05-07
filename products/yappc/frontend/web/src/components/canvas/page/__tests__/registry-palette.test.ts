import { describe, expect, it } from 'vitest';
import type { BuilderPaletteEntry } from '@ghatana/ds-registry';

import {
  getBuilderPaletteCategories,
  getFilteredBuilderPalette,
} from '../registry';

const entries: readonly BuilderPaletteEntry[] = [
  {
    id: 'button',
    name: 'Button',
    displayName: 'Button',
    tooltip: 'Trigger an action',
    group: 'Inputs',
    subGroup: 'Actions',
    rank: 2,
    icon: undefined,
    defaultProps: {},
    featured: false,
    searchKeywords: ['cta', 'submit'],
    version: '1.0.0',
    status: 'stable',
  },
  {
    id: 'text-field',
    name: 'TextField',
    displayName: 'Text field',
    tooltip: 'Capture customer email',
    group: 'Inputs',
    subGroup: 'Text',
    rank: 1,
    icon: undefined,
    defaultProps: {},
    featured: true,
    searchKeywords: ['form', 'email'],
    version: '1.0.0',
    status: 'stable',
  },
  {
    id: 'card',
    name: 'Card',
    displayName: 'Card',
    tooltip: 'Group related content',
    group: 'Layout',
    subGroup: undefined,
    rank: 1,
    icon: undefined,
    defaultProps: {},
    featured: false,
    searchKeywords: ['container'],
    version: '1.0.0',
    status: 'stable',
  },
];

describe('builder registry palette filtering', () => {
  it('returns sorted palette categories', () => {
    expect(getBuilderPaletteCategories(entries)).toEqual(['Inputs', 'Layout']);
  });

  it('searches names, tooltips, groups, subgroups, and keywords', () => {
    expect(getFilteredBuilderPalette({ query: 'email' }, entries).map((entry) => entry.name)).toEqual([
      'TextField',
    ]);
    expect(getFilteredBuilderPalette({ query: 'container' }, entries).map((entry) => entry.name)).toEqual([
      'Card',
    ]);
  });

  it('filters by category and keeps recommended components first', () => {
    expect(getFilteredBuilderPalette({ category: 'Inputs' }, entries).map((entry) => entry.name)).toEqual([
      'TextField',
      'Button',
    ]);
  });

  it('supports phase and context filtering for non-design palettes', () => {
    expect(getFilteredBuilderPalette({ phaseMode: 'validate' }, entries)).toEqual([]);
    expect(
      getFilteredBuilderPalette(
        { phaseMode: 'validate', includeReadOnlyPhaseComponents: true, contextTags: ['form'] },
        entries
      ).map((entry) => entry.name)
    ).toEqual(['TextField']);
  });
});
