import React from 'react';
import { describe, expect, it } from 'vitest';

import { getNavigationSectionsForShellRole } from '../../layouts/DefaultLayout';

describe('DefaultLayout navigation progressive disclosure', () => {
  it('keeps operator and admin routes out of the primary shell', () => {
    const sections = getNavigationSectionsForShellRole('primary-user');
    const labels = sections.flatMap((section) => section.items.map((item) => item.label));

    expect(labels).toEqual(['Home', 'Data', 'Pipelines', 'Query']);
  });

  it('reveals operator routes without exposing admin-only settings', () => {
    const sections = getNavigationSectionsForShellRole('operator');
    const labels = sections.flatMap((section) => section.items.map((item) => item.label));

    expect(labels).toContain('Insights');
    expect(labels).toContain('Trust');
    expect(labels).toContain('Events');
    expect(labels).not.toContain('Settings');
  });

  it('reveals the full shell for admins', () => {
    const sections = getNavigationSectionsForShellRole('admin');
    const labels = sections.flatMap((section) => section.items.map((item) => item.label));

    expect(labels).toContain('Insights');
    expect(labels).toContain('Trust');
    expect(labels).toContain('Events');
    expect(labels).toContain('Alerts');
    expect(labels).not.toContain('Settings');
  });
});